package com.elite.assistant

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elite.assistant.actions.ActionCall
import com.elite.assistant.actions.ActionExecutor
import com.elite.assistant.actions.ActionParser
import com.elite.assistant.actions.ActionResult
import com.elite.assistant.ai.ApiKeyStore
import com.elite.assistant.ai.EliteBrain
import com.elite.assistant.coach.ConversationBuffer
import com.elite.assistant.coach.WakePhrase
import com.elite.assistant.settings.LanguageStore
import com.elite.assistant.settings.VoiceLanguage
import com.elite.assistant.voice.ContinuousListener
import com.elite.assistant.voice.SpeechInput
import com.elite.assistant.voice.SpeechOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

/** Where Elite is in the voice loop. The UI colours and animates by phase. */
enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

data class EliteUiState(
    val phase: Phase = Phase.IDLE,
    /** What the user said (live while listening). */
    val heard: String = "",
    /** What Elite said back, with action tags removed. Grows as sentences stream in. */
    val reply: String = "Systems online. Tap the core to speak, Sir.",
    /** Mic loudness normalised to 0..1 for the pulse animation. */
    val level: Float = 0f,
    /** True when no Claude API key is set yet; the UI shows the key dialog. */
    val needsApiKey: Boolean = false,
    /** Language the recogniser listens for (Elite answers in whatever you speak). */
    val language: VoiceLanguage = VoiceLanguage.AUTO,
    /** A runtime permission the Activity should ask for (e.g. contacts), or null. */
    val permissionRequest: String? = null,
    /** The Activity should open the camera for a LOOK question. */
    val photoRequest: Boolean = false,

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
 * Assistant:  tap -> listen (STT) -> Claude streams a reply, spoken sentence by sentence
 *             -> actions run (apps, alarms, calls, notifications, camera) -> idle.
 * Coach:      listen continuously to a conversation; on "Elite Elite" (or a tap on the core)
 *             ask Claude for a good reply and show it on screen.
 *
 * Lives across screen rotations; owns the recognizers, TTS engine and Claude client.
 */
class EliteViewModel(app: Application) : AndroidViewModel(app), SpeechInput.Listener {

    private val keyStore = ApiKeyStore(app)
    private val languageStore = LanguageStore(app)

    /** Key typed in the app wins; otherwise fall back to one baked in via local.properties. */
    private fun currentKey(): String = keyStore.get().ifBlank { BuildConfig.ANTHROPIC_API_KEY }

    private var brain: EliteBrain? = currentKey().takeIf { it.isNotBlank() }?.let(::EliteBrain)

    private val _state = MutableStateFlow(
        EliteUiState(needsApiKey = brain == null, language = languageStore.get())
    )
    val state: StateFlow<EliteUiState> = _state.asStateFlow()

    private val speechIn = SpeechInput(app, this) { _state.value.language.recognizerTag }
    private val speechOut = SpeechOutput(app)
    private val executor = ActionExecutor(app)

    /** Bumped whenever a reply is interrupted, so its leftover callbacks know to stand down. */
    private var turn = 0
    private var thinkJob: Job? = null
    /** The last thing the user asked, reused when a LOOK photo comes back. */
    private var lastQuestion = ""

    private val conversation = ConversationBuffer()
    private val coachListener = ContinuousListener(app, CoachCallbacks()) { _state.value.language.recognizerTag }
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
            Phase.THINKING, Phase.SPEAKING -> interrupt()
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

    /** AUTO -> EN -> HI -> AUTO. */
    fun cycleLanguage() {
        val next = _state.value.language.next()
        languageStore.save(next)
        _state.update { it.copy(language = next) }
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

    /** Stop talking / thinking right away. */
    private fun interrupt() {
        turn++
        thinkJob?.cancel()
        speechOut.stop()
        goIdle()
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
        val myTurn = ++turn
        lastQuestion = text
        thinkJob = viewModelScope.launch {
            var spoke = false
            // Speak each sentence as soon as it streams in.
            val raw = brain.ask(text) { sentence ->
                withContext(Dispatchers.Main) {
                    _state.update {
                        it.copy(phase = Phase.SPEAKING, reply = if (spoke) it.reply + " " + sentence else sentence)
                    }
                    spoke = true
                    speechOut.enqueue(sentence)
                }
            }
            val parsed = ActionParser.parse(raw)
            if (parsed.speech.isNotBlank()) _state.update { it.copy(reply = parsed.speech) }
            runActions(parsed.actions, text, myTurn)
        }
    }

    /**
     * Runs the reply's actions. Readings (battery, notifications) are spoken right after the
     * reply; things that open another screen wait until Elite has finished talking.
     */
    private suspend fun runActions(actions: List<ActionCall>, userText: String, myTurn: Int) {
        val afterSpeech = mutableListOf<() -> String?>()
        var permission: String? = null
        var wantPhoto = false

        for (call in actions) {
            when (val result = withContext(Dispatchers.IO) { executor.execute(call) }) {
                ActionResult.None -> Unit
                is ActionResult.Say -> addSpeech(result.text)
                is ActionResult.AfterSpeech -> afterSpeech += result.run
                is ActionResult.NeedPermission -> {
                    permission = result.permission
                    addSpeech(result.message)
                }
                is ActionResult.Notifications -> {
                    _state.update { it.copy(phase = Phase.THINKING) }
                    addSpeech(brain?.summarizeNotifications(result.digest, userText) ?: continue)
                }
                ActionResult.TakePhoto -> wantPhoto = true
            }
        }

        speechOut.whenIdle {
            if (myTurn != turn) return@whenIdle          // interrupted: don't act
            val problems = afterSpeech.mapNotNull { it() }
            goIdle()
            if (permission != null) _state.update { it.copy(permissionRequest = permission) }
            if (wantPhoto) _state.update { it.copy(photoRequest = true) }
            if (problems.isNotEmpty()) say(problems.joinToString(" "))
        }
    }

    /** Append [text] to the on-screen reply and queue it for speech. */
    private fun addSpeech(text: String) {
        if (text.isBlank()) return
        _state.update { it.copy(phase = Phase.SPEAKING, reply = (it.reply + " " + text).trim()) }
        speechOut.enqueue(text)
    }

    /** Speak [text] (replacing anything playing), show it, then go idle and run [after]. */
    private fun say(text: String, after: () -> Unit = {}) {
        val myTurn = ++turn
        _state.update { it.copy(phase = Phase.SPEAKING, reply = text, level = 0f) }
        speechOut.speak(text) {
            if (myTurn != turn) return@speak
            goIdle()
            after()
        }
    }

    private fun goIdle() {
        _state.update { it.copy(phase = if (it.coachOn) Phase.LISTENING else Phase.IDLE, level = 0f) }
    }

    // ---- Permission & camera hand-offs from the Activity ----

    fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionRequest = null) }
        if (granted) say("Thank you, Sir. Please ask me again.")
    }

    /** The Activity has started the camera; clear the request so it isn't opened twice. */
    fun onPhotoRequestHandled() {
        _state.update { it.copy(photoRequest = false) }
    }

    /** Photo for a LOOK question is ready (or the user backed out). */
    fun onPhotoResult(success: Boolean, photo: File) {
        val brain = brain ?: return
        if (!success || !photo.exists() || photo.length() == 0L) {
            say("No photo taken, Sir.")
            return
        }
        val myTurn = ++turn
        _state.update { it.copy(phase = Phase.THINKING, heard = lastQuestion) }
        thinkJob = viewModelScope.launch {
            val jpeg = withContext(Dispatchers.IO) { encodeForClaude(photo) }
            photo.delete()                                  // don't keep the picture
            if (jpeg == null) {
                say("I couldn't read that photo, Sir.")
                return@launch
            }
            val answer = brain.lookAt(lastQuestion, jpeg)
            if (myTurn == turn) say(answer)
        }
    }

    /** Shrinks the photo so its long side is at most 1568 px (Claude's sweet spot) and base64s it. */
    private fun encodeForClaude(file: File): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val longSide = max(bounds.outWidth, bounds.outHeight)
        if (longSide <= 0) return null
        var sample = 1
        while (longSide / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val scale = MAX_IMAGE_SIDE.toFloat() / max(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
        } else decoded
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
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
            turn++
            thinkJob?.cancel()
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
        turn++
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

    /** Whisper the suggestion through the speaker at low volume. */
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

    private companion object {
        const val MAX_IMAGE_SIDE = 1568
    }
}
