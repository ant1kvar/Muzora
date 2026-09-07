package com.muzora.player

import com.muzora.data.api.SubsonicClient
import com.muzora.data.local.cache.CacheManager
import com.muzora.data.local.db.BlacklistDao
import com.muzora.data.local.db.BlacklistEntity
import com.muzora.data.local.prefs.PreferencesRepository
import com.muzora.data.repo.StarRepository
import com.muzora.domain.model.CacheStatus
import com.muzora.domain.model.PlaybackMode
import com.muzora.domain.model.QueueItem
import com.muzora.domain.model.RadioStation
import com.muzora.domain.model.RepeatMode
import com.muzora.domain.model.Song
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.ArrayDeque

data class PlayerUiState(
    val current: Song? = null,
    val radio: RadioStation? = null,
    val queue: List<QueueItem> = emptyList(),
    val index: Int = 0,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val mode: PlaybackMode = PlaybackMode.LibraryRandom,
    val isOffline: Boolean = false,
    val errorMessage: String? = null,
    val likedIds: Set<String> = emptySet(),
)

class QueueManager(
    private val client: SubsonicClient,
    private val blacklistDao: BlacklistDao,
    private val cacheManager: CacheManager,
    private val prefs: PreferencesRepository,
    private val scope: CoroutineScope,
    private val starRepository: StarRepository,
) {
    private val mutex = Mutex()
    private val playedIds = linkedSetOf<String>()
    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var blacklist: Set<String> = emptySet()
    private var starred: List<Song> = emptyList()
    /** Set when we drop to cache/offline in LibraryRandom; cleared after a successful server handoff. */
    @Volatile
    private var pendingServerHandoff: Boolean = false

    init {
        scope.launch {
            blacklistDao.observeIds().collect { ids ->
                blacklist = ids.toSet()
            }
        }
        scope.launch {
            cacheManager.statuses.collect { statuses ->
                _state.update { current ->
                    current.copy(
                        queue = current.queue.map { item ->
                            item.copy(cacheStatus = statuses[item.song.id] ?: item.cacheStatus)
                        }
                    )
                }
            }
        }
        scope.launch {
            starRepository.observeLocalStars().collect { local ->
                starred = local
                _state.update { it.copy(likedIds = local.map { s -> s.id }.toSet()) }
            }
        }
    }

    fun updatePlaybackProgress(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        _state.update {
            it.copy(positionMs = positionMs, durationMs = durationMs, isPlaying = isPlaying)
        }
    }

    fun setOffline(offline: Boolean) {
        if (offline &&
            (_state.value.mode == PlaybackMode.LibraryRandom ||
                _state.value.mode == PlaybackMode.Favorites)
        ) {
            pendingServerHandoff = true
        }
        _state.update { it.copy(isOffline = offline) }
    }

    fun needsServerHandoff(): Boolean = pendingServerHandoff

    fun setError(message: String?) {
        _state.update { it.copy(errorMessage = message) }
    }

    /** Stop all playback state on logout / session clear. */
    fun clearPlayback() {
        playedIds.clear()
        pendingServerHandoff = false
        _state.update {
            PlayerUiState(
                isOffline = it.isOffline,
                likedIds = emptySet(),
            )
        }
    }

    suspend fun loadLocalStars() {
        starred = starRepository.getLocalStars()
        _state.update { it.copy(likedIds = starred.map { s -> s.id }.toSet()) }
    }

    suspend fun refreshStarred() {
        if (!client.isConfigured) {
            loadLocalStars()
            return
        }
        runCatching {
            val remote = client.getStarred()
            starRepository.replaceFromServer(remote)
            starred = starRepository.getLocalStars()
            _state.update { it.copy(likedIds = starred.map { s -> s.id }.toSet()) }
        }.onFailure {
            loadLocalStars()
        }
    }

    /** Flush pending likes and pull server favorites. Call when network returns. */
    suspend fun syncStars() {
        starRepository.syncPending()
        refreshStarred()
    }

    suspend fun startShuffle() {
        // Repeat-One would make "new random" sound like the track is stuck looping.
        val repeat = _state.value.repeatMode.let { if (it == RepeatMode.One) RepeatMode.Off else it }
        _state.update {
            it.copy(
                mode = PlaybackMode.LibraryRandom,
                radio = null,
                errorMessage = null,
                repeatMode = repeat,
            )
        }
        playedIds.clear()
        val songs = loadRandomBatch()
        mutex.withLock {
            if (songs.isEmpty()) {
                _state.update { it.copy(current = null, queue = emptyList(), errorMessage = "empty") }
                return
            }
            setQueue(songs, 0)
        }
    }

    /**
     * Good network returned: fetch a fresh server random queue, prefetch it, and swap in
     * after the currently playing (likely offline/cache) track finishes.
     *
     * Also covers Favorites started while offline — otherwise the queue would keep
     * looping only liked tracks after the server comes back.
     */
    suspend fun onServerBecameReachable() {
        syncStars()
        if (prefs.getSettings().playCacheOnly || !client.isConfigured) {
            pendingServerHandoff = false
            return
        }
        _state.update { it.copy(isOffline = false) }
        val snapshot = _state.value
        val handoffFromFavorites = snapshot.mode == PlaybackMode.Favorites
        if (snapshot.mode != PlaybackMode.LibraryRandom && !handoffFromFavorites) {
            pendingServerHandoff = false
            mutex.withLock { prefetchAround(_state.value.index) }
            return
        }
        val serverSongs = loadRandomFromServer()
        mutex.withLock {
            val mode = _state.value.mode
            if (mode != PlaybackMode.LibraryRandom && mode != PlaybackMode.Favorites) {
                pendingServerHandoff = false
                return
            }
            if (serverSongs.isEmpty()) {
                Log.i(TAG, "handoff: server random empty, keep cache queue (pending stays)")
                prefetchAround(_state.value.index)
                return
            }
            val current = _state.value.current
            if (current == null) {
                setQueue(serverSongs, 0)
                _state.update { it.copy(mode = PlaybackMode.LibraryRandom) }
                pendingServerHandoff = false
                Log.i(TAG, "handoff: started server queue size=${serverSongs.size}")
                return
            }
            val upcoming = serverSongs.filterNot { it.id == current.id }
            val items = (listOf(current) + upcoming).map { song ->
                QueueItem(
                    song = song,
                    cacheStatus = if (cacheManager.hasComplete(song.id)) {
                        CacheStatus.Cached
                    } else {
                        CacheStatus.NotCached
                    },
                )
            }
            playedIds.clear()
            playedIds.add(current.id)
            _state.update {
                it.copy(
                    mode = PlaybackMode.LibraryRandom,
                    queue = items,
                    index = 0,
                    current = current,
                    isOffline = false,
                    errorMessage = null,
                )
            }
            pendingServerHandoff = false
            prefetchAround(0)
            Log.i(
                TAG,
                "handoff: keep '${current.title}', queued ${upcoming.size} server tracks" +
                    if (handoffFromFavorites) " (from Favorites)" else "",
            )
        }
    }

    suspend fun startFavorites(startId: String? = null) = mutex.withLock {
        // Local list only — network refresh on every tap rewrote Room and flashed covers.
        loadLocalStars()
        val songs = loadFavoritesSongs()
        if (songs.isEmpty()) {
            _state.update {
                it.copy(
                    mode = PlaybackMode.Favorites,
                    current = null,
                    queue = emptyList(),
                    radio = null,
                    errorMessage = "empty_favorites",
                )
            }
            return
        }
        val index = startId?.let { id -> songs.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        _state.update { it.copy(mode = PlaybackMode.Favorites, radio = null) }
        setQueue(songs, index)
    }

    suspend fun playSongThenRandom(song: Song) = mutex.withLock {
        _state.update { it.copy(mode = PlaybackMode.LibraryRandom, radio = null) }
        val rest = loadRandomBatch().filterNot { it.id == song.id }
        setQueue(listOf(song) + rest, 0)
    }

    suspend fun playRadio(station: RadioStation) = mutex.withLock {
        _state.update {
            it.copy(
                mode = PlaybackMode.Radio,
                radio = station,
                current = Song(
                    id = "radio:${station.id}",
                    title = station.name,
                    artist = "Radio",
                    album = station.name,
                ),
                queue = emptyList(),
                index = 0,
                errorMessage = null,
            )
        }
    }

    /**
     * ICY / stream metadata (StreamTitle). Common form: "Artist - Title".
     * Updates [PlayerUiState.current] so Now Playing + radio list stay in sync.
     */
    fun updateRadioStreamInfo(streamTitle: String?, streamArtist: String? = null) {
        val state = _state.value
        if (state.mode != PlaybackMode.Radio) return
        val station = state.radio ?: return
        val rawTitle = streamTitle?.trim().orEmpty()
        val rawArtist = streamArtist?.trim().orEmpty()
        if (rawTitle.isEmpty() && rawArtist.isEmpty()) return

        val (artist, title) = parseIcyTitleArtist(rawTitle, rawArtist)
        if (title.isNullOrBlank()) return
        // Ignore the initial MediaItem branding bump (station name / "Radio").
        if (title.equals(station.name, ignoreCase = true) &&
            (artist.isNullOrBlank() || artist.equals("Radio", ignoreCase = true))
        ) {
            return
        }

        _state.update {
            it.copy(
                current = it.current?.copy(
                    title = title,
                    artist = artist?.takeIf { a -> a.isNotBlank() } ?: station.name,
                    album = station.name,
                ),
            )
        }
    }

    suspend fun jumpTo(index: Int) = mutex.withLock {
        val queue = _state.value.queue
        if (index !in queue.indices) return
        val song = queue[index].song
        playedIds.add(song.id)
        _state.update { it.copy(index = index, current = song) }
        prefetchAround(index)
    }

    fun cycleRepeat(): RepeatMode {
        val next = _state.value.repeatMode.next()
        _state.update { it.copy(repeatMode = next) }
        return next
    }

    suspend fun skipNext(): Song? {
        val state = _state.value
        if (state.mode == PlaybackMode.Radio) return state.current
        return advanceToPlayable()
    }

    suspend fun onTrackEnded(): Song? {
        val state = _state.value
        if (state.mode == PlaybackMode.Radio) return null
        val current = state.current ?: return null
        if (state.repeatMode == RepeatMode.One) return current
        return advanceToPlayable()
    }

    /** Index of the next playable song; when offline only cached songs count (others are skipped). */
    private suspend fun nextPlayableIndex(state: PlayerUiState, from: Int): Int? {
        val offline = state.isOffline || prefs.getSettings().playCacheOnly || !client.isConfigured
        for (i in from until state.queue.size) {
            if (!offline || cacheManager.hasComplete(state.queue[i].song.id)) return i
        }
        return null
    }

    /** Advance to the next playable song; refill from cache/library when the queue is exhausted. */
    private suspend fun advanceToPlayable(): Song? {
        val endedId: String?
        mutex.withLock {
            val state = _state.value
            if (state.mode == PlaybackMode.Radio) return null
            endedId = state.current?.id
            val nextIndex = nextPlayableIndex(state, state.index + 1)
            if (nextIndex != null) {
                val song = state.queue[nextIndex].song
                playedIds.add(song.id)
                _state.update { it.copy(index = nextIndex, current = song) }
                prefetchAround(nextIndex)
                Log.i(TAG, "advance: queue[${nextIndex}] '${song.title}' (ended=$endedId)")
                return song
            }
        }

        val mode = _state.value.mode
        if (mode == PlaybackMode.LibraryRandom) {
            // Network I/O outside the mutex so handoff/recovery can proceed.
            val more = if (pendingServerHandoff && client.isConfigured &&
                !prefs.getSettings().playCacheOnly
            ) {
                loadRandomFromServer().ifEmpty { loadRandomBatch() }
            } else {
                loadRandomBatch()
            }
            if (more.isNotEmpty()) {
                mutex.withLock {
                    val pick = more.firstOrNull { it.id != endedId } ?: more.first()
                    if (pick.id == endedId && more.size == 1) {
                        Log.w(TAG, "advance: only one track available ('${pick.title}') — will re-play")
                    }
                    val idx = more.indexOfFirst { it.id == pick.id }.coerceAtLeast(0)
                    setQueue(more, idx)
                    Log.i(TAG, "advance: refilled ${more.size} → '${pick.title}'")
                    return pick
                }
            }
        }

        mutex.withLock {
            val state = _state.value
            if (state.repeatMode == RepeatMode.All && state.queue.isNotEmpty()) {
                val loopIndex = nextPlayableIndex(state, 0)
                if (loopIndex != null && loopIndex != state.index) {
                    val song = state.queue[loopIndex].song
                    playedIds.add(song.id)
                    _state.update { it.copy(index = loopIndex, current = song) }
                    prefetchAround(loopIndex)
                    return song
                }
            }
            if (state.mode == PlaybackMode.Favorites) {
                val songs = loadFavoritesSongs()
                if (songs.isNotEmpty()) {
                    setQueue(songs, 0)
                    return songs.first()
                }
            }
            Log.i(TAG, "advance: nothing playable (ended=$endedId offline=${state.isOffline})")
            return null
        }
    }
    suspend fun skipPrevious(): Song? = mutex.withLock {
        val state = _state.value
        if (state.mode == PlaybackMode.Radio) return state.current
        if (state.positionMs > 3000 || state.index == 0) {
            return state.current
        }
        val prev = state.index - 1
        val song = state.queue[prev].song
        _state.update { it.copy(index = prev, current = song) }
        prefetchAround(prev)
        return song
    }

    suspend fun toggleLike(song: Song): Boolean {
        val liked = _state.value.likedIds.contains(song.id)
        val newLiked = !liked
        // Optimistic local update (works offline)
        if (newLiked) {
            _state.update { it.copy(likedIds = it.likedIds + song.id) }
            starred = (starred.filterNot { it.id == song.id } + song.copy(starred = true))
            cacheManager.setLikedPinned(song.id, true)
            scope.launch { cacheManager.ensureCached(song, likedPinned = true) }
        } else {
            _state.update { it.copy(likedIds = it.likedIds - song.id) }
            starred = starred.filterNot { it.id == song.id }
            cacheManager.setLikedPinned(song.id, false)
        }
        starRepository.setStarredLocally(song, newLiked)
        return newLiked
    }

    suspend fun dislike(song: Song): Song? {
        val wasLiked = _state.value.likedIds.contains(song.id)
        if (wasLiked) {
            _state.update { it.copy(likedIds = it.likedIds - song.id) }
            starred = starred.filterNot { it.id == song.id }
            starRepository.setStarredLocally(song, starred = false)
        }
        blacklistDao.insert(BlacklistEntity(song.id))
        blacklist = blacklist + song.id
        cacheManager.deleteSong(song.id)
        return mutex.withLock {
            val state = _state.value
            val removedIndex = state.queue.indexOfFirst { it.song.id == song.id }
            val filtered = state.queue.filterNot { it.song.id == song.id }
            if (filtered.isEmpty()) {
                if (state.mode == PlaybackMode.LibraryRandom) {
                    val more = loadRandomBatch()
                    if (more.isEmpty()) {
                        _state.update { it.copy(current = null, queue = emptyList()) }
                        return@withLock null
                    }
                    setQueue(more, 0)
                    return@withLock more.first()
                }
                _state.update { it.copy(current = null, queue = emptyList()) }
                return@withLock null
            }
            val fallback = when {
                removedIndex < 0 -> state.index
                removedIndex >= filtered.size -> filtered.lastIndex
                else -> removedIndex
            }.coerceAtMost(filtered.lastIndex)
            val newIndex = nextPlayableIndex(state.copy(queue = filtered), fallback) ?: fallback
            _state.update {
                it.copy(
                    queue = filtered,
                    index = newIndex,
                    current = filtered[newIndex].song,
                )
            }
            prefetchAround(newIndex)
            filtered[newIndex].song
        }
    }

    suspend fun undoDislike(songId: String) {
        blacklistDao.delete(songId)
        blacklist = blacklist - songId
    }

    fun currentSong(): Song? = _state.value.current

    fun currentRadio(): RadioStation? = _state.value.radio

    private suspend fun setQueue(songs: List<Song>, index: Int) {
        val items = songs.map { song ->
            QueueItem(
                song = song,
                cacheStatus = if (cacheManager.hasComplete(song.id)) CacheStatus.Cached else CacheStatus.NotCached,
            )
        }
        val current = items.getOrNull(index)?.song
        if (current != null) playedIds.add(current.id)
        _state.update {
            it.copy(queue = items, index = index, current = current, errorMessage = null)
        }
        prefetchAround(index)
    }

    private fun prefetchAround(index: Int) {
        val state = _state.value
        val upcoming = state.queue.drop(index).map { it.song }
        cacheManager.prefetch(upcoming, state.likedIds)
    }

    private suspend fun loadFavoritesSongs(): List<Song> {
        val local = starRepository.getLocalStars().filterNot { blacklist.contains(it.id) }
        if (local.isNotEmpty()) return local
        return cacheManager.offlineSongs()
            .filter { it.starred || _state.value.likedIds.contains(it.id) }
            .filterNot { blacklist.contains(it.id) }
    }

    private suspend fun loadRandomBatch(): List<Song> {
        val settings = prefs.getSettings()
        val offline = _state.value.isOffline || settings.playCacheOnly || !client.isConfigured
        if (!offline) {
            val server = loadRandomFromServer()
            if (server.isNotEmpty()) return server
            Log.i(TAG, "server random failed/slow → cache fallback")
        }
        return loadRandomFromCache()
    }

    private suspend fun loadRandomFromCache(): List<Song> {
        val avoidCurrent = _state.value.current?.id
        val all = cacheManager.offlineSongs().filterNot { blacklist.contains(it.id) }
        val fresh = all.filterNot { playedIds.contains(it.id) }.shuffled()
        if (fresh.isNotEmpty()) {
            // Prefer not restarting the track that just ended when anything else is cached.
            val rotated = if (avoidCurrent != null && fresh.size > 1) {
                fresh.filterNot { it.id == avoidCurrent }.ifEmpty { fresh }
            } else {
                fresh
            }
            return rotated
        }
        playedIds.clear()
        val reshuffled = all.shuffled()
        return if (avoidCurrent != null && reshuffled.size > 1) {
            reshuffled.filterNot { it.id == avoidCurrent }.ifEmpty { reshuffled }
        } else {
            reshuffled
        }
    }

    private suspend fun loadRandomFromServer(): List<Song> {
        if (!client.isConfigured) return emptyList()
        val result = ArrayDeque<Song>()
        var attempts = 0
        while (result.size < 40 && attempts < 3) {
            attempts++
            val batch = withTimeoutOrNull(SERVER_RANDOM_TIMEOUT_MS) {
                runCatching { client.getRandomSongs(50) }.getOrDefault(emptyList())
            } ?: emptyList()
            for (song in batch) {
                if (blacklist.contains(song.id)) continue
                if (playedIds.contains(song.id)) continue
                if (result.any { it.id == song.id }) continue
                result.add(song)
            }
            if (batch.isEmpty()) break
        }
        if (result.isEmpty()) {
            playedIds.clear()
            val batch = withTimeoutOrNull(SERVER_RANDOM_TIMEOUT_MS) {
                runCatching { client.getRandomSongs(50) }.getOrDefault(emptyList())
            } ?: emptyList()
            return batch.filterNot { blacklist.contains(it.id) }
        }
        return result.toList()
    }

    companion object {
        private const val TAG = "MuzoraQueue"
        private const val SERVER_RANDOM_TIMEOUT_MS = 4_000L

        /** Prefer "Artist - Title"; fall back to raw title / artist fields. */
        internal fun parseIcyTitleArtist(
            rawTitle: String,
            rawArtist: String,
        ): Pair<String?, String?> {
            if (rawArtist.isNotEmpty() && rawTitle.isNotEmpty()) {
                return rawArtist to rawTitle
            }
            val blob = rawTitle.ifEmpty { rawArtist }
            if (blob.isEmpty()) return null to null
            for (sep in listOf(" - ", " – ", " — ", " | ")) {
                val idx = blob.indexOf(sep)
                if (idx > 0 && idx + sep.length < blob.length) {
                    return blob.substring(0, idx).trim() to
                        blob.substring(idx + sep.length).trim()
                }
            }
            return if (rawArtist.isNotEmpty() && rawTitle.isEmpty()) {
                rawArtist to null
            } else {
                null to blob
            }
        }
    }
}
