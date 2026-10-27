package com.elite.assistant.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.WebSearchTool20260209
import com.elite.assistant.actions.ActionParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The "brain" of Elite: every call to Claude goes through here.
 *
 * - [ask]: the assistant conversation. Streams the reply sentence by sentence, can search
 *   the web, and keeps history so follow-ups have context.
 * - [suggestReply], [summarizeNotifications], [lookAt]: one-off calls with their own prompts.
 *
 * Replies may contain action tags such as [ACTION: OPEN_CAMERA]; [ActionParser] pulls those out.
 */
class EliteBrain(apiKey: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    /** Conversation so far, oldest first. Only ever appended to (or cleared). */
    private val history = mutableListOf<MessageParam>()

    /**
     * Sends [userText] to Claude and streams the answer. Each finished sentence (action tags
     * removed) is passed to [onSentence] as soon as it arrives, so speech can start early.
     *
     * Returns the full raw reply, action tags included. Never throws for API problems:
     * failures are spoken through [onSentence] and returned as text.
     */
    suspend fun ask(userText: String, onSentence: suspend (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            // Start fresh instead of trimming: earlier turns must go back exactly as sent.
            if (history.size >= MAX_HISTORY_MESSAGES) history.clear()
            val startSize = history.size

            history.add(
                MessageParam.builder()
                    .role(MessageParam.Role.USER)
                    // The phone's clock lets Claude turn "in 20 minutes" into an alarm time.
                    .content("[Local time: ${now()}] $userText")
                    .build()
            )

            val fullText = StringBuilder()
            val splitter = SentenceSplitter()
            try {
                // Web search can pause a long turn (stop reason pause_turn); continuing means
                // sending the conversation back as-is. Cap the rounds to keep replies quick.
                repeat(MAX_CONTINUATIONS) {
                    val message = streamOnce(fullText, splitter, onSentence)
                    if (message.stopReason().orElse(null) == StopReason.REFUSAL) {
                        while (history.size > startSize) history.removeAt(history.lastIndex)
                        val sorry = "I'm afraid I can't help with that one, Sir."
                        onSentence(sorry)
                        return@withContext sorry
                    }
                    history.add(message.toParam())
                    if (message.stopReason().orElse(null) != StopReason.PAUSE_TURN) {
                        splitter.flush()?.let { speakable(it) }?.let { onSentence(it) }
                        return@withContext fullText.toString().trim().ifEmpty { "Done, Sir." }
                    }
                }
                splitter.flush()?.let { speakable(it) }?.let { onSentence(it) }
                fullText.toString().trim()
            } catch (e: Exception) {
                // Remove this exchange so roles keep alternating; rethrow cancellation.
                while (history.size > startSize) history.removeAt(history.lastIndex)
                if (e is kotlinx.coroutines.CancellationException) throw e
                val msg = friendlyError(e)
                onSentence(msg)
                msg
            }
        }

    /** One streamed request; feeds sentences out and returns the complete message. */
    private suspend fun streamOnce(
        fullText: StringBuilder,
        splitter: SentenceSplitter,
        onSentence: suspend (String) -> Unit,
    ): Message {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .messages(history.toList())
            .addTool(WebSearchTool20260209.builder().maxUses(3L).build())
            // Low effort keeps spoken replies fast; raise to MEDIUM for harder questions.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            .withFallback()
            .build()

        val accumulator = MessageAccumulator.create()
        client.messages().createStreaming(params).use { stream ->
            for (event in stream.stream().iterator()) {
                kotlin.coroutines.coroutineContext.ensureActive()   // stop when the user taps
                accumulator.accumulate(event)
                val delta = event.contentBlockDelta().orElse(null)?.delta()?.text()?.orElse(null)
                    ?: continue
                fullText.append(delta.text())
                for (sentence in splitter.feed(delta.text())) {
                    speakable(sentence)?.let { onSentence(it) }
                }
            }
        }
        return accumulator.message()
    }

    /**
     * Conversation Coach: reads the recent [transcript] of a conversation the user is having
     * and returns one good reply the user could say next. Stateless: does not touch [history].
     */
    suspend fun suggestReply(transcript: String): String = oneShot(
        system = COACH_PROMPT,
        effort = OutputConfig.Effort.MEDIUM,
        blocks = listOf(text("Conversation so far (most recent last):\n\n$transcript\n\nWhat should I say next?")),
        fallbackText = "I couldn't think of a good reply, Sir.",
    )

    /** Turns the phone's current notifications into a short spoken briefing. */
    suspend fun summarizeNotifications(digest: String, userRequest: String): String = oneShot(
        system = NOTIFICATIONS_PROMPT,
        effort = OutputConfig.Effort.LOW,
        blocks = listOf(text("User asked: $userRequest\n\nCurrent notifications (newest first):\n$digest")),
        fallbackText = "You have no new notifications, Sir.",
    )

    /** Answers [question] about a photo the user just took (JPEG, base64). */
    suspend fun lookAt(question: String, jpegBase64: String): String = oneShot(
        system = VISION_PROMPT,
        effort = OutputConfig.Effort.LOW,
        blocks = listOf(
            ContentBlockParam.ofImage(
                ImageBlockParam.builder()
                    .source(
                        Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.IMAGE_JPEG)
                            .data(jpegBase64)
                            .build()
                    )
                    .build()
            ),
            text(question.ifBlank { "What is this?" }),
        ),
        fallbackText = "I couldn't make that out, Sir.",
    )

    /** Forget the conversation. */
    fun reset() = history.clear()

    private suspend fun oneShot(
        system: String,
        effort: OutputConfig.Effort,
        blocks: List<ContentBlockParam>,
        fallbackText: String,
    ): String = withContext(Dispatchers.IO) {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(system)
            .addUserMessageOfBlockParams(blocks)
            .outputConfig(OutputConfig.builder().effort(effort).build())
            .withFallback()
            .build()
        try {
            val response = client.messages().create(params)
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                return@withContext "I can't help with that one, Sir."
            }
            response.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString(" ")
                .trim()
                .ifEmpty { fallbackText }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            friendlyError(e)
        }
    }

    /**
     * If Claude's safety filter declines a harmless request, let the API retry it on a
     * fallback model automatically instead of failing.
     */
    private fun MessageCreateParams.Builder.withFallback() = this
        .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
        .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))

    private fun text(value: String) =
        ContentBlockParam.ofText(TextBlockParam.builder().text(value).build())

    /** Removes action tags and stray markdown; null if nothing is left to say. */
    private fun speakable(sentence: String): String? =
        ActionParser.stripTags(sentence).replace("*", "").replace("#", "").trim().ifEmpty { null }

    private fun now(): String = SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())

    private fun friendlyError(e: Exception): String = when (e) {
        is UnauthorizedException -> "My API key was rejected, Sir. Tap KEY at the top to enter a new one."
        is RateLimitException -> "I'm being rate limited, Sir. Give me a moment."
        is AnthropicServiceException -> "The server returned an error, Sir. Code ${e.statusCode()}."
        else -> "I can't reach my servers, Sir. Please check the internet connection."
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
        private const val MAX_HISTORY_MESSAGES = 40
        private const val MAX_CONTINUATIONS = 3

        /**
         * Personality + action protocol. Add new tags here AND in
         * [com.elite.assistant.actions.EliteAction] when you extend Elite.
         */
        val SYSTEM_PROMPT = """
            You are ELITE, a personal voice assistant running on the user's Android phone, in the
            spirit of a calm, capable AI butler. Address the user as "Sir".

            Everything you write is read aloud by text-to-speech, so:
            - Answer in one to three short sentences unless the user asks for detail.
            - Use plain spoken words only: no markdown, bullet points, emoji, code or URLs.
            - Reply in the language the user spoke. Write Hindi in Devanagari script so it is
              pronounced correctly; write Hinglish in Latin script.
            - Each user message starts with the phone's local time in brackets. Use it for
              anything time-related, but don't read it out.

            You can search the web for anything current: news, scores, prices, weather, opening
            hours. Search when the answer may have changed recently, then answer briefly
            without reading out website names or links.

            You can control the phone by adding action tags at the very end of your reply.
            Available tags (write them exactly like this):
            [ACTION: OPEN_CAMERA]                      open the camera
            [ACTION: OPEN_GALLERY]                     open the photo gallery
            [ACTION: BATTERY_LEVEL]                    the app speaks the battery level after you
            [ACTION: OPEN_APP: <app name>]             open an installed app, e.g. [ACTION: OPEN_APP: YouTube]
            [ACTION: SET_ALARM: <HH:MM 24h> | <label>] e.g. [ACTION: SET_ALARM: 06:30 | Gym]
            [ACTION: SET_TIMER: <seconds> | <label>]   e.g. [ACTION: SET_TIMER: 600 | Tea]
            [ACTION: CALL: <contact name or number>]   opens the dialer ready to call
            [ACTION: WHATSAPP: <contact name> | <message>]  opens WhatsApp with the message typed in
            [ACTION: READ_NOTIFICATIONS]               the app reads the phone's notifications and briefs the user
            [ACTION: LOOK]                             opens the camera so you can see what the user is asking about

            Rules for tags:
            - Give a short spoken confirmation first, e.g. "Setting an alarm for six thirty, Sir.
              [ACTION: SET_ALARM: 06:30 | Wake up]"
            - Convert relative times ("in 20 minutes", "tomorrow at 7") into the 24h clock time
              using the local time given.
            - For WHATSAPP, write the message the way the user would send it, in their language.
              The user still presses send themselves, so say "I've typed it out, Sir."
            - For BATTERY_LEVEL and READ_NOTIFICATIONS you don't know the answer yet; just say
              something brief like "Checking, Sir." and the app does the rest.
            - Use LOOK when the user asks about something in front of them ("what is this",
              "read this", "is this medicine safe").
            - Use only the tags listed. If the user asks for a phone action that isn't listed,
              say you can't do that yet. Never mention the tags out loud.
        """.trimIndent()

        /** Conversation Coach personality: what makes a "good response" to say to someone. */
        val COACH_PROMPT = """
            You are ELITE's conversation coach. The user is talking face to face (or on speaker)
            with another person and has quietly asked you what to say next.

            The transcript comes from one phone microphone, so speakers are NOT labelled and some
            words may be misheard. Work out from context who said what. The user is the person
            who asked for help; the other person is whoever they are talking with.

            Write the exact words the user can say next, as they would naturally say them:
            - One to three short sentences, easy to say out loud. No preamble, no quotes, no labels,
              no options list, no markdown.
            - Use the same language and tone as the conversation (Hindi, English or Hinglish).
            - Be respectful, warm and honest. Answer what was actually asked or said.
            - Calm things down if the conversation is tense; acknowledge the other person's point
              before giving the user's view.
            - Never suggest lying, manipulating, pressuring, insulting or making promises the user
              may not keep. Never invent facts about the user (prices, dates, commitments); if a
              fact is needed, suggest a polite way to check or ask.
            - If the transcript is too unclear, suggest a natural clarifying question instead.
        """.trimIndent()

        val NOTIFICATIONS_PROMPT = """
            You are ELITE, a voice assistant. Brief the user ("Sir") on their phone notifications,
            spoken aloud. Group by app and person, lead with what looks important (messages from
            people, calls, deliveries, bank alerts), and skip noise like promotions. Keep it to a
            few short sentences; offer counts rather than reading everything. Plain spoken words,
            no lists or markdown. Reply in the language the user asked in.
        """.trimIndent()

        val VISION_PROMPT = """
            You are ELITE, a voice assistant. The user pointed their phone camera at something
            and asked about it. Answer what they asked in one to three short spoken sentences,
            addressing them as "Sir". Read out text when they ask what something says. For
            medicines or food, say what it is and point them to the label or a pharmacist for
            dosing. If the photo is blurry or doesn't show the thing, say so and ask them to try
            again. Plain spoken words, no markdown. Reply in the language they asked in.
        """.trimIndent()
    }
}
