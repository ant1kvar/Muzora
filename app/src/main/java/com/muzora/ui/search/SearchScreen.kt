package com.muzora.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muzora.MuzoraApp
import com.muzora.R
import com.muzora.domain.model.Album
import com.muzora.domain.model.Artist
import com.muzora.domain.model.SearchResults
import com.muzora.domain.model.Song
import com.muzora.player.PlaybackController
import com.muzora.ui.components.MuzoraHeader
import com.muzora.ui.components.PixelField
import com.muzora.ui.favorites.SongRow
import com.muzora.ui.theme.JetBrainsMono
import com.muzora.ui.theme.LocalMuzoraColors
import com.muzora.ui.theme.PixelifySans
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuzoraApp
    val scope = rememberCoroutineScope()
    val playerState by app.container.queueManager.state.collectAsState()
    val colors = LocalMuzoraColors.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(SearchResults()) }
    var detailSongs by remember { mutableStateOf<List<Song>?>(null) }
    var detailTitle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        // Wait a frame so the field is in the composition tree, then open IME.
        delay(64)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    LaunchedEffect(query, playerState.isOffline) {
        if (query.isBlank()) {
            results = SearchResults()
            detailSongs = null
            return@LaunchedEffect
        }
        delay(300)
        results = if (playerState.isOffline || !app.container.subsonicClient.isConfigured) {
            SearchResults(songs = app.container.cacheManager.searchOffline(query))
        } else {
            runCatching { app.container.subsonicClient.search(query) }.getOrDefault(SearchResults())
        }
        detailSongs = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        MuzoraHeader(
            title = stringResource(R.string.nav_search),
            online = !playerState.isOffline,
            showBack = true,
            onBack = onBack,
            onTitleClick = onBack,
        )
        Column(modifier = Modifier.fillMaxSize()) {
            PixelField(
                label = ">",
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.search_hint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                focusRequester = focusRequester,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (playerState.isOffline) {
                Text(
                    text = stringResource(R.string.searching_cache),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = TextStyle(
                        fontFamily = JetBrainsMono,
                        fontSize = 11.sp,
                        color = colors.muted,
                    ),
                )
            }

            if (detailSongs != null) {
                Text(
                    text = "< ${detailTitle.orEmpty()}",
                    modifier = Modifier
                        .clickable {
                            detailSongs = null
                            detailTitle = null
                        }
                        .padding(16.dp),
                    style = TextStyle(
                        fontFamily = PixelifySans,
                        fontSize = 14.sp,
                        color = colors.accent,
                    ),
                )
                LazyColumn {
                    items(detailSongs.orEmpty(), key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            coverUrl = cover(app, song.coverArt),
                            onClick = { PlaybackController.playSongThenRandom(context, song) },
                        )
                    }
                }
                return@Column
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (results.songs.isNotEmpty()) {
                    item {
                        SectionHeader(stringResource(R.string.tracks))
                    }
                    items(results.songs, key = { "s-${it.id}" }) { song ->
                        SongRow(
                            song = song,
                            coverUrl = cover(app, song.coverArt),
                            onClick = { PlaybackController.playSongThenRandom(context, song) },
                        )
                    }
                }
                if (results.albums.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.albums)) }
                    items(results.albums, key = { "a-${it.id}" }) { album ->
                        AlbumRow(album) {
                            detailTitle = album.name
                            scope.launch {
                                detailSongs = withContext(Dispatchers.IO) {
                                    runCatching {
                                        app.container.subsonicClient.getAlbumSongs(album.id)
                                    }.getOrDefault(emptyList())
                                }
                            }
                        }
                    }
                }
                if (results.artists.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.artists)) }
                    items(results.artists, key = { "r-${it.id}" }) { artist ->
                        ArtistRow(artist) {
                            detailTitle = artist.name
                            scope.launch {
                                detailSongs = withContext(Dispatchers.IO) {
                                    val albums = runCatching {
                                        app.container.subsonicClient.getArtistAlbums(artist.id)
                                    }.getOrDefault(emptyList())
                                    albums.flatMap { album ->
                                        runCatching {
                                            app.container.subsonicClient.getAlbumSongs(album.id)
                                        }.getOrDefault(emptyList())
                                    }
                                }
                            }
                        }
                    }
                }
                if (query.isNotBlank() &&
                    results.songs.isEmpty() &&
                    results.albums.isEmpty() &&
                    results.artists.isEmpty()
                ) {
                    item {
                        Text(
                            text = stringResource(
                                if (playerState.isOffline) R.string.no_cached_tracks else R.string.nothing_found
                            ),
                            modifier = Modifier.padding(24.dp),
                            style = TextStyle(
                                fontFamily = PixelifySans,
                                fontSize = 16.sp,
                                color = colors.white,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = LocalMuzoraColors.current
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = TextStyle(
            fontFamily = JetBrainsMono,
            fontSize = 11.sp,
            color = colors.accent,
        ),
    )
}

@Composable
private fun AlbumRow(album: Album, onClick: () -> Unit) {
    val colors = LocalMuzoraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            album.name,
            style = TextStyle(
                fontFamily = PixelifySans,
                fontSize = 15.sp,
                color = colors.white,
            ),
        )
        Text(
            album.artist,
            style = TextStyle(
                fontFamily = JetBrainsMono,
                fontSize = 12.sp,
                color = colors.muted,
            ),
        )
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    val colors = LocalMuzoraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            artist.name,
            style = TextStyle(
                fontFamily = PixelifySans,
                fontSize = 15.sp,
                color = colors.white,
            ),
        )
    }
}

private fun cover(app: MuzoraApp, coverArt: String?): String? {
    if (!app.container.subsonicClient.isConfigured) return null
    return app.container.subsonicClient.streamUrlBuilder().coverArtUrl(coverArt, 120)
}
