package com.elite.assistant.voice

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * Wrapper around Android's Text-to-Speech engine so Elite can talk back.
 *
 * [speak] calls [onDone] on the main thread once the sentence has finished (or failed),
 * which is how the voice loop knows when to run follow-up actions.
 */
class SpeechOutput(context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val pending = mutableMapOf<String, () -> Unit>()
    private var ready = false
    private var failed = false
    private var queuedBeforeReady: Triple<String, Float, () -> Unit>? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        failed = !ready
        if (ready) configure()
        // Speak anything requested while the engine was still starting up.
        queuedBeforeReady?.let { (text, volume, done) ->
            queuedBeforeReady = null
            if (ready) speak(text, volume, done) else done()
        }
    }

    private fun configure() {
        val result = tts.setLanguage(Locale.getDefault())
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.setLanguage(Locale.US)
        }
        // Slightly lower pitch and calm rate for an "AI butler" feel. Tweak to taste.
        tts.setPitch(0.9f)
        tts.setSpeechRate(1.0f)

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
    }

    /**
     * Speaks [text]. [volume] is 0..1 relative to the media volume; Coach mode uses a low
     * value so the suggestion is a whisper the other person barely hears.
     */
    fun speak(text: String, volume: Float = 1f, onDone: () -> Unit = {}) {
        if (failed) {
            // No voice engine on this phone: skip speech but keep the loop moving.
            mainHandler.post(onDone)
            return
        }
        if (!ready) {
            queuedBeforeReady = Triple(text, volume, onDone)
            return
        }
        val id = UUID.randomUUID().toString()
        synchronized(pending) { pending[id] = onDone }
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id)
    }

    /** Interrupt speech immediately (e.g. the user tapped the mic again). */
    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun finish(utteranceId: String?) {
        val callback = synchronized(pending) { pending.remove(utteranceId) } ?: return
        mainHandler.post(callback)
    }
}
