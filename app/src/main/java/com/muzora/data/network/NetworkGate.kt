package com.muzora.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.telephony.TelephonyManager
import android.util.Log
import com.muzora.data.api.SubsonicClient
import com.muzora.data.local.prefs.PreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gates server use on real link quality + stable Subsonic ping.
 * "Internet available" alone is not enough (captive / broken DNS / 2G-3G).
 */
class NetworkGate(
    context: Context,
    private val client: SubsonicClient,
    private val prefs: PreferencesRepository,
) {
    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val probeMutex = Mutex()

    private val _serverUsable = MutableStateFlow(false)
    val serverUsable: StateFlow<Boolean> = _serverUsable.asStateFlow()

    fun isServerUsable(): Boolean = _serverUsable.value

    fun hasQualityLink(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return isQualityNetwork(caps)
    }

    fun isQualityNetwork(caps: NetworkCapabilities): Boolean {
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return false

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) {
            return true
        }

        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return false

        // Prefer LTE/5G; ignore EDGE/3G even if VALIDATED.
        if (isLteOrBetterCellular()) return true

        val down = caps.linkDownstreamBandwidthKbps
        return down >= MIN_CELLULAR_DOWN_KBPS
    }

    /**
     * @return true if the server answered quickly enough to stream/download.
     */
    suspend fun probeServer(requireStable: Boolean = true): Boolean = probeMutex.withLock {
        if (prefs.getSettings().playCacheOnly) {
            _serverUsable.value = false
            return false
        }
        if (!client.isConfigured) {
            _serverUsable.value = false
            return false
        }
        if (!hasQualityLink()) {
            Log.i(TAG, "probe: no quality link")
            _serverUsable.value = false
            return false
        }

        val needed = if (requireStable) STABLE_SUCCESSES else 1
        var okCount = 0
        repeat(needed + 1) { attempt ->
            val latency = pingLatencyMs()
            if (latency != null && latency <= MAX_PING_MS) {
                okCount++
                Log.i(TAG, "probe ok #$okCount latency=${latency}ms")
                if (okCount >= needed) {
                    _serverUsable.value = true
                    return true
                }
            } else {
                Log.i(TAG, "probe fail attempt=$attempt latency=$latency")
                okCount = 0
                if (attempt < needed) {
                    kotlinx.coroutines.delay(400L * (attempt + 1))
                }
            }
        }
        _serverUsable.value = false
        return false
    }

    fun markUnusable() {
        _serverUsable.value = false
    }

    private suspend fun pingLatencyMs(): Long? {
        val start = SystemClock.elapsedRealtime()
        val ok = withTimeoutOrNull(MAX_PING_MS + 500L) {
            runCatching { client.ping() }.getOrDefault(false)
        } ?: false
        if (!ok) return null
        return SystemClock.elapsedRealtime() - start
    }

    private fun isLteOrBetterCellular(): Boolean {
        val tm = appContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            ?: return false
        return try {
            val type = if (Build.VERSION.SDK_INT >= 24) {
                tm.dataNetworkType
            } else {
                @Suppress("DEPRECATION")
                tm.networkType
            }
            when (type) {
                TelephonyManager.NETWORK_TYPE_LTE,
                TelephonyManager.NETWORK_TYPE_NR,
                TelephonyManager.NETWORK_TYPE_IWLAN,
                -> true
                else -> false
            }
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        private const val TAG = "MuzoraNet"
        /** Advertised cellular floor roughly above 3G. */
        const val MIN_CELLULAR_DOWN_KBPS = 1_500
        const val MAX_PING_MS = 2_000L
        const val STABLE_SUCCESSES = 2
    }
}
