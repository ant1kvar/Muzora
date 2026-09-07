package com.muzora.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.muzora.data.api.dto.AlbumDto
import com.muzora.data.api.dto.ArtistDto
import com.muzora.data.api.dto.SongDto
import com.muzora.data.api.dto.SubsonicResponse
import com.muzora.domain.model.Album
import com.muzora.domain.model.Artist
import com.muzora.domain.model.RadioStation
import com.muzora.domain.model.SearchResults
import com.muzora.domain.model.Song
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class SubsonicClient(
    private val okHttpClient: OkHttpClient = defaultClient(),
) {
    private var api: SubsonicApi? = null
    private var baseUrl: String = ""
    private var username: String = ""
    private var password: String = ""
    /** Stable for the session so cover/stream URLs don't change every Compose frame. */
    private var sessionAuth: SubsonicAuth.AuthParams? = null

    val isConfigured: Boolean
        get() = api != null && baseUrl.isNotBlank() && username.isNotBlank() && sessionAuth != null

    fun configure(serverUrl: String, username: String, password: String) {
        this.baseUrl = normalizeBaseUrl(serverUrl)
        this.username = username
        this.password = password
        this.sessionAuth = SubsonicAuth.create(username, password)
        val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        api = Retrofit.Builder()
            .baseUrl(if (this.baseUrl.endsWith("/")) this.baseUrl else "${this.baseUrl}/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SubsonicApi::class.java)
    }

    fun clear() {
        api = null
        baseUrl = ""
        username = ""
        password = ""
        sessionAuth = null
    }

    fun streamUrlBuilder(): StreamUrlBuilder {
        checkConfigured()
        return StreamUrlBuilder(baseUrl, requireNotNull(sessionAuth))
    }

    suspend fun ping(): Boolean {
        val response = withAuth { u, t, s -> requireApi().ping(u, t, s) }
        return response.response.isOk()
    }

    suspend fun getRandomSongs(size: Int = 50): List<Song> {
        val response = withAuth { u, t, s -> requireApi().getRandomSongs(u, t, s, size) }.response.requireOk()
        return response.randomSongs?.song.orEmpty().map { it.toSong() }
    }

    suspend fun getStarred(): List<Song> {
        val response = withAuth { u, t, s -> requireApi().getStarred2(u, t, s) }.response.requireOk()
        return response.starred2?.song.orEmpty().map { it.toSong(starred = true) }
    }

    suspend fun star(id: String) {
        withAuth { u, t, s -> requireApi().star(u, t, s, id) }.response.requireOk()
    }

    suspend fun unstar(id: String) {
        withAuth { u, t, s -> requireApi().unstar(u, t, s, id) }.response.requireOk()
    }

    suspend fun search(query: String): SearchResults {
        val response = withAuth { u, t, s -> requireApi().search3(u, t, s, query) }.response.requireOk()
        val result = response.searchResult3
        return SearchResults(
            songs = result?.song.orEmpty().map { it.toSong() },
            albums = result?.album.orEmpty().map { it.toAlbum() },
            artists = result?.artist.orEmpty().map { it.toArtist() },
        )
    }

    suspend fun getRadioStations(): List<RadioStation> {
        val response = withAuth { u, t, s -> requireApi().getInternetRadioStations(u, t, s) }.response.requireOk()
        return response.internetRadioStations?.internetRadioStation.orEmpty().mapNotNull { dto ->
            val url = dto.streamUrl ?: return@mapNotNull null
            RadioStation(
                id = dto.id,
                name = dto.name ?: url,
                streamUrl = url,
                isCustom = false,
            )
        }
    }

    suspend fun getAlbumSongs(albumId: String): List<Song> {
        val response = withAuth { u, t, s -> requireApi().getAlbum(u, t, s, albumId) }.response.requireOk()
        return response.album?.song.orEmpty().map { it.toSong() }
    }

    suspend fun getArtistAlbums(artistId: String): List<Album> {
        val response = withAuth { u, t, s -> requireApi().getArtist(u, t, s, artistId) }.response.requireOk()
        return response.artist?.album.orEmpty().map { it.toAlbum() }
    }

    suspend fun scrobble(id: String, submission: Boolean = true) {
        runCatching {
            withAuth { u, t, s -> requireApi().scrobble(u, t, s, id, submission) }
        }
    }

    private suspend fun <T> withAuth(block: suspend (String, String, String) -> T): T {
        checkConfigured()
        val auth = SubsonicAuth.create(username, password)
        return block(auth.username, auth.token, auth.salt)
    }

    private fun requireApi(): SubsonicApi = api ?: error("Subsonic client not configured")

    private fun checkConfigured() {
        check(isConfigured) { "Subsonic client not configured" }
    }

    companion object {
        fun defaultClient(): OkHttpClient {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(logging)
                .build()
        }

        fun normalizeBaseUrl(url: String): String {
            var value = url.trim()
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "https://$value"
            }
            return value.trimEnd('/')
        }
    }
}

fun SubsonicResponse.isOk(): Boolean = status.equals("ok", ignoreCase = true)

fun SubsonicResponse.requireOk(): SubsonicResponse {
    if (!isOk()) {
        val message = error?.message ?: "Subsonic error ${error?.code ?: -1}"
        throw SubsonicException(error?.code ?: -1, message)
    }
    return this
}

class SubsonicException(val code: Int, message: String) : Exception(message)

fun SongDto.toSong(starred: Boolean = this.starred != null): Song = Song(
    id = id,
    title = title ?: "Unknown",
    artist = artist ?: "Unknown",
    album = album ?: "",
    albumId = albumId,
    coverArt = coverArt,
    duration = duration ?: 0,
    track = track,
    year = year,
    starred = starred,
)

fun AlbumDto.toAlbum(): Album = Album(
    id = id,
    name = name ?: "Unknown",
    artist = artist ?: "Unknown",
    coverArt = coverArt,
)

fun ArtistDto.toArtist(): Artist = Artist(
    id = id,
    name = name ?: "Unknown",
    albumCount = albumCount ?: 0,
)
