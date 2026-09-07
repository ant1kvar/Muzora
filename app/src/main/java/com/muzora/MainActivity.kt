package com.muzora

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.muzora.data.network.NetworkMonitor
import com.muzora.player.PlayerService
import com.muzora.ui.components.NavAntennaIcon
import com.muzora.ui.components.NavChipIcon
import com.muzora.ui.components.NavNowIcon
import com.muzora.ui.components.NavStarIcon
import com.muzora.ui.components.PixelNavBar
import com.muzora.ui.components.PixelNavItem
import com.muzora.ui.favorites.FavoritesScreen
import com.muzora.ui.login.LoginScreen
import com.muzora.ui.login.StartExit
import com.muzora.ui.nav.Routes
import com.muzora.ui.nowplaying.NowPlayingScreen
import com.muzora.ui.radio.RadioScreen
import com.muzora.ui.search.SearchScreen
import com.muzora.ui.settings.SettingsScreen
import com.muzora.ui.theme.LocalMuzoraColors
import com.muzora.ui.theme.MuzoraTheme
import com.muzora.ui.theme.PressStart2P
import com.muzora.ui.theme.rememberTrackAccent
import com.muzora.util.BatteryOptimization
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : AppCompatActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* notification permission required for reliable background playback */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as MuzoraApp
        runBlocking {
            val prefs = app.container.preferences
            val lang = if (prefs.hasLanguagePreference()) {
                prefs.getSettings().language
            } else {
                prefs.updateSettings { it.copy(language = "en") }
                "en"
            }
            MuzoraApp.applyAppLanguage(app, lang)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        setContent {
            MuzoraAppRoot()
        }
    }

    override fun onStart() {
        super.onStart()
        if (controllerFuture == null) {
            val token = SessionToken(this, ComponentName(this, PlayerService::class.java))
            val future = MediaController.Builder(this, token).buildAsync()
            controllerFuture = future
            future.addListener(
                { runCatching { future.get() } },
                MoreExecutors.directExecutor(),
            )
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onDestroy()
    }
}

