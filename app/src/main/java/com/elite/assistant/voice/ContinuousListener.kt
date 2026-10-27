package com.elite.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Keeps the speech recognizer running back-to-back for Conversation Coach mode.
 *
 * Android's recognizer stops after each pause, so this restarts it automatically until
 * [stop] is called. Main thread only. Nothing is recorded to disk: only text comes back.
 */
class ContinuousListener(
    context: Context,
    private val listener: Listener,
    /** BCP-47 tag such as "hi-IN", or null for the phone's language. */
    private val language: () -> String? = { null },
) {
    interface Listener {
        /** Live words of the sentence currently being spoken. */
        fun onLivePartial(text: String)
        /** A finished sentence / phrase. */
        fun onSegment(text: String)
        fun onVolume(rmsDb: Float)
        /** Listening can't continue (e.g. no permission). Safe to show the user. */
        fun onFatalError(message: String)
    }

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    val isRunning: Boolean get() = running

    fun start() {
        if (running) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            listener.onFatalError("Speech recognition isn't available on this phone, Sir.")
            return
        }
        running = true
        startSession()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
    }

    /** Pause briefly (e.g. while Elite speaks) without leaving coach mode. */
    fun pause() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.cancel()
    }

    fun resume() {
        if (running) restartSoon(0)
    }

    private fun startSession() {
        if (!running) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(callbacks)
            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    language()?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
                    // Ask for longer sessions; many phones ignore these, which is fine.
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                }
            )
        }
    }

    private fun restartSoon(delayMs: Long = 250) {
        if (!running) return
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ startSession() }, delayMs)
    }

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
        override fun onRmsChanged(rmsdB: Float) = listener.onVolume(rmsdB)

        override fun onPartialResults(partialResults: Bundle?) {
            first(partialResults)?.let(listener::onLivePartial)
        }

        override fun onResults(results: Bundle?) {
            first(results)?.takeIf { it.isNotBlank() }?.let(listener::onSegment)
            restartSoon()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    stop()
                    listener.onFatalError("I need microphone permission for coach mode, Sir.")
                }
                // Busy / client errors need a slightly longer breather before retrying.
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> restartSoon(800)
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> restartSoon(2000)
                // No speech / silence: just keep listening.
                else -> restartSoon()
            }
        }
    }

    private fun first(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
}
