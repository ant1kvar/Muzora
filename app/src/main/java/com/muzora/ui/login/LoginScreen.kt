package com.muzora.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzora.MuzoraApp
import com.muzora.R
import com.muzora.data.api.SubsonicClient
import com.muzora.data.local.prefs.Session
import com.muzora.player.PlaybackController
import com.muzora.ui.components.MuzoraStatusChip
import com.muzora.ui.components.PixelButton
import com.muzora.ui.components.PixelField
import com.muzora.ui.components.SparkShuffleButton
import com.muzora.ui.theme.JetBrainsMono
import com.muzora.ui.theme.LocalMuzoraColors
import com.muzora.ui.theme.PressStart2P
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class StartExit {
    Shuffle,
    Radio,
}

@Composable
fun LoginScreen(
    initialConnected: Boolean = false,
    onEnterApp: (StartExit) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuzoraApp
    val scope = rememberCoroutineScope()
    val colors = LocalMuzoraColors.current
    val settings by app.container.preferences.settingsFlow.collectAsState(
        initial = com.muzora.data.local.prefs.AppSettings(),
    )
    val language = when (settings.language) {
        "ru" -> "ru"
        else -> "en"
    }

    var server by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var connected by remember { mutableStateOf(initialConnected) }

    LaunchedEffect(initialConnected) {
        if (initialConnected) connected = true
        val session = app.container.preferences.getSession()
        if (session != null) {
            server = session.serverUrl
            username = session.username
        }
    }

    fun setLanguage(code: String) {
        if (code == language) return
        scope.launch {
            app.container.preferences.updateSettings { it.copy(language = code) }
            MuzoraApp.applyAppLanguage(app, code)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MuzoraStatusChip(online = connected)
            Spacer(Modifier.weight(1f))
            LoginLanguageToggle(
                selected = language,
                onSelect = ::setLanguage,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = TextStyle(
                    fontFamily = PressStart2P,
                    fontSize = 18.sp,
                    color = colors.accent,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.login_subtitle),
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 11.sp,
                    color = colors.muted,
                ),
            )
        }

        if (!connected) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PixelField(
                    label = stringResource(R.string.server_url),
                    value = server,
                    onValueChange = { server = it },
                    placeholder = stringResource(R.string.server_url_placeholder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                PixelField(
                    label = stringResource(R.string.username),
                    value = username,
                    onValueChange = { username = it },
                )
                PixelField(
                    label = stringResource(R.string.password),
                    value = password,
                    onValueChange = { password = it },
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailing = {
                        Text(
                            text = stringResource(
                                if (showPassword) R.string.password_hide else R.string.password_show,
                            ),
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontSize = 10.sp,
                                color = colors.accent,
                            ),
                            modifier = Modifier
                                .background(colors.field)
                                .border(1.dp, colors.border)
                                .clickable { showPassword = !showPassword }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                        )
                    },
                )

                if (error != null) {
                    Text(
                        text = stringResource(R.string.login_error),
                        style = TextStyle(
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            color = colors.offline,
                        ),
                    )
                }

                PixelButton(
                    text = if (loading) "..." else stringResource(R.string.connect_cta),
                    enabled = !loading && server.isNotBlank() &&
                        username.isNotBlank() && password.isNotBlank(),
                    onClick = {
                        loading = true
                        error = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching {
                                    val normalized = SubsonicClient.normalizeBaseUrl(server)
                                    app.container.subsonicClient.configure(
                                        normalized,
                                        username.trim(),
                                        password,
                                    )
                                    val ping = app.container.networkGate.probeServer(
                                        requireStable = false,
                                    )
                                    if (ping) {
                                        app.container.preferences.saveSession(
                                            Session(normalized, username.trim(), password),
                                        )
                                        app.container.queueManager.setOffline(false)
                                        app.container.queueManager.refreshStarred()
                                    }
                                    ping
                                }.getOrDefault(false)
                            }
                            loading = false
                            if (ok) connected = true else error = "fail"
                        }
                    },
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.server_connected),
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = colors.muted,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    SparkShuffleButton(
                        onClick = {
                            PlaybackController.shuffle(context)
                            onEnterApp(StartExit.Shuffle)
                        },
                        size = 96.dp,
                        showBorder = true,
                    )
                }
                Text(
                    text = stringResource(R.string.shuffle_cta),
                    style = TextStyle(
                        fontFamily = PressStart2P,
                        fontSize = 10.sp,
                        color = colors.accent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.shuffle_hint),
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        color = colors.muted,
                    ),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(colors.field)
                .border(width = 2.dp, color = colors.accent)
                .clickable { onEnterApp(StartExit.Radio) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.radio_shortcut),
                style = TextStyle(
                    fontFamily = PressStart2P,
                    fontSize = 14.sp,
                    color = colors.accent,
                ),
            )
        }
    }
}

@Composable
private fun LoginLanguageToggle(
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = LocalMuzoraColors.current
    Row(
        modifier = Modifier
            .border(1.dp, colors.border)
            .background(colors.field),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageChip(
            label = "EN",
            selected = selected == "en",
            onClick = { onSelect("en") },
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(colors.border),
        )
        LanguageChip(
            label = "RU",
            selected = selected == "ru",
            onClick = { onSelect("ru") },
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalMuzoraColors.current
    Text(
        text = label,
        style = TextStyle(
            fontFamily = PressStart2P,
            fontSize = 9.sp,
            color = if (selected) colors.bg else colors.accent,
        ),
        modifier = Modifier
            .background(if (selected) colors.accent else colors.field)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}
