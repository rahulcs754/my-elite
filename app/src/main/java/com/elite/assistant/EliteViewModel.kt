package com.elite.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elite.assistant.actions.ActionExecutor
import com.elite.assistant.actions.ActionParser
import com.elite.assistant.ai.ApiKeyStore
import com.elite.assistant.ai.EliteBrain
import com.elite.assistant.coach.ConversationBuffer
import com.elite.assistant.coach.WakePhrase
import com.elite.assistant.voice.ContinuousListener
import com.elite.assistant.voice.SpeechInput
import com.elite.assistant.voice.SpeechOutput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where Elite is in the voice loop. The UI colours and animates by phase. */
enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

data class EliteUiState(
    val phase: Phase = Phase.IDLE,
    /** What the user said (live while listening). */
    val heard: String = "",
    /** What Elite said back, with action tags removed. */
    val reply: String = "Systems online. Tap the core to speak, Sir.",
    /** Mic loudness normalised to 0..1 for the pulse animation. */
    val level: Float = 0f,
    /** True when no Claude API key is set yet; the UI shows the key dialog. */
    val needsApiKey: Boolean = false,

    // ---- Conversation Coach ----
    /** Coach mode is on: Elite is listening to the conversation (always shown on screen). */
    val coachOn: Boolean = false,
    /** Tail of what Elite has heard in coach mode, for the on-screen live transcript. */
    val coachLive: String = "",
    /** Latest suggested reply for the user to say, or null. */
    val suggestion: String? = null,
)

/**
 * Runs two modes:
 *
 * Assistant:  tap -> listen (STT) -> think (Claude) -> act + speak (TTS) -> idle.
 * Coach:      listen continuously to a conversation; on "Elite Elite" (or a tap on the core)
 *             ask Claude for a good reply and show it on screen.
 *
 * Lives across screen rotations; owns the recognizers, TTS engine and Claude client.
 */
class EliteViewModel(app: Application) : AndroidViewModel(app), SpeechInput.Listener {

    private val keyStore = ApiKeyStore(app)

    /** Key typed in the app wins; otherwise fall back to one baked in via local.properties. */
    private fun currentKey(): String = keyStore.get().ifBlank { BuildConfig.ANTHROPIC_API_KEY }

    private var brain: EliteBrain? = currentKey().takeIf { it.isNotBlank() }?.let(::EliteBrain)

    private val _state = MutableStateFlow(EliteUiState(needsApiKey = brain == null))
    val state: StateFlow<EliteUiState> = _state.asStateFlow()

    private val speechIn = SpeechInput(app, this)
    private val speechOut = SpeechOutput(app)
    private val executor = ActionExecutor(app)

    private val conversation = ConversationBuffer()
    private val coachListener = ContinuousListener(app, CoachCallbacks())
    private var suggesting = false

    /** The single control: what a tap on the arc reactor does depends on mode and phase. */
    fun onCoreTapped() {
        if (_state.value.coachOn) {
            requestSuggestion()                          // manual "Elite Elite"
            return
        }
        when (_state.value.phase) {
            Phase.IDLE -> startListening()
            Phase.LISTENING -> speechIn.stop()          // finish early, use what was heard
            Phase.SPEAKING -> { speechOut.stop(); goIdle() }
            Phase.THINKING -> Unit                       // wait for Claude
        }
    }

