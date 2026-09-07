package com.muzora.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.muzora.di.AppContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

class NetworkMonitor(
    context: Context,
    private val container: AppContainer,
) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val evalMutex = Mutex()
    private var healthJob: Job? = null
    private var recoveryJob: Job? = null

    fun start() {
        if (!started.compareAndSet(false, true)) {
            Log.i(TAG, "start: already running")
            return
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                container.applicationScope.launch { evaluate(reason = "available") }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                container.applicationScope.launch { evaluate(reason = "caps") }
            }

            override fun onLost(network: Network) {
                container.applicationScope.launch {
                    goOffline(reason = "network lost")
                }
            }
        })

        container.applicationScope.launch { evaluate(reason = "start") }
    }

    private suspend fun evaluate(reason: String) {
        evalMutex.withLock {
            doEvaluate(reason)
        }
    }

    private suspend fun doEvaluate(reason: String) {
        val settings = container.preferences.getSettings()
        if (settings.playCacheOnly) {
            goOffline(reason = "playCacheOnly", cancelHealth = true)
            return
        }
        if (!container.subsonicClient.isConfigured) {
            goOffline(reason = "not configured", cancelHealth = true)
            return
        }

        if (!container.networkGate.hasQualityLink()) {
            Log.i(TAG, "evaluate($reason): weak link → cache-only")
            goOffline(reason = "weak link", cancelHealth = true)
            return
        }

        val wasUsable = container.networkGate.isServerUsable()
        val needsHandoff = container.queueManager.needsServerHandoff()
        val ok = container.networkGate.probeServer(requireStable = !wasUsable || needsHandoff)
        container.queueManager.setOffline(!ok)
        Log.i(
            TAG,
            "evaluate($reason): serverUsable=$ok (was=$wasUsable, needsHandoff=$needsHandoff)",
        )

        if (ok) {
            recoveryJob?.cancel()
            if (!wasUsable || needsHandoff) {
                runCatching { container.queueManager.onServerBecameReachable() }
                    .onFailure { Log.w(TAG, "handoff failed", it) }
            } else {
                val state = container.queueManager.state.value
                val upcoming = state.queue.drop(state.index).map { it.song }
                container.cacheManager.prefetch(upcoming, state.likedIds)
            }
            startHealthLoop()
        } else {
            // Link looks ok but ping failed — keep retrying; don't wait for another caps event.
            scheduleRecovery(reason = "probe-fail")
        }
    }

    private fun goOffline(reason: String, cancelHealth: Boolean = true) {
        container.networkGate.markUnusable()
        container.queueManager.setOffline(true)
        if (cancelHealth) {
            healthJob?.cancel()
            healthJob = null
        }
        Log.i(TAG, "$reason → offline")
        scheduleRecovery(reason = reason)
    }

    private fun scheduleRecovery(reason: String) {
        if (recoveryJob?.isActive == true) return
        recoveryJob = container.applicationScope.launch {
            var attempt = 0
            while (attempt < RECOVERY_ATTEMPTS) {
                attempt++
                delay(RECOVERY_DELAY_MS * attempt)
                if (container.networkGate.isServerUsable() &&
                    !container.queueManager.needsServerHandoff()
                ) {
                    return@launch
                }
                Log.i(TAG, "recovery #$attempt after $reason")
                evaluate(reason = "recovery")
                if (container.networkGate.isServerUsable()) return@launch
            }
        }
    }

    private fun startHealthLoop() {
        if (healthJob?.isActive == true) return
        healthJob = container.applicationScope.launch {
            while (true) {
                delay(HEALTH_INTERVAL_MS)
                val ok = container.networkGate.probeServer(requireStable = false)
                if (!ok) {
                    // Transient ping loss while the link may still be up — force offline
                    // AND schedule recovery so handoff can run when the server returns.
                    goOffline(reason = "health: ping lost", cancelHealth = false)
                    break
                }
            }
        }
    }

    companion object {
        private const val TAG = "MuzoraNet"
        private const val HEALTH_INTERVAL_MS = 30_000L
        private const val RECOVERY_DELAY_MS = 2_000L
        private const val RECOVERY_ATTEMPTS = 8
        private val started = AtomicBoolean(false)
    }
}
