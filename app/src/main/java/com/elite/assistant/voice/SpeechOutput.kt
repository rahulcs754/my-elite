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
 * Sentences can be queued one by one as they stream in ([enqueue]); [whenIdle] runs a
 * callback once everything queued has been spoken. Hindi (Devanagari) sentences are
 * spoken with a Hindi voice, everything else with an Indian English voice when available.
 */
class SpeechOutput(context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    /** Utterances queued but not finished yet. IDs (not a counter) so a late callback from a
     *  stopped utterance can't be mistaken for a new one. */
    private val outstanding = mutableSetOf<String>()
    private var idleCallback: (() -> Unit)? = null
    private var ready = false
    private var failed = false
    private val queuedBeforeReady = mutableListOf<Pair<String, Float>>()
    private var waitingBeforeReady = 0

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
        failed = !ready
        if (ready) configure()
        // Speak anything requested while the engine was still starting up.
        val pending = synchronized(lock) {
            waitingBeforeReady = 0
            queuedBeforeReady.toList().also { queuedBeforeReady.clear() }
        }
        if (ready) pending.forEach { (text, volume) -> enqueue(text, volume) }
        fireIdleIfDone()
    }

    private fun configure() {
        // Slightly lower pitch and calm rate for an "AI butler" feel. Tweak to taste.
        tts.setPitch(0.9f)
        tts.setSpeechRate(1.0f)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finishOne(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finishOne(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finishOne(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = Unit
        })
    }

    /**
     * Adds [text] to the speech queue. [volume] is 0..1 relative to media volume; Coach mode
     * uses a low value so the suggestion is a whisper.
     */
    fun enqueue(text: String, volume: Float = 1f) {
        if (text.isBlank()) return
        if (failed) return                            // no voice engine: stay silent
        synchronized(lock) {
            if (!ready) {
                queuedBeforeReady += text to volume
                waitingBeforeReady++
                return
            }
        }
        val id = UUID.randomUUID().toString()
        synchronized(lock) { outstanding += id }
        tts.language = localeFor(text)
        val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume) }
        if (tts.speak(text, TextToSpeech.QUEUE_ADD, params, id) != TextToSpeech.SUCCESS) finishOne(id)
    }

    /** Runs [onDone] on the main thread once everything queued so far has been spoken. */
    fun whenIdle(onDone: () -> Unit) {
        synchronized(lock) { idleCallback = onDone }
        fireIdleIfDone()
    }

    /** Interrupts speech now and drops the queue; a pending [whenIdle] callback still runs. */
    fun stop() {
        if (ready) tts.stop()
        synchronized(lock) {
            outstanding.clear()
            queuedBeforeReady.clear()
            waitingBeforeReady = 0
        }
        fireIdleIfDone()
    }

    /** Replace whatever is playing with [text], then call [onDone]. */
    fun speak(text: String, volume: Float = 1f, onDone: () -> Unit = {}) {
        if (ready) tts.stop()
        synchronized(lock) {
            outstanding.clear()
            idleCallback = null
        }
        enqueue(text, volume)
        whenIdle(onDone)
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun finishOne(utteranceId: String?) {
        synchronized(lock) { outstanding.remove(utteranceId) }
        fireIdleIfDone()
    }

    private fun fireIdleIfDone() {
        val callback = synchronized(lock) {
            if (outstanding.isEmpty() && waitingBeforeReady == 0) idleCallback.also { idleCallback = null } else null
        } ?: return
        mainHandler.post(callback)
    }

    /** Devanagari text gets a Hindi voice; everything else Indian English (or the phone default). */
    private fun localeFor(text: String): Locale {
        val hindi = text.any { it in 'ऀ'..'ॿ' }
        val wanted = if (hindi) Locale("hi", "IN") else Locale("en", "IN")
        val ok = tts.isLanguageAvailable(wanted) >= TextToSpeech.LANG_AVAILABLE
        return if (ok) wanted else Locale.getDefault()
    }
}
