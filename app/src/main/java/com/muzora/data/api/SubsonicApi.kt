package com.muzora.data.api

import com.muzora.data.api.dto.SubsonicResponseWrapper
import retrofit2.http.GET
import retrofit2.http.Query

interface SubsonicApi {
    @GET("rest/ping.view")
    suspend fun ping(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/getRandomSongs.view")
    suspend fun getRandomSongs(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("size") size: Int = 50,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/getStarred2.view")
    suspend fun getStarred2(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/star.view")
    suspend fun star(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("id") id: String,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/unstar.view")
    suspend fun unstar(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("id") id: String,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/search3.view")
    suspend fun search3(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("query") query: String,
        @Query("songCount") songCount: Int = 40,
        @Query("albumCount") albumCount: Int = 20,
        @Query("artistCount") artistCount: Int = 20,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/getInternetRadioStations.view")
    suspend fun getInternetRadioStations(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/getAlbum.view")
    suspend fun getAlbum(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("id") id: String,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/getArtist.view")
    suspend fun getArtist(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("id") id: String,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper

    @GET("rest/scrobble.view")
    suspend fun scrobble(
        @Query("u") username: String,
        @Query("t") token: String,
        @Query("s") salt: String,
        @Query("id") id: String,
        @Query("submission") submission: Boolean = true,
        @Query("v") version: String = StreamUrlBuilder.API_VERSION,
        @Query("c") client: String = StreamUrlBuilder.CLIENT_NAME,
        @Query("f") format: String = "json",
    ): SubsonicResponseWrapper
}
