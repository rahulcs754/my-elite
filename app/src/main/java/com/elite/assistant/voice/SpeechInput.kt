package com.elite.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Thin wrapper around Android's built-in speech recognizer (speech-to-text).
 *
 * Must be created and used on the main thread. Call [destroy] when done.
 */
class SpeechInput(
    context: Context,
    private val listener: Listener,
    /** BCP-47 tag such as "hi-IN", or null for the phone's language. Read on each start. */
    private val language: () -> String? = { null },
) {
    interface Listener {
        /** Words recognised so far, updated live while the user talks. */
        fun onPartialText(text: String)
        /** Final transcript once the user stops talking. */
        fun onFinalText(text: String)
        /** Mic loudness, roughly -2..10 dB; drives the arc-reactor pulse. */
        fun onVolume(rmsDb: Float)
        /** Something went wrong; [message] is safe to speak to the user. */
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private var recognizer: SpeechRecognizer? = null

    val isAvailable: Boolean
        get() = SpeechRecognizer.isRecognitionAvailable(appContext)

    fun start() {
        if (!isAvailable) {
            listener.onError("Speech recognition isn't available on this phone, Sir.")
            return
        }
        // A fresh recognizer per session avoids "recognizer busy" errors on some devices.
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(callbacks)
            startListening(buildIntent())
        }
    }

    /** Stop listening and process what was heard so far. */
    fun stop() {
        recognizer?.stopListening()
    }

    /** Abort without producing a result. */
    fun cancel() {
        recognizer?.cancel()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun buildIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        language()?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
    }

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) = listener.onVolume(rmsdB)

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let(listener::onPartialText)
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            if (text.isNullOrBlank()) listener.onError("I didn't catch that, Sir.")
            else listener.onFinalText(text)
        }

        override fun onError(error: Int) = listener.onError(describe(error))
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()

    private fun describe(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that, Sir."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "I need microphone permission, Sir."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition needs a network connection, Sir."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recognizer is busy, Sir. Try again."
        SpeechRecognizer.ERROR_AUDIO -> "There was a problem with the microphone, Sir."
        else -> "Voice recognition failed, Sir. Error $error."
    }
}
