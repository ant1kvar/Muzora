package com.muzora.ui.nowplaying

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.muzora.MuzoraApp
import com.muzora.R
import com.muzora.domain.model.CacheStatus
import com.muzora.domain.model.PlaybackMode
import com.muzora.player.PlaybackController
import com.muzora.ui.components.AnimatedDislikeButton
import com.muzora.ui.components.CoverDebris
import com.muzora.ui.components.MuzoraHeader
import com.muzora.ui.components.MuzoraPauseIcon
import com.muzora.ui.components.MuzoraPlayIcon
import com.muzora.ui.components.MuzoraPrevIcon
import com.muzora.ui.components.PixelSearchBar
import com.muzora.ui.components.RoundTransportButton
import com.muzora.ui.components.SparkLikeButton
import com.muzora.ui.components.SparkShuffleButton
import com.muzora.ui.components.SpectrumSeekBar
import com.muzora.ui.theme.DesignTokens
import com.muzora.ui.theme.JetBrainsMono
import com.muzora.ui.theme.LocalMuzoraColors
import com.muzora.ui.theme.PixelifySans
import com.muzora.ui.theme.PressStart2P
import com.muzora.ui.util.formatTime
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(
    onOpenSearch: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuzoraApp
    val state by app.container.queueManager.state.collectAsState()
    val cacheStatuses by app.container.cacheManager.statuses.collectAsState()
    val colors = LocalMuzoraColors.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var seeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }

    val currentCached = state.mode != PlaybackMode.Radio &&
        state.current?.id?.let { id ->
            state.queue.getOrNull(state.index)?.cacheStatus == CacheStatus.Cached ||
                cacheStatuses[id] == CacheStatus.Cached
        } == true

    val coverUrl = remember(state.current?.coverArt, state.mode) {
        if (state.mode == PlaybackMode.Radio) null
        else state.current?.coverArt?.let {
            if (app.container.subsonicClient.isConfigured) {
                app.container.subsonicClient.streamUrlBuilder().coverArtUrl(it)
            } else null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            MuzoraHeader(
                title = stringResource(R.string.app_name),
                online = !state.isOffline,
            )

            if (state.current == null) {
                EmptyTracks(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    onShuffle = { PlaybackController.shuffle(context) },
                )
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    PixelSearchBar(
                        onClick = onOpenSearch,
                        hint = stringResource(R.string.search_library_hint),
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Figma / demo: 328×220 stage, art 180 at (74,20)
                        Box(
                            modifier = Modifier
                                .width(328.dp)
                                .height(220.dp),
                        ) {
                            CoverDebris(modifier = Modifier.fillMaxSize())
                            Box(
                                modifier = Modifier
                                    .padding(start = 74.dp, top = 20.dp)
                                    .size(DesignTokens.CoverSize)
                                    .border(2.dp, colors.border)
                                    .background(colors.field),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (coverUrl != null) {
                                    AsyncImage(
                                        model = coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Album,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = colors.muted,
                                    )
                                }
                            }
                        }
                    }

                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = state.current?.title.orEmpty(),
                            style = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 20.sp,
                                color = colors.white,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.by_artist,
                                    state.current?.artist.orEmpty(),
                                ),
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontSize = 13.sp,
                                    color = colors.muted,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (currentCached) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "[#]",
                                    style = TextStyle(
                                        fontFamily = JetBrainsMono,
                                        fontSize = 13.sp,
                                        color = DesignTokens.CachedIconGreen,
                                    ),
                                )
                            }
                        }
                    }

                    if (state.mode != PlaybackMode.Radio) {
                        val metaDuration = (state.current?.duration ?: 0).toLong() * 1000L
                        val duration = state.durationMs.takeIf { it > 0 } ?: metaDuration
                        val position = if (seeking) {
                            (seekFraction * duration).toLong()
                        } else {
                            state.positionMs
                        }
                        val fraction = if (duration > 0) {
                            (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                formatTime(position),
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp,
                                    color = colors.accent,
                                ),
                            )
                            Text(
                                formatTime(duration),
                                style = TextStyle(
                                    fontFamily = JetBrainsMono,
                                    fontSize = 11.sp,
                                    color = colors.muted,
                                ),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        SpectrumSeekBar(
                            progress = fraction,
                            playing = state.isPlaying,
                            scrubbing = seeking,
                            onScrub = { f ->
                                if (duration <= 0) return@SpectrumSeekBar
                                seeking = true
                                seekFraction = f
                            },
                            onSeekFinished = { f ->
                                if (duration <= 0) {
                                    seeking = false
                                    return@SpectrumSeekBar
                                }
                                seekFraction = f
                                PlaybackController.seekTo((f * duration).toLong())
                                seeking = false
                            },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }

                    // Figma block between spectrum and bottom nav: like 68 + transport 80, evenly spaced
                    val removedMsg = stringResource(R.string.removed_from_random)
                    val undoMsg = stringResource(R.string.undo)
                    val liked = state.current?.id?.let { state.likedIds.contains(it) } == true

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (state.mode == PlaybackMode.Radio) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .background(colors.field)
                                    .border(2.dp, colors.accent)
                                    .clickable {
                                        PlaybackController.leaveRadioToMusic(context)
                                    }
                                    .padding(vertical = 14.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.radio_exit_to_music),
                                    style = TextStyle(
                                        fontFamily = PressStart2P,
                                        fontSize = 12.sp,
                                        color = colors.accent,
                                    ),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = stringResource(R.string.radio_exit_hint),
                                    style = TextStyle(
                                        fontFamily = JetBrainsMono,
                                        fontSize = 10.sp,
                                        color = colors.muted,
                                    ),
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AnimatedDislikeButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        val song = state.current ?: return@AnimatedDislikeButton
                                        PlaybackController.dislike(context, song) { id ->
                                            scope.launch {
                                                val result = snackbar.showSnackbar(
                                                    message = removedMsg,
                                                    actionLabel = undoMsg,
                                                    duration = SnackbarDuration.Short,
                                                )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    PlaybackController.undoDislike(id)
                                                }
                                            }
                                        }
                                    },
                                )
                                SparkLikeButton(
                                    modifier = Modifier.weight(1f),
                                    liked = liked,
                                    onToggle = {
                                        state.current?.let { PlaybackController.toggleLike(it) }
                                    },
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(horizontal = 40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (state.mode != PlaybackMode.Radio) {
                                    RoundTransportButton(
                                        onClick = { PlaybackController.skipPrevious(context) },
                                        size = DesignTokens.TransportSide,
                                    ) {
                                        MuzoraPrevIcon(
                                            size = DesignTokens.TransportIcon,
                                            tint = colors.accent,
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                RoundTransportButton(
                                    onClick = { PlaybackController.playPause(context) },
                                    size = DesignTokens.TransportPlay,
                                ) {
                                    if (state.isPlaying) {
                                        MuzoraPauseIcon(
                                            size = DesignTokens.TransportIcon,
                                            tint = colors.accent,
                                        )
                                    } else {
                                        MuzoraPlayIcon(
                                            size = DesignTokens.TransportIcon,
                                            tint = colors.accent,
                                        )
                                    }
                                }
                            }
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                if (state.mode == PlaybackMode.Radio) {
                                    SparkShuffleButton(
                                        onClick = {
                                            PlaybackController.leaveRadioToMusic(context)
                                        },
                                        size = DesignTokens.TransportSide,
                                        showBorder = true,
                                    )
                                } else {
                                    SparkShuffleButton(
                                        onClick = { PlaybackController.skipNext(context) },
                                        size = DesignTokens.TransportSide,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )
    }
}

@Composable
private fun EmptyTracks(modifier: Modifier, onShuffle: () -> Unit) {
    val colors = LocalMuzoraColors.current
    Column(
        modifier = modifier.padding(24.dp),
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
        SparkShuffleButton(
            onClick = onShuffle,
            size = 96.dp,
            showBorder = true,
        )
        Spacer(Modifier.height(16.dp))
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
