package com.muzora.data.local.cache

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.muzora.data.api.SubsonicClient
import com.muzora.data.local.db.CacheDao
import com.muzora.data.local.db.CacheEntity
import com.muzora.data.local.prefs.PreferencesRepository
import com.muzora.domain.model.CacheStatus
import com.muzora.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

class CacheManager(
    private val context: Context,
    private val cacheDao: CacheDao,
    private val client: SubsonicClient,
    private val prefs: PreferencesRepository,
    private val okHttp: OkHttpClient,
    private val scope: CoroutineScope,
    private val networkGate: com.muzora.data.network.NetworkGate,
) {
    private val mutex = Mutex()
    private val downloading = MutableStateFlow<Set<String>>(emptySet())
    private var prefetchJob: Job? = null

    private val _statuses = MutableStateFlow<Map<String, CacheStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, CacheStatus>> = _statuses.asStateFlow()

    private val cacheDir: File by lazy {
        File(context.noBackupFilesDir, "songs").also { it.mkdirs() }
    }

    suspend fun validateOnStart() {
        // Drop incomplete downloads only
        cacheDir.listFiles()
            ?.filter { it.name.endsWith(".part") }
            ?.forEach { runCatching { it.delete() } }

        cacheDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && it.length() > 0L }
            ?.forEach { file ->
                val id = file.nameWithoutExtension
                if (id.isBlank()) return@forEach
                val entity = cacheDao.get(id)
                if (entity == null) {
                    // DB was wiped/migrated — rebuild index from file, do NOT delete
                    recoverOrphanFile(file, id)
                } else {
                    val onDisk = File(entity.filePath)
                    if (!onDisk.exists() || onDisk.absolutePath != file.absolutePath) {
                        cacheDao.upsert(
                            entity.copy(
                                filePath = file.absolutePath,
                                bytes = file.length(),
                                contentLength = file.length(),
                                complete = true,
                            )
                        )
                    } else if (file.length() <= 0L) {
                        runCatching { file.delete() }
                        cacheDao.delete(id)
                    }
                }
            }

        for (entity in cacheDao.getComplete()) {
            if (!File(entity.filePath).exists()) {
                // Try alternate path in cacheDir
                val alt = File(cacheDir, "${entity.songId}.opus")
                if (alt.exists() && alt.length() > 0L) {
                    cacheDao.upsert(
                        entity.copy(
                            filePath = alt.absolutePath,
                            bytes = alt.length(),
                            contentLength = alt.length(),
                            complete = true,
                        )
                    )
                } else {
                    cacheDao.delete(entity.songId)
                }
            }
        }
        refreshStatuses(cacheDao.getComplete().map { it.songId })
    }

    private suspend fun recoverOrphanFile(file: File, id: String) {
        val meta = readFileMetadata(file)
        cacheDao.upsert(
            CacheEntity(
                songId = id,
                filePath = file.absolutePath,
                bytes = file.length(),
                contentLength = file.length(),
                complete = true,
                likedPinned = false,
                lastAccess = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                title = meta.title,
                artist = meta.artist,
                album = meta.album,
                coverArt = null,
                duration = meta.durationSec,
            )
        )
    }

    private data class FileMeta(
        val title: String,
        val artist: String,
        val album: String,
        val durationSec: Int,
    )

    private fun readFileMetadata(file: File): FileMeta {
        return runCatching {
            val mmr = android.media.MediaMetadataRetriever()
            try {
                mmr.setDataSource(file.absolutePath)
                val title = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf { it.isNotBlank() }
                    ?: file.nameWithoutExtension
                val artist = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?.takeIf { it.isNotBlank() }
                    ?: ""
                val album = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    ?.takeIf { it.isNotBlank() }
                    ?: ""
                val durationMs = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                FileMeta(title, artist, album, (durationMs / 1000L).toInt())
            } finally {
                runCatching { mmr.release() }
            }
        }.getOrElse {
            FileMeta(title = file.nameWithoutExtension, artist = "", album = "", durationSec = 0)
        }
    }

    fun statusOf(songId: String): CacheStatus {
        return _statuses.value[songId]
            ?: if (downloading.value.contains(songId)) CacheStatus.Downloading
            else CacheStatus.NotCached
    }

    suspend fun getLocalFile(songId: String): File? {
        val entity = cacheDao.get(songId) ?: return null
        if (!entity.complete) return null
        val file = File(entity.filePath)
        if (!file.exists() || file.length() <= 0) {
            cacheDao.delete(songId)
            return null
        }
        cacheDao.touch(songId)
        return file
    }

    suspend fun hasComplete(songId: String): Boolean = getLocalFile(songId) != null

    fun prefetch(songs: List<Song>, likedIds: Set<String>) {
        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            val settings = prefs.getSettings()
            val toFetch = songs.take(settings.prefetchCount + 1)
            for (song in toFetch) {
                ensureCached(song, likedIds.contains(song.id))
            }
        }
    }

    suspend fun ensureCached(song: Song, likedPinned: Boolean): Boolean = mutex.withLock {
        if (hasComplete(song.id)) {
            cacheDao.setLikedPinned(song.id, likedPinned)
            markStatus(song.id, CacheStatus.Cached)
            return true
        }
        if (!canDownloadNow()) return false
        markDownloading(song.id, true)
        markStatus(song.id, CacheStatus.Downloading)
        try {
            val ok = downloadWithRetries(song, likedPinned)
            markStatus(song.id, if (ok) CacheStatus.Cached else CacheStatus.NotCached)
            return ok
        } finally {
            markDownloading(song.id, false)
        }
    }

    suspend fun markPlayedComplete(song: Song, liked: Boolean) {
        val entity = cacheDao.get(song.id)
        if (entity?.complete == true) {
            cacheDao.setLikedPinned(song.id, liked || entity.likedPinned)
            cacheDao.touch(song.id)
        }
    }

    suspend fun setLikedPinned(songId: String, pinned: Boolean) {
        cacheDao.setLikedPinned(songId, pinned)
    }

    suspend fun deleteSong(songId: String) {
        val entity = cacheDao.get(songId)
        if (entity != null) {
            runCatching { File(entity.filePath).delete() }
            cacheDao.delete(songId)
        } else {
            File(cacheDir, "$songId.opus").delete()
        }
        markStatus(songId, CacheStatus.NotCached)
    }

    suspend fun offlineSongs(likedFirst: Boolean = true): List<Song> {
        val items = cacheDao.getComplete()
        val mapped = items.map { it.toSong() }
        return if (likedFirst) {
            mapped.sortedByDescending { it.starred }
        } else mapped
    }

    suspend fun searchOffline(query: String): List<Song> {
        if (query.isBlank()) return emptyList()
        return cacheDao.search(query).map { it.toSong() }
    }

    private suspend fun downloadWithRetries(song: Song, likedPinned: Boolean): Boolean {
        repeat(3) { attempt ->
            val ok = runCatching { downloadOnce(song, likedPinned) }.getOrDefault(false)
            if (ok) {
                evictIfNeeded()
                return true
            }
            if (attempt < 2) {
                kotlinx.coroutines.delay(500L * (attempt + 1))
            }
        }
        // remove partial
        File(cacheDir, "${song.id}.opus").delete()
        File(cacheDir, "${song.id}.part").delete()
        cacheDao.delete(song.id)
        return false
    }

    private suspend fun downloadOnce(song: Song, likedPinned: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!client.isConfigured) return@withContext false
        val settings = prefs.getSettings()
        val builder = client.streamUrlBuilder()
        val url = builder.streamUrl(song.id, settings.opusBitrate)
        val request = Request.Builder().url(url).get().build()
        okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext false
            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            val part = File(cacheDir, "${song.id}.part")
            val target = File(cacheDir, "${song.id}.opus")
            body.byteStream().use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            if (contentLength > 0 && part.length() != contentLength) {
                part.delete()
                return@withContext false
            }
            if (part.length() <= 0) {
                part.delete()
                return@withContext false
            }
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            cacheDao.upsert(
                CacheEntity(
                    songId = song.id,
                    filePath = target.absolutePath,
                    bytes = target.length(),
                    contentLength = if (contentLength > 0) contentLength else target.length(),
                    complete = true,
                    likedPinned = likedPinned,
                    lastAccess = System.currentTimeMillis(),
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    coverArt = song.coverArt,
                    duration = song.duration,
                )
            )
            true
        }
    }

    private suspend fun evictIfNeeded() {
        var total = cacheDao.totalBytes()
        if (total <= MAX_CACHE_BYTES) return
        val victims = cacheDao.oldestNonLiked()
        for (victim in victims) {
            if (total <= MAX_CACHE_BYTES) break
            File(victim.filePath).delete()
            cacheDao.delete(victim.songId)
            total -= victim.bytes
            markStatus(victim.songId, CacheStatus.NotCached)
        }
    }

    suspend fun canDownloadNow(): Boolean {
        if (prefs.getSettings().playCacheOnly) return false
        if (!networkGate.isServerUsable()) return false
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        if (!networkGate.isQualityNetwork(caps)) return false
        val settings = prefs.getSettings()
        if (settings.wifiOnly) {
            return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        }
        return true
    }


    private fun markDownloading(id: String, active: Boolean) {
        downloading.update { current ->
            if (active) current + id else current - id
        }
    }

    private fun markStatus(id: String, status: CacheStatus) {
        _statuses.update { it + (id to status) }
    }

    private suspend fun refreshStatuses(ids: List<String>) {
        val map = mutableMapOf<String, CacheStatus>()
        for (id in ids) {
            map[id] = if (hasComplete(id)) CacheStatus.Cached else CacheStatus.NotCached
        }
        _statuses.update { it + map }
    }

    companion object {
        const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024
    }
}

private fun CacheEntity.toSong(): Song = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    coverArt = coverArt,
    duration = duration,
    starred = likedPinned,
)
