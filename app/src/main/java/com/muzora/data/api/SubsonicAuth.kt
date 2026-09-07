package com.muzora.data.api

import java.security.MessageDigest
import java.util.UUID

object SubsonicAuth {
    data class AuthParams(
        val username: String,
        val token: String,
        val salt: String,
    )

    fun create(username: String, password: String): AuthParams {
        val salt = UUID.randomUUID().toString().replace("-", "").take(12)
        val token = md5(password + salt)
        return AuthParams(username = username, token = token, salt = salt)
    }

    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
