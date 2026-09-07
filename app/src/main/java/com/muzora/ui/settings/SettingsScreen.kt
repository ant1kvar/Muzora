package com.muzora.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.muzora.MuzoraApp
import com.muzora.R
import com.muzora.player.PlaybackController
import com.muzora.ui.components.MuzoraHeader
import com.muzora.ui.theme.JetBrainsMono
import com.muzora.ui.theme.LocalMuzoraColors
import com.muzora.ui.theme.PressStart2P
import com.muzora.util.BatteryOptimization
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuzoraApp
    val colors = LocalMuzoraColors.current
    val settings by app.container.preferences.settingsFlow.collectAsState(
        initial = com.muzora.data.local.prefs.AppSettings(),
    )
    val session by app.container.preferences.sessionFlow.collectAsState(initial = null)
    val playerState by app.container.queueManager.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showBitrate by remember { mutableStateOf(false) }
    var showPrefetch by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var batteryOk by remember { mutableStateOf(BatteryOptimization.isIgnoring(context)) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        batteryOk = BatteryOptimization.isIgnoring(context)
    }

    val itemColors = ListItemDefaults.colors(
        containerColor = colors.bg,
        headlineColor = colors.white,
        supportingColor = colors.muted,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        MuzoraHeader(
            title = stringResource(R.string.nav_settings),
            online = !playerState.isOffline,
            showBack = true,
            onBack = onBack,
            onTitleClick = onBack,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle(stringResource(R.string.settings_account))
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.server_url)) },
                supportingContent = { Text(session?.serverUrl.orEmpty()) },
            )
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.username)) },
                supportingContent = { Text(session?.username.orEmpty()) },
            )
            ListItem(
                colors = itemColors,
                headlineContent = {
                    Text(
                        stringResource(R.string.log_out),
                        color = colors.offline,
                    )
                },
                modifier = Modifier.clickable {
                    scope.launch {
                        PlaybackController.stopAndClear(context)
                        app.container.preferences.clearSession()
                        app.container.subsonicClient.clear()
                        onLoggedOut()
                    }
                },
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.4f))
            SectionTitle(stringResource(R.string.settings_playback))
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.battery_settings)) },
                supportingContent = {
                    Text(
                        stringResource(
                            if (batteryOk) R.string.battery_settings_on
                            else R.string.battery_settings_off,
                        ),
                    )
                },
                modifier = Modifier.clickable {
                    if (!batteryOk) {
                        BatteryOptimization.requestIgnore(context)
                    } else {
                        BatteryOptimization.openSettings(context)
                    }
                },
            )
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.cache_size)) },
                supportingContent = { Text(stringResource(R.string.cache_size_value)) },
            )
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.prefetch_count)) },
                supportingContent = { Text(settings.prefetchCount.toString()) },
                modifier = Modifier.clickable { showPrefetch = true },
            )
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.wifi_only)) },
                trailingContent = {
                    Switch(
                        checked = settings.wifiOnly,
                        onCheckedChange = {
                            scope.launch {
                                app.container.preferences.updateSettings { s -> s.copy(wifiOnly = it) }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accent,
                            checkedTrackColor = colors.accentDim,
                        ),
                    )
                },
            )
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.play_cache_only)) },
                trailingContent = {
                    Switch(
                        checked = settings.playCacheOnly,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                app.container.preferences.updateSettings { s ->
                                    s.copy(playCacheOnly = enabled)
                                }
                                if (enabled) {
                                    app.container.networkGate.markUnusable()
                                    app.container.queueManager.setOffline(true)
                                } else {
                                    val ok = runCatching {
                                        app.container.networkGate.probeServer(requireStable = false)
                                    }.getOrDefault(false)
                                    app.container.queueManager.setOffline(!ok)
                                    if (ok) {
                                        runCatching {
                                            app.container.queueManager.onServerBecameReachable()
                                        }
                                    }
                                }
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.accent,
                            checkedTrackColor = colors.accentDim,
                        ),
                    )
                },
            )
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.opus_bitrate)) },
                supportingContent = { Text("${settings.opusBitrate} kbps") },
                modifier = Modifier.clickable { showBitrate = true },
            )
            HorizontalDivider(color = colors.border.copy(alpha = 0.4f))
            SectionTitle(stringResource(R.string.settings_appearance))
            ListItem(
                colors = itemColors,
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = {
                    Text(
                        when (settings.language) {
                            "ru" -> stringResource(R.string.lang_russian)
                            "en" -> stringResource(R.string.lang_english)
                            else -> stringResource(R.string.lang_system)
                        },
                    )
                },
                modifier = Modifier.clickable { showLanguage = true },
            )
        }
    }

    if (showBitrate) {
        ChoiceDialog(
            title = stringResource(R.string.opus_bitrate),
            options = listOf(96, 128, 160, 192).map { "$it" to it },
            onDismiss = { showBitrate = false },
            onSelect = { value ->
                scope.launch {
                    app.container.preferences.updateSettings { it.copy(opusBitrate = value) }
                }
                showBitrate = false
            },
        )
    }
    if (showPrefetch) {
        ChoiceDialog(
            title = stringResource(R.string.prefetch_count),
            options = listOf(1, 3, 5, 8, 10).map { "$it" to it },
            onDismiss = { showPrefetch = false },
            onSelect = { value ->
                scope.launch {
                    app.container.preferences.updateSettings { it.copy(prefetchCount = value) }
                }
                showPrefetch = false
            },
        )
    }
    if (showLanguage) {
        ChoiceDialog(
            title = stringResource(R.string.language),
            options = listOf(
                stringResource(R.string.lang_english) to "en",
                stringResource(R.string.lang_russian) to "ru",
                stringResource(R.string.lang_system) to "system",
            ),
            onDismiss = { showLanguage = false },
            onSelect = { value ->
                scope.launch {
                    app.container.preferences.updateSettings { it.copy(language = value) }
                    MuzoraApp.applyAppLanguage(app, value)
                }
                showLanguage = false
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = LocalMuzoraColors.current
    Text(
        text = text.uppercase(),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = TextStyle(
            fontFamily = PressStart2P,
            fontSize = 9.sp,
            color = colors.accent,
        ),
    )
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<String, T>>,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    val colors = LocalMuzoraColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        title = {
            Text(
                title,
                style = TextStyle(
                    fontFamily = PressStart2P,
                    fontSize = 10.sp,
                    color = colors.accent,
                ),
            )
        },
        text = {
            Column {
                options.forEach { (label, value) ->
                    TextButton(onClick = { onSelect(value) }) {
                        Text(
                            label,
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontSize = 13.sp,
                                color = colors.white,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = colors.muted)
            }
        },
    )
}