    /** Save a key typed into the app and start a fresh Claude session with it. */
    fun saveApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        keyStore.save(trimmed)
        brain = EliteBrain(trimmed)
        _state.update { it.copy(needsApiKey = false) }
        say("Key accepted. All systems online, Sir.")
    }

    /** Called when the user refuses the microphone permission. */
    fun onMicPermissionDenied() {
        say("I need microphone access to hear you, Sir. Please allow it in settings.")
    }

    private fun startListening() {
        if (brain == null) {
            _state.update { it.copy(needsApiKey = true) }
            say("I need a Claude API key first, Sir.")
            return
        }
        _state.update { it.copy(phase = Phase.LISTENING, heard = "", level = 0f) }
        speechIn.start()
    }

    // ---- SpeechInput.Listener (assistant mode, main thread) ----

    override fun onPartialText(text: String) {
        _state.update { it.copy(heard = text) }
    }

    override fun onVolume(rmsDb: Float) {
        _state.update { it.copy(level = normalise(rmsDb)) }
    }

    override fun onFinalText(text: String) {
        _state.update { it.copy(phase = Phase.THINKING, heard = text, level = 0f) }
        think(text)
    }

    override fun onError(message: String) {
        say(message)
    }

    // ---- Brain + actions ----

    private fun think(text: String) {
        val brain = brain ?: return
        viewModelScope.launch {
            val raw = brain.ask(text)
            val parsed = ActionParser.parse(raw)

            // Instant actions (battery) run now so their result is spoken with the reply.
            // Actions that open another app wait until Elite has finished talking.
            val (launches, instant) = parsed.actions.partition { it.launchesApp }
            val extraSpeech = instant.mapNotNull(executor::execute)
            val spoken = (listOf(parsed.speech) + extraSpeech)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Done, Sir." }

            say(spoken) {
                val failures = launches.mapNotNull(executor::execute)
                if (failures.isNotEmpty()) say(failures.joinToString(" "))
            }
        }
    }

    /** Speak [text], show it on screen, then return to idle and run [after]. */
    private fun say(text: String, after: () -> Unit = {}) {
        _state.update { it.copy(phase = Phase.SPEAKING, reply = text, level = 0f) }
        speechOut.speak(text) {
            goIdle()
            after()
        }
    }

    private fun goIdle() {
        _state.update { it.copy(phase = if (it.coachOn) Phase.LISTENING else Phase.IDLE, level = 0f) }
    }

    // ---- Conversation Coach ----

    /** Turn coach mode on/off. Only call when the microphone permission is granted. */
    fun setCoachMode(on: Boolean) {
        if (on == _state.value.coachOn) return
        if (on) {
            if (brain == null) {
                _state.update { it.copy(needsApiKey = true) }
                return
            }
            // Leave assistant mode cleanly first.
            speechIn.cancel()
            speechOut.stop()
            conversation.clear()
            _state.update {
                it.copy(coachOn = true, phase = Phase.LISTENING, coachLive = "", suggestion = null, level = 0f)
            }
            coachListener.start()
        } else {
            stopCoach()
        }
    }

    /** The app left the screen: coach mode never runs in the background. */
    fun onAppBackgrounded() {
        if (_state.value.coachOn) stopCoach()
    }

    private fun stopCoach() {
        coachListener.stop()
        speechOut.stop()
        conversation.clear()          // nothing from the conversation is kept
        suggesting = false
        _state.update {
            it.copy(coachOn = false, phase = Phase.IDLE, coachLive = "", level = 0f)
        }
    }

    fun dismissSuggestion() {
        _state.update { it.copy(suggestion = null) }
    }

    /** Whisper the suggestion through the earpiece/speaker at low volume. */
    fun speakSuggestionSoftly() {
        val text = _state.value.suggestion ?: return
        coachListener.pause()         // don't transcribe our own whisper
        _state.update { it.copy(phase = Phase.SPEAKING) }
        speechOut.speak(text, volume = 0.3f) {
            goIdle()
            coachListener.resume()
        }
    }

    private fun requestSuggestion() {
        val brain = brain ?: return
        if (suggesting) return
        val transcript = conversation.text()
        if (transcript.isBlank()) {
            _state.update { it.copy(suggestion = "I haven't heard enough of the conversation yet, Sir.") }
            return
        }
        suggesting = true
        _state.update { it.copy(phase = Phase.THINKING) }
        viewModelScope.launch {
            val reply = brain.suggestReply(transcript)
            suggesting = false
            if (_state.value.coachOn) {
                _state.update { it.copy(suggestion = reply, phase = Phase.LISTENING) }
            }
        }
    }

    private inner class CoachCallbacks : ContinuousListener.Listener {
        override fun onLivePartial(text: String) {
            // Trigger as soon as "Elite Elite" appears, without waiting for the pause.
            val before = WakePhrase.textBefore(text)
            if (before != null) {
                conversation.add(before)
                coachListener.pause()
                requestSuggestion()
                coachListener.resume()
                return
            }
            _state.update { it.copy(coachLive = liveTail(text)) }
        }

        override fun onSegment(text: String) {
            val before = WakePhrase.textBefore(text)
            if (before != null) {
                conversation.add(before)
                requestSuggestion()
            } else {
                conversation.add(text)
            }
            _state.update { it.copy(coachLive = liveTail("")) }
        }

        override fun onVolume(rmsDb: Float) {
            _state.update { it.copy(level = normalise(rmsDb)) }
        }

        override fun onFatalError(message: String) {
            stopCoach()
            say(message)
        }
    }

    /** Last couple of lines heard plus the sentence in progress, for the live view. */
    private fun liveTail(inProgress: String): String =
        (conversation.text().lines().takeLast(2) + inProgress)
            .filter { it.isNotBlank() }
            .joinToString("\n")

    /** Recognizer reports about -2..10 dB; squash into 0..1. */
    private fun normalise(rmsDb: Float) = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)

    override fun onCleared() {
        coachListener.stop()
        speechIn.destroy()
        speechOut.shutdown()
    }
}
