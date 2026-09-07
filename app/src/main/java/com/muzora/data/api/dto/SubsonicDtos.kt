package com.muzora.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubsonicResponseWrapper(
    @SerialName("subsonic-response")
    val response: SubsonicResponse,
)

@Serializable
data class SubsonicResponse(
    val status: String,
    val version: String? = null,
    val type: String? = null,
    val error: SubsonicError? = null,
    val randomSongs: SongsContainer? = null,
    val starred2: Starred2? = null,
    val searchResult3: SearchResult3? = null,
    val internetRadioStations: InternetRadioStations? = null,
    val album: AlbumDetail? = null,
    val artist: ArtistDetail? = null,
)

@Serializable
data class SubsonicError(
    val code: Int,
    val message: String? = null,
)

@Serializable
data class SongsContainer(
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class Starred2(
    val song: List<SongDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val artist: List<ArtistDto> = emptyList(),
)

@Serializable
data class SearchResult3(
    val song: List<SongDto> = emptyList(),
    val album: List<AlbumDto> = emptyList(),
    val artist: List<ArtistDto> = emptyList(),
)

@Serializable
data class InternetRadioStations(
    val internetRadioStation: List<InternetRadioStationDto> = emptyList(),
)

@Serializable
data class SongDto(
    val id: String,
    val title: String? = null,
    val album: String? = null,
    val artist: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val year: Int? = null,
    val starred: String? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
)

@Serializable
data class AlbumDto(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val year: Int? = null,
)

@Serializable
data class ArtistDto(
    val id: String,
    val name: String? = null,
    val albumCount: Int? = null,
    val coverArt: String? = null,
)

@Serializable
data class InternetRadioStationDto(
    val id: String,
    val name: String? = null,
    val streamUrl: String? = null,
    val homepageUrl: String? = null,
)

@Serializable
data class AlbumDetail(
    val id: String,
    val name: String? = null,
    val artist: String? = null,
    val coverArt: String? = null,
    val song: List<SongDto> = emptyList(),
)

@Serializable
data class ArtistDetail(
    val id: String,
    val name: String? = null,
    val album: List<AlbumDto> = emptyList(),
)
