package com.muzora.domain.model

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: String? = null,
    val coverArt: String? = null,
    val duration: Int = 0,
    val track: Int? = null,
    val year: Int? = null,
    val starred: Boolean = false,
)

data class Album(
    val id: String,
    val name: String,
    val artist: String,
    val coverArt: String? = null,
)

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int = 0,
)

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val isCustom: Boolean = false,
)

enum class RepeatMode {
    Off, One, All;

    fun next(): RepeatMode = when (this) {
        Off -> One
        One -> All
        All -> Off
    }
}

enum class CacheStatus {
    Cached, Downloading, NotCached
}

enum class PlaybackMode {
    LibraryRandom, Favorites, Radio
}

data class QueueItem(
    val song: Song,
    val cacheStatus: CacheStatus = CacheStatus.NotCached,
)

data class SearchResults(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
)
