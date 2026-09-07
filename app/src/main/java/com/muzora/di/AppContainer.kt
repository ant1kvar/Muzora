package com.muzora.di

import android.content.Context
import androidx.room.Room
import com.muzora.data.api.SubsonicClient
import com.muzora.data.local.cache.CacheManager
import com.muzora.data.local.db.MuzoraDatabase
import com.muzora.data.local.prefs.PreferencesRepository
import com.muzora.data.network.NetworkGate
import com.muzora.player.PlaybackController
import com.muzora.player.QueueManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val preferences = PreferencesRepository(appContext)

    val database: MuzoraDatabase = Room.databaseBuilder(
        appContext,
        MuzoraDatabase::class.java,
        "muzora.db",
    )
        .addMigrations(com.muzora.data.local.db.DatabaseMigrations.MIGRATION_1_2)
        .build()

    /** Short timeouts for API (ping / random). Downloads use [downloadOkHttp]. */
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build()

    val downloadOkHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val subsonicClient = SubsonicClient(okHttp)

    val networkGate = NetworkGate(
        context = appContext,
        client = subsonicClient,
        prefs = preferences,
    )

    val cacheManager = CacheManager(
        context = appContext,
        cacheDao = database.cacheDao(),
        client = subsonicClient,
        prefs = preferences,
        okHttp = downloadOkHttp,
        scope = applicationScope,
        networkGate = networkGate,
    )

    val starRepository = com.muzora.data.repo.StarRepository(
        context = appContext,
        localStarDao = database.localStarDao(),
        pendingStarDao = database.pendingStarDao(),
        client = subsonicClient,
    )

    val queueManager = QueueManager(
        client = subsonicClient,
        blacklistDao = database.blacklistDao(),
        cacheManager = cacheManager,
        prefs = preferences,
        scope = applicationScope,
        starRepository = starRepository,
    )

    init {
        PlaybackController.init(queueManager)
        applicationScope.launch {
            cacheManager.validateOnStart()
            starRepository.restoreFromBackupIfEmpty()
            queueManager.loadLocalStars()
            // Pin recovered cache entries that are liked
            starRepository.getLocalStarIds().forEach { id ->
                runCatching { cacheManager.setLikedPinned(id, true) }
            }
            val session = preferences.getSession()
            if (session != null) {
                subsonicClient.configure(session.serverUrl, session.username, session.password)
                val ok = runCatching { networkGate.probeServer(requireStable = true) }.getOrDefault(false)
                queueManager.setOffline(!ok)
                if (ok) {
                    runCatching { queueManager.syncStars() }
                }
            }
        }
    }
}
