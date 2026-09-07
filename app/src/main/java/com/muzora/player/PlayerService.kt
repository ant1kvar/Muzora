package com.muzora.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.muzora.MainActivity
import com.muzora.R
import com.muzora.MuzoraApp
import com.muzora.domain.model.PlaybackMode
import com.muzora.domain.model.RepeatMode
import com.muzora.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {
    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var progressJob: Job? = null
    private var preferServerNext = true

    private val container get() = (application as MuzoraApp).container
    private val queueManager get() = container.queueManager
    private val cacheManager get() = container.cacheManager
    private val client get() = container.subsonicClient
    private val prefs get() = container.preferences

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .setNotificationId(NOTIFICATION_ID)
                .build()
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            // Keep CPU + Wi‑Fi awake while streaming with the screen off
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publishProgress(isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch { advance(scrobble = true) }
                }
                if (playbackState == Player.STATE_READY) {
                    publishProgress(player?.isPlaying == true)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (queueManager.state.value.mode != PlaybackMode.Radio) return
                val title = mediaMetadata.title?.toString()
                    ?: mediaMetadata.displayTitle?.toString()
                val artist = mediaMetadata.artist?.toString()
                    ?: mediaMetadata.albumArtist?.toString()
                queueManager.updateRadioStreamInfo(title, artist)
            }

            override fun onMetadata(metadata: androidx.media3.common.Metadata) {
                if (queueManager.state.value.mode != PlaybackMode.Radio) return
                for (i in 0 until metadata.length()) {
                    val entry = metadata.get(i)
                    if (entry is androidx.media3.extractor.metadata.icy.IcyInfo) {
                        queueManager.updateRadioStreamInfo(entry.title, null)
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                scope.launch {
                    queueManager.setError(error.message)
                    // Radio has no library queue — don't skip-ahead into song logic.
                    if (queueManager.state.value.mode == PlaybackMode.Radio) {
                        player?.stop()
                        return@launch
                    }
                    if (!cacheManager.canDownloadNow()) {
                        container.networkGate.markUnusable()
                        queueManager.setOffline(true)
                    }
                    val current = queueManager.currentSong()
                    if (current != null) {
                        val local = cacheManager.getLocalFile(current.id)
                        if (local != null && preferServerNext) {
                            preferServerNext = false
                            playSong(current)
                            return@launch
                        }
                    }
                    advance(scrobble = false)
                }
            }
        })

        player = exo
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        mediaSession = MediaSession.Builder(this, exo)
            .setSessionActivity(sessionActivity)
            .setId("muzora_session")
            .build()

        progressJob = scope.launch {
            while (isActive) {
                val p = player
                if (p != null) {
                    val playerDur = p.duration.takeUnless { it == C.TIME_UNSET || it < 0 } ?: 0L
                    val metaDur = (queueManager.currentSong()?.duration ?: 0).toLong() * 1000L
                    queueManager.updatePlaybackProgress(
                        positionMs = p.currentPosition.coerceAtLeast(0),
                        durationMs = playerDur.takeIf { it > 0 } ?: metaDur,
                        isPlaying = p.isPlaying,
                    )
                }
                delay(250)
            }
        }

        PlaybackController.bind(this)
    }

    private fun publishProgress(isPlaying: Boolean) {
        val p = player ?: return
        val playerDur = p.duration.takeUnless { it == C.TIME_UNSET || it < 0 } ?: 0L
        val metaDur = (queueManager.currentSong()?.duration ?: 0).toLong() * 1000L
        queueManager.updatePlaybackProgress(
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = playerDur.takeIf { it > 0 } ?: metaDur,
            isPlaying = isPlaying,
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        val keep = p != null && p.playWhenReady &&
            p.playbackState != Player.STATE_IDLE &&
            p.playbackState != Player.STATE_ENDED
        if (!keep) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        PlaybackController.unbind(this)
        progressJob?.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        serviceJob.cancel()
        super.onDestroy()
    }

    suspend fun playCurrent() {
        val state = queueManager.state.value
        if (state.mode == PlaybackMode.Radio) {
            val radio = state.radio ?: return
            val mime = streamMimeType(radio.streamUrl)
            val item = MediaItem.Builder()
                .setUri(radio.streamUrl)
                .setMediaId(radio.id)
                .apply { if (mime != null) setMimeType(mime) }
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(radio.name)
                        .setArtist("Radio")
                        .build()
                )
                .build()
            player?.apply {
                setMediaItem(item)
                prepare()
                play()
            }
            return
        }
        val song = state.current ?: return
        playSong(song)
    }

    private suspend fun playSong(song: Song) {
        val settings = prefs.getSettings()
        val offline = queueManager.state.value.isOffline ||
            settings.playCacheOnly ||
            !container.networkGate.isServerUsable()
        val local = cacheManager.getLocalFile(song.id)
        // Prefer complete cache for seekability. Stream only when server is actually usable.
        val uri = when {
            local != null -> local.toURI().toString()
            !offline && client.isConfigured && cacheManager.canDownloadNow() ->
                client.streamUrlBuilder().streamUrl(song.id, settings.opusBitrate)
            else -> {
                // Unplayable right now (uncached + no usable server) — skip ahead.
                val next = queueManager.skipNext()
                if (next != null && next.id != song.id) {
                    playSong(next)
                } else {
                    queueManager.setError("empty")
                }
                return
            }
        }

        val cover = if (client.isConfigured && container.networkGate.isServerUsable()) {
            client.streamUrlBuilder().coverArtUrl(song.coverArt)
        } else null

        val durationMs = (song.duration.takeIf { it > 0 }?.toLong() ?: 0L) * 1000L
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(cover?.let { android.net.Uri.parse(it) })
                    .setDurationMs(durationMs)
                    .build()
            )
            .build()

        player?.apply {
            // Always manage repeat in QueueManager — never let Exo loop a single item.
            repeatMode = Player.REPEAT_MODE_OFF
            setMediaItem(mediaItem, /* resetPosition = */ true)
            prepare()
            playWhenReady = true
        }

        // Publish metadata duration immediately so the seek bar works before Exo reports length
        if (durationMs > 0) {
            queueManager.updatePlaybackProgress(0L, durationMs, isPlaying = true)
        }

        val idx = queueManager.state.value.index
        val upcoming = queueManager.state.value.queue.drop(idx).map { it.song }
        cacheManager.prefetch(upcoming, queueManager.state.value.likedIds)

        if (client.isConfigured && container.networkGate.isServerUsable()) {
            client.scrobble(song.id, submission = false)
        }
    }

    fun seekTo(positionMs: Long) {
        val p = player ?: return
        val target = positionMs.coerceAtLeast(0)
        if (p.playbackState == Player.STATE_IDLE) {
            p.prepare()
        }
        p.seekTo(target)
        publishProgress(p.isPlaying || p.playWhenReady)
    }

    private suspend fun advance(scrobble: Boolean) {
        val current = queueManager.currentSong()
        if (scrobble && current != null && client.isConfigured &&
            queueManager.state.value.mode != PlaybackMode.Radio
        ) {
            client.scrobble(current.id, submission = true)
            cacheManager.markPlayedComplete(
                current,
                liked = queueManager.state.value.likedIds.contains(current.id),
            )
        }
        preferServerNext = cacheManager.canDownloadNow() && !prefs.getSettings().playCacheOnly
        val next = queueManager.onTrackEnded()
        if (next != null) {
            playSong(next)
        }
    }

    fun playPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun stopPlayback() {
        player?.apply {
            pause()
            stop()
            clearMediaItems()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    suspend fun skipNext() {
        preferServerNext = cacheManager.canDownloadNow()
        val next = queueManager.skipNext()
        if (next != null) playSong(next)
        else if (queueManager.state.value.mode == PlaybackMode.Radio) {
            playCurrent()
        }
    }

    suspend fun skipPrevious() {
        val p = player ?: return
        val beforeIndex = queueManager.state.value.index
        if (p.currentPosition > 3000 || beforeIndex == 0) {
            p.seekTo(0)
            return
        }
        val prev = queueManager.skipPrevious()
        if (prev != null && queueManager.state.value.index != beforeIndex) {
            playSong(prev)
        } else {
            p.seekTo(0)
        }
    }

    fun applyRepeatMode(mode: RepeatMode) {
        player?.repeatMode = Player.REPEAT_MODE_OFF
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "muzora_playback"
        const val NOTIFICATION_ID = 0x57A5

        /** Hint ExoPlayer for HLS / playlist URLs (needs media3-exoplayer-hls). */
        fun streamMimeType(url: String): String? {
            val path = url.substringBefore('#').substringBefore('?').lowercase(Locale.US)
            return when {
                path.endsWith(".m3u8") || path.endsWith(".m3u") -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
                else -> null
            }
        }
    }
}
