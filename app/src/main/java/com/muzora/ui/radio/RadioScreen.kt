package com.muzora.ui.radio

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzora.MuzoraApp
import com.muzora.R
import com.muzora.data.local.db.LocalRadioEntity
import com.muzora.domain.model.PlaybackMode
import com.muzora.domain.model.RadioStation
import com.muzora.player.PlaybackController
import com.muzora.ui.components.MuzoraHeader
import com.muzora.ui.components.PixelField
import com.muzora.ui.theme.JetBrainsMono
import com.muzora.ui.theme.LocalMuzoraColors
import com.muzora.ui.theme.PixelifySans
import com.muzora.ui.theme.PressStart2P
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun RadioScreen(
    onBack: () -> Unit = {},
    /** Guest start-flow: bottom ← BACK bar instead of header back / main tabs. */
    showBottomBack: Boolean = false,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuzoraApp
    val scope = rememberCoroutineScope()
    val colors = LocalMuzoraColors.current
    val custom by app.container.database.localRadioDao().observeAll().collectAsState(initial = emptyList())
    val playerState by app.container.queueManager.state.collectAsState()
    var serverStations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        serverStations = runCatching {
            if (app.container.subsonicClient.isConfigured) {
                app.container.subsonicClient.getRadioStations()
            } else emptyList()
        }.getOrDefault(emptyList())
    }

    val stations = serverStations + custom.map {
        RadioStation(it.id, it.name, it.streamUrl, isCustom = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg)
            .then(if (showBottomBack) Modifier.statusBarsPadding() else Modifier)
            .then(if (showBottomBack) Modifier.navigationBarsPadding() else Modifier),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            Column(Modifier.fillMaxSize()) {
                MuzoraHeader(
                    title = stringResource(R.string.nav_radio),
                    online = !playerState.isOffline,
                    showBack = !showBottomBack,
                    onBack = onBack,
                    onTitleClick = if (showBottomBack) null else onBack,
                )
                if (stations.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Rounded.Radio,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = colors.muted,
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.radio_empty),
                            style = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 18.sp,
                                color = colors.white,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.radio_empty_body),
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontSize = 12.sp,
                                color = colors.muted,
                            ),
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(stations, key = { it.id }) { station ->
                            val isActive = playerState.mode == PlaybackMode.Radio &&
                                playerState.radio?.id == station.id
                            val metaTitle = playerState.current?.title
                            val metaArtist = playerState.current?.artist
                            val hasTrackMeta = isActive &&
                                !metaTitle.isNullOrBlank() &&
                                !metaTitle.equals(station.name, ignoreCase = true)
                            val subtitle = when {
                                hasTrackMeta &&
                                    !metaArtist.isNullOrBlank() &&
                                    !metaArtist.equals("Radio", ignoreCase = true) &&
                                    !metaArtist.equals(station.name, ignoreCase = true) ->
                                    stringResource(
                                        R.string.radio_now_playing,
                                        metaArtist,
                                        metaTitle!!,
                                    )
                                hasTrackMeta -> metaTitle!!
                                isActive -> stringResource(R.string.radio_on_air)
                                station.isCustom -> stringResource(R.string.custom)
                                else -> station.streamUrl
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isActive) {
                                            Modifier
                                                .background(colors.accent.copy(alpha = 0.12f))
                                                .border(width = 2.dp, color = colors.accent)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { PlaybackController.playRadio(context, station) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Radio,
                                    null,
                                    tint = if (isActive) colors.accent else colors.muted,
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 12.dp),
                                ) {
                                    Text(
                                        station.name,
                                        style = TextStyle(
                                            fontFamily = PixelifySans,
                                            fontSize = 15.sp,
                                            color = if (isActive) colors.accent else colors.white,
                                        ),
                                    )
                                    Text(
                                        subtitle,
                                        style = TextStyle(
                                            fontFamily = JetBrainsMono,
                                            fontSize = 11.sp,
                                            color = if (isActive) colors.accent else colors.muted,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isActive) {
                                    Text(
                                        text = "►",
                                        style = TextStyle(
                                            fontFamily = PressStart2P,
                                            fontSize = 10.sp,
                                            color = colors.accent,
                                        ),
                                    )
                                }
                                if (station.isCustom) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                app.container.database.localRadioDao().delete(station.id)
                                            }
                                        },
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            stringResource(R.string.delete),
                                            tint = colors.offline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text(
                text = "+",
                style = TextStyle(
                    fontFamily = PressStart2P,
                    fontSize = 18.sp,
                    color = colors.accent,
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 20.dp,
                        bottom = if (playerState.mode == PlaybackMode.Radio) 88.dp else 20.dp,
                    )
                    .background(colors.field)
                    .border(2.dp, colors.accent)
                    .clickable { showDialog = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (playerState.mode == PlaybackMode.Radio) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .background(colors.field)
                        .border(2.dp, colors.accent)
                        .clickable { PlaybackController.leaveRadioToMusic(context) }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.radio_exit_to_music),
                        style = TextStyle(
                            fontFamily = PressStart2P,
                            fontSize = 12.sp,
                            color = colors.accent,
                        ),
                    )
                }
            }
        }
        if (showBottomBack) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(colors.field)
                    .border(width = 2.dp, color = colors.accent)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.radio_back),
                    style = TextStyle(
                        fontFamily = PressStart2P,
                        fontSize = 14.sp,
                        color = colors.accent,
                    ),
                )
            }
        }
    }

    if (showDialog) {
        AddStationDialog(
            onDismiss = { showDialog = false },
            onAdd = { name, url ->
                scope.launch {
                    app.container.database.localRadioDao().upsert(
                        LocalRadioEntity(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            streamUrl = url,
                        ),
                    )
                }
                showDialog = false
            },
        )
    }
}

@Composable
private fun AddStationDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    val colors = LocalMuzoraColors.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bg,
        title = {
            Text(
                stringResource(R.string.add_station),
                style = TextStyle(
                    fontFamily = PressStart2P,
                    fontSize = 11.sp,
                    color = colors.accent,
                ),
            )
        },
        text = {
            Column {
                PixelField(
                    label = stringResource(R.string.station_name),
                    value = name,
                    onValueChange = { name = it },
                )
                Spacer(Modifier.height(12.dp))
                PixelField(
                    label = stringResource(R.string.stream_url),
                    value = url,
                    onValueChange = { url = it },
                    placeholder = stringResource(R.string.stream_url_placeholder),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim(), url.trim()) },
                enabled = name.isNotBlank() &&
                    (url.startsWith("http://") || url.startsWith("https://")),
            ) {
                Text(stringResource(R.string.add), color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = colors.muted)
            }
        },
    )
}
