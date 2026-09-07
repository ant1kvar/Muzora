package com.muzora.data.api

import okhttp3.HttpUrl.Companion.toHttpUrl

class StreamUrlBuilder(
    private val baseUrl: String,
    private val auth: SubsonicAuth.AuthParams,
) {
    fun streamUrl(songId: String, maxBitRate: Int): String {
        return normalizedBase().newBuilder()
            .addPathSegment("rest")
            .addPathSegment("stream.view")
            .addQueryParameter("id", songId)
            .addQueryParameter("format", "opus")
            .addQueryParameter("maxBitRate", maxBitRate.toString())
            // Lets ExoPlayer know length of transcoded streams so seeking works
            .addQueryParameter("estimateContentLength", "true")
            .addAuth()
            .build()
            .toString()
    }

    fun coverArtUrl(coverArtId: String?, size: Int = 600): String? {
        if (coverArtId.isNullOrBlank()) return null
        return normalizedBase().newBuilder()
            .addPathSegment("rest")
            .addPathSegment("getCoverArt.view")
            .addQueryParameter("id", coverArtId)
            .addQueryParameter("size", size.toString())
            .addAuth()
            .build()
            .toString()
    }

    private fun okhttp3.HttpUrl.Builder.addAuth(): okhttp3.HttpUrl.Builder =
        addQueryParameter("u", auth.username)
            .addQueryParameter("t", auth.token)
            .addQueryParameter("s", auth.salt)
            .addQueryParameter("v", API_VERSION)
            .addQueryParameter("c", CLIENT_NAME)

    private fun normalizedBase() = baseUrl.trimEnd('/').toHttpUrl()

    companion object {
        const val API_VERSION = "1.16.1"
        const val CLIENT_NAME = "Muzora"
    }
}