@Composable
private fun MuzoraAppRoot() {
    val context = LocalContext.current
    val app = context.applicationContext as MuzoraApp
    val session by app.container.preferences.sessionFlow.collectAsState(initial = null)
    val playerState by app.container.queueManager.state.collectAsState()
    var bootstrapped by remember { mutableStateOf(false) }
    /** Saved Subsonic session — Start Connected, not auto-enter player. */
    var hasSession by remember { mutableStateOf(false) }
    /** User pressed Shuffle / Radio on Start — inside main tabs. */
    var inApp by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        NetworkMonitor(context, app.container).start()
        hasSession = app.container.preferences.getSession() != null
        bootstrapped = true
    }

    LaunchedEffect(session) {
        hasSession = session != null
        if (session == null) inApp = false
    }

    val coverUrl = remember(playerState.current?.coverArt, playerState.mode, inApp) {
        val art = playerState.current?.coverArt
        if (!inApp || art.isNullOrBlank()) null
        else if (app.container.subsonicClient.isConfigured) {
            app.container.subsonicClient.streamUrlBuilder().coverArtUrl(art, 200)
        } else null
    }
    val accent = rememberTrackAccent(
        song = if (inApp) playerState.current else null,
        coverUrl = coverUrl,
    )

    MuzoraTheme(accent = accent) {
        if (!bootstrapped) return@MuzoraTheme

        val navController = rememberNavController()
        val start = if (inApp) Routes.Main else Routes.Login
        val colors = LocalMuzoraColors.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg),
        ) {
            var mainStartRoute by remember { mutableStateOf(Routes.NowPlaying) }

            NavHost(navController = navController, startDestination = start) {
                composable(Routes.Login) {
                    LoginScreen(
                        initialConnected = hasSession,
                        onEnterApp = { exit ->
                            when (exit) {
                                StartExit.Shuffle -> {
                                    mainStartRoute = Routes.NowPlaying
                                    inApp = true
                                    navController.navigate(Routes.Main) {
                                        popUpTo(Routes.Login) { inclusive = true }
                                    }
                                }
                                StartExit.Radio -> {
                                    if (hasSession) {
                                        mainStartRoute = Routes.Radio
                                        inApp = true
                                        navController.navigate(Routes.Main) {
                                            popUpTo(Routes.Login) { inclusive = true }
                                        }
                                    } else {
                                        navController.navigate(Routes.GuestRadio)
                                    }
                                }
                            }
                        },
                    )
                }
                composable(Routes.GuestRadio) {
                    RadioScreen(
                        showBottomBack = true,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.Main) {
                    MainScaffold(
                        startRoute = mainStartRoute,
                        onLoggedOut = {
                            inApp = false
                            hasSession = false
                            mainStartRoute = Routes.NowPlaying
                            navController.navigate(Routes.Login) {
                                popUpTo(Routes.Main) { inclusive = true }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainScaffold(
    onLoggedOut: () -> Unit,
    startRoute: String = Routes.NowPlaying,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuzoraApp
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val colors = LocalMuzoraColors.current
    val backStack by navController.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    var showBatteryDialog by remember { mutableStateOf(false) }

    val selectedForNav = when (current) {
        Routes.Search -> Routes.NowPlaying
        else -> current
    }

    val tabs = listOf(
        PixelNavItem(Routes.NowPlaying, stringResource(R.string.nav_now_playing)) { selected ->
            NavNowIcon(tint = if (selected) colors.accent else colors.muted)
        },
        PixelNavItem(Routes.Favorites, stringResource(R.string.nav_favorites)) { selected ->
            NavStarIcon(tint = if (selected) colors.accent else colors.muted)
        },
        PixelNavItem(Routes.Radio, stringResource(R.string.nav_radio)) { selected ->
            NavAntennaIcon(tint = if (selected) colors.accent else colors.muted)
        },
        PixelNavItem(Routes.Settings, stringResource(R.string.nav_settings)) { selected ->
            NavChipIcon(tint = if (selected) colors.accent else colors.muted)
        },
    )

    LaunchedEffect(Unit) {
        val done = app.container.preferences.isBatteryPromptDone()
        if (!done && !BatteryOptimization.isIgnoring(context)) {
            showBatteryDialog = true
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (BatteryOptimization.isIgnoring(context)) {
            showBatteryDialog = false
            scope.launch { app.container.preferences.setBatteryPromptDone() }
        }
    }

    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = {
                showBatteryDialog = false
                scope.launch { app.container.preferences.setBatteryPromptDone() }
            },
            title = { Text(stringResource(R.string.battery_title)) },
            text = { Text(stringResource(R.string.battery_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        BatteryOptimization.requestIgnore(context)
                        scope.launch { app.container.preferences.setBatteryPromptDone() }
                        showBatteryDialog = false
                    },
                ) {
                    Text(stringResource(R.string.battery_allow))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showBatteryDialog = false
                        scope.launch { app.container.preferences.setBatteryPromptDone() }
                    },
                ) {
                    Text(stringResource(R.string.battery_later))
                }
            },
        )
    }

    Scaffold(
        containerColor = colors.bg,
        bottomBar = {
            PixelNavBar(
                items = tabs,
                selectedRoute = selectedForNav,
                onSelect = { route ->
                    // Search sits on top of NOW; re-tapping NOW must pop Search
                    // (launchSingleTop to NowPlaying alone does nothing while Search is current).
                    if (current == Routes.Search && route == Routes.NowPlaying) {
                        navController.popBackStack()
                    } else {
                        if (current == Routes.Search) {
                            navController.popBackStack()
                        }
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                modifier = Modifier.navigationBarsPadding(),
            )
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier
                .padding(padding)
                .statusBarsPadding()
                .background(colors.bg),
        ) {
            composable(Routes.NowPlaying) {
                NowPlayingScreen(
                    onOpenSearch = {
                        navController.navigate(Routes.Search) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.Favorites) {
                FavoritesScreen(
                    onBack = {
                        navController.navigate(Routes.NowPlaying) {
                            popUpTo(Routes.NowPlaying) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.Search) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.Radio) {
                RadioScreen(
                    onBack = {
                        navController.navigate(Routes.NowPlaying) {
                            popUpTo(Routes.NowPlaying) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.Settings) {
                SettingsScreen(
                    onLoggedOut = onLoggedOut,
                    onBack = {
                        navController.navigate(Routes.NowPlaying) {
                            popUpTo(Routes.NowPlaying) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}
