package com.muzora.ui.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import coil.request.ImageRequest
import com.muzora.MuzoraApp
import com.muzora.R
import com.muzora.domain.model.Song
import com.muzora.player.PlaybackController
import com.muzora.ui.components.MuzoraHeader
import com.muzora.ui.theme.JetBrainsMono
import com.muzora.ui.theme.LocalMuzoraColors
import com.muzora.ui.theme.PixelifySans
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun FavoritesScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuzoraApp
    val songs by app.container.starRepository.observeLocalStars().collectAsState(initial = emptyList())
    // Don't collect full player state — progress ticks were rebuilding every cover URL.
    val isOffline by remember {
        app.container.queueManager.state.map { it.isOffline }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val colors = LocalMuzoraColors.current
    val coverBuilder = remember(app.container.subsonicClient.isConfigured) {
        if (app.container.subsonicClient.isConfigured) {
            app.container.subsonicClient.streamUrlBuilder()
        } else {
            null
        }
    }

    LaunchedEffect(Unit) {
        PlaybackController.refreshStarred()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        MuzoraHeader(
            title = stringResource(R.string.nav_favorites),
            online = !isOffline,
            showBack = true,
            onBack = onBack,
            onTitleClick = onBack,
        )
        if (songs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    Icons.Outlined.FavoriteBorder,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = colors.muted,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.favorites_empty),
                    style = TextStyle(
                        fontFamily = PixelifySans,
                        fontSize = 18.sp,
                        color = colors.white,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.favorites_empty_body),
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontSize = 12.sp,
                        color = colors.muted,
                    ),
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        coverUrl = coverBuilder?.coverArtUrl(song.coverArt, 120),
                        onClick = { PlaybackController.playFavorites(context, song.id) },
                    )
                }
            }
        }
    }
}

@Composable
fun SongRow(
    song: Song,
    coverUrl: String?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalMuzoraColors.current
    val cacheKey = "cover:${song.coverArt ?: song.id}:120"
    val imageRequest = remember(cacheKey, coverUrl) {
        ImageRequest.Builder(context)
            .data(coverUrl)
            .crossfade(false)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, colors.border)
                .background(colors.field),
            contentScale = ContentScale.Crop,
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f),
        ) {
            Text(
                song.title,
                style = TextStyle(
                    fontFamily = PixelifySans,
                    fontSize = 15.sp,
                    color = colors.white,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                style = TextStyle(
                    fontFamily = JetBrainsMono,
                    fontSize = 12.sp,
                    color = colors.muted,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
