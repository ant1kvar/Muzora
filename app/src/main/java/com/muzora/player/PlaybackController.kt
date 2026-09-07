package com.muzora.player

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.muzora.domain.model.RadioStation
import com.muzora.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

object PlaybackController {
    private var serviceRef: WeakReference<PlayerService>? = null
    private var queueManager: QueueManager? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pending = CopyOnWriteArrayList<suspend PlayerService.() -> Unit>()

    fun init(qm: QueueManager) {
        queueManager = qm
    }

    fun bind(service: PlayerService) {
        serviceRef = WeakReference(service)
        val actions = pending.toList()
        pending.clear()
        scope.launch {
            actions.forEach { action ->
                runCatching { service.action() }
            }
        }
    }

    fun unbind(service: PlayerService) {
        if (serviceRef?.get() === service) {
            serviceRef = null
        }
    }

    val state: StateFlow<PlayerUiState>?
        get() = queueManager?.state

    fun ensureService(context: Context) {
        val intent = Intent(context, PlayerService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun withService(context: Context, action: suspend PlayerService.() -> Unit) {
        ensureService(context)
        val service = serviceRef?.get()
        if (service != null) {
            scope.launch { service.action() }
        } else {
            pending.add(action)
        }
    }

    fun shuffle(context: Context) {
        withService(context) {
            queueManager?.startShuffle()
            playCurrent()
        }
    }

    fun playFavorites(context: Context, startId: String? = null) {
        withService(context) {
            queueManager?.startFavorites(startId)
            playCurrent()
        }
    }

    fun playSongThenRandom(context: Context, song: Song) {
        withService(context) {
            queueManager?.playSongThenRandom(song)
            playCurrent()
        }
    }

    fun playRadio(context: Context, station: RadioStation) {
        withService(context) {
            queueManager?.playRadio(station)
            playCurrent()
        }
    }

    /** Stop radio stream and start library shuffle (back to server music). */
    fun leaveRadioToMusic(context: Context) {
        shuffle(context)
    }

    fun playPause(context: Context) {
        withService(context) { playPause() }
    }

    /** Pause player, clear queue, stop foreground service (logout). */
    fun stopAndClear(context: Context) {
        pending.clear()
        queueManager?.clearPlayback()
        val service = serviceRef?.get()
        if (service != null) {
            scope.launch { service.stopPlayback() }
        } else {
            context.stopService(Intent(context, PlayerService::class.java))
        }
    }

    fun skipNext(context: Context) {
        withService(context) { skipNext() }
    }

    fun skipPrevious(context: Context) {
        withService(context) { skipPrevious() }
    }

    fun seekTo(positionMs: Long) {
        serviceRef?.get()?.seekTo(positionMs)
    }

    fun jumpTo(context: Context, index: Int) {
        withService(context) {
            queueManager?.jumpTo(index)
            playCurrent()
        }
    }

    fun cycleRepeat() {
        val mode = queueManager?.cycleRepeat() ?: return
        serviceRef?.get()?.applyRepeatMode(mode)
    }

    fun toggleLike(song: Song, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            val liked = queueManager?.toggleLike(song) ?: false
            onResult(liked)
        }
    }

    fun dislike(context: Context, song: Song, onUndoneReady: (String) -> Unit) {
        withService(context) {
            val next = queueManager?.dislike(song)
            onUndoneReady(song.id)
            if (next != null) {
                playCurrent()
            }
        }
    }

    fun undoDislike(songId: String) {
        scope.launch { queueManager?.undoDislike(songId) }
    }

    fun refreshStarred() {
        scope.launch { queueManager?.refreshStarred() }
    }

    fun syncStars() {
        scope.launch { queueManager?.syncStars() }
    }

    fun setOffline(offline: Boolean) {
        queueManager?.setOffline(offline)
    }
}
