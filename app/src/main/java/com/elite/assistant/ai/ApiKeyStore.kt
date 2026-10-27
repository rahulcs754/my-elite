package com.elite.assistant.ai

import android.content.Context

/**
 * Remembers the Claude API key the user typed into the app, so a prebuilt APK
 * works without rebuilding. Stored in app-private storage on this phone only.
 */
class ApiKeyStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("elite_prefs", Context.MODE_PRIVATE)

    fun get(): String = prefs.getString(KEY, "").orEmpty()

    fun save(key: String) {
        prefs.edit().putString(KEY, key.trim()).apply()
    }

    private companion object {
        const val KEY = "anthropic_api_key"
    }
}
