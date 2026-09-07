package com.muzora.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "muzora_prefs")

data class Session(
    val serverUrl: String,
    val username: String,
    val password: String,
)

data class AppSettings(
    val prefetchCount: Int = 5,
    val wifiOnly: Boolean = false,
    val playCacheOnly: Boolean = false,
    val opusBitrate: Int = 128,
    val language: String = "en",
)

class PreferencesRepository(private val context: Context) {
    private val serverUrlKey = stringPreferencesKey("server_url")
    private val usernameKey = stringPreferencesKey("username")
    private val passwordKey = stringPreferencesKey("password")
    private val prefetchKey = intPreferencesKey("prefetch_count")
    private val wifiOnlyKey = booleanPreferencesKey("wifi_only")
    private val playCacheOnlyKey = booleanPreferencesKey("play_cache_only")
    private val bitrateKey = intPreferencesKey("opus_bitrate")
    private val languageKey = stringPreferencesKey("language")
    private val batteryPromptDoneKey = booleanPreferencesKey("battery_prompt_done")

    val sessionFlow: Flow<Session?> = context.dataStore.data.map { prefs ->
        val url = prefs[serverUrlKey]
        val user = prefs[usernameKey]
        val pass = prefs[passwordKey]
        if (url.isNullOrBlank() || user.isNullOrBlank() || pass.isNullOrBlank()) null
        else Session(url, user, pass)
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            prefetchCount = prefs[prefetchKey] ?: 5,
            wifiOnly = prefs[wifiOnlyKey] ?: false,
            playCacheOnly = prefs[playCacheOnlyKey] ?: false,
            opusBitrate = prefs[bitrateKey] ?: 128,
            language = prefs[languageKey] ?: "en",
        )
    }

    suspend fun getSession(): Session? = sessionFlow.first()

    suspend fun getSettings(): AppSettings = settingsFlow.first()

    suspend fun hasLanguagePreference(): Boolean =
        context.dataStore.data.first().contains(languageKey)

    suspend fun isBatteryPromptDone(): Boolean =
        context.dataStore.data.first()[batteryPromptDoneKey] == true

    suspend fun setBatteryPromptDone() {
        context.dataStore.edit { it[batteryPromptDoneKey] = true }
    }
    suspend fun saveSession(session: Session) {
        context.dataStore.edit { prefs ->
            prefs[serverUrlKey] = session.serverUrl
            prefs[usernameKey] = session.username
            prefs[passwordKey] = session.password
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(serverUrlKey)
            prefs.remove(usernameKey)
            prefs.remove(passwordKey)
        }
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = getSettings()
        val next = transform(current)
        context.dataStore.edit { prefs ->
            prefs[prefetchKey] = next.prefetchCount
            prefs[wifiOnlyKey] = next.wifiOnly
            prefs[playCacheOnlyKey] = next.playCacheOnly
            prefs[bitrateKey] = next.opusBitrate
            prefs[languageKey] = next.language
        }
    }
}
