package com.elite.assistant.settings

import android.content.Context

/**
 * Which language Elite listens for. Elite always answers in the language you speak;
 * this only tells the speech recogniser what to expect.
 */
enum class VoiceLanguage(val label: String, val recognizerTag: String?) {
    AUTO("AUTO", null),          // phone's language
    ENGLISH("EN", "en-IN"),      // Indian English, handles Hinglish well
    HINDI("HI", "hi-IN");

    fun next(): VoiceLanguage = entries[(ordinal + 1) % entries.size]
}

/** Remembers the chosen [VoiceLanguage] between launches. */
class LanguageStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("elite_prefs", Context.MODE_PRIVATE)

    fun get(): VoiceLanguage =
        runCatching { VoiceLanguage.valueOf(prefs.getString(KEY, null) ?: "AUTO") }.getOrDefault(VoiceLanguage.AUTO)

    fun save(language: VoiceLanguage) {
        prefs.edit().putString(KEY, language.name).apply()
    }

    private companion object {
        const val KEY = "voice_language"
    }
}
