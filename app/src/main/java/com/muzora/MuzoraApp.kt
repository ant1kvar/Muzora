package com.muzora

import android.app.Application
import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.muzora.di.AppContainer
import kotlinx.coroutines.runBlocking

class MuzoraApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        runBlocking {
            val prefs = container.preferences
            val lang = if (prefs.hasLanguagePreference()) {
                prefs.getSettings().language
            } else {
                prefs.updateSettings { it.copy(language = "en") }
                "en"
            }
            applyAppLanguage(this@MuzoraApp, lang)
        }
    }

    companion object {
        private const val TAG = "MuzoraLocale"

        fun applyAppLanguage(app: Application, language: String) {
            val tag = when (language) {
                "ru" -> "ru-RU"
                "system" -> null
                else -> "en-US"
            }
            Log.i(TAG, "apply language=$language tag=$tag")
            if (Build.VERSION.SDK_INT >= 33) {
                val manager = app.getSystemService(LocaleManager::class.java)
                if (manager != null) {
                    manager.applicationLocales = if (tag == null) {
                        LocaleList.getEmptyLocaleList()
                    } else {
                        LocaleList.forLanguageTags(tag)
                    }
                    Log.i(TAG, "LocaleManager now=${manager.applicationLocales}")
                }
            }
            val compat = if (tag == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(tag)
            }
            AppCompatDelegate.setApplicationLocales(compat)
        }
    }
}
