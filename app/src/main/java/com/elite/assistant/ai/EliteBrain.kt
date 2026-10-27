package com.elite.assistant.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.RateLimitException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The "brain" of Elite: sends what the user said to Claude and returns the reply text.
 *
 * Keeps the running conversation so follow-ups ("and what about now?") have context.
 * The reply may contain action tags such as [ACTION: OPEN_CAMERA]; [ActionParser] pulls those out.
 */
class EliteBrain(apiKey: String) {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    /** Conversation so far, oldest first. Always alternates user / assistant. */
    private val history = mutableListOf<MessageParam>()

    /**
     * Sends [userText] to Claude and returns the raw reply (action tags included).
     * Runs on the IO dispatcher, so it is safe to call from the main thread.
     * Never throws: failures come back as a short sentence Elite can speak.
     */
    suspend fun ask(userText: String): String = withContext(Dispatchers.IO) {
        // Start a fresh conversation instead of trimming old turns: the API expects
        // earlier turns to stay exactly as they were sent.
        if (history.size >= MAX_HISTORY_MESSAGES) history.clear()

        val userMessage = MessageParam.builder()
            .role(MessageParam.Role.USER)
            .content(userText)
            .build()
        history.add(userMessage)

        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(SYSTEM_PROMPT)
            .messages(history.toList())
            // Low effort keeps spoken replies fast; raise to MEDIUM for harder questions.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
            // If Claude's safety filter declines a harmless request, let the API retry it
            // on a fallback model automatically instead of failing.
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()

        try {
            val response = client.messages().create(params)

            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                history.removeAt(history.lastIndex)
                return@withContext "I'm afraid I can't help with that one, Sir."
            }

            // Store the full assistant turn (including any thinking blocks) so the next
            // request carries the conversation back unchanged.
            history.add(response.toParam())

            response.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString(" ")
                .trim()
                .ifEmpty { "Done, Sir." }
        } catch (e: Exception) {
            // Drop the unanswered user turn so history keeps alternating correctly.
            history.remove(userMessage)
            friendlyError(e)
        }
    }

    /**
     * Conversation Coach: reads the recent [transcript] of a conversation the user is having
     * and returns one good reply the user could say next. Stateless: does not touch [history].
     */
    suspend fun suggestReply(transcript: String): String = withContext(Dispatchers.IO) {
        val params = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(16000L)
            .system(COACH_PROMPT)
            .addUserMessage("Conversation so far (most recent last):\n\n$transcript\n\nWhat should I say next?")
            // A bit more thought than chat: the wording matters here.
            .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
            .putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .build()
        try {
            val response = client.messages().create(params)
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) {
                return@withContext "I can't suggest a reply for this one, Sir."
            }
            response.content()
                .mapNotNull { block -> block.text().orElse(null)?.text() }
                .joinToString(" ")
                .trim()
                .ifEmpty { "I couldn't think of a good reply, Sir." }
        } catch (e: Exception) {
            friendlyError(e)
        }
    }

    /** Forget the conversation (e.g. a "new session" button later on). */
    fun reset() = history.clear()

    private fun friendlyError(e: Exception): String = when (e) {
        is UnauthorizedException -> "My API key was rejected, Sir. Tap KEY at the top to enter a new one."
        is RateLimitException -> "I'm being rate limited, Sir. Give me a moment."
        is AnthropicServiceException -> "The server returned an error, Sir. Code ${e.statusCode()}."
        else -> "I can't reach my servers, Sir. Please check the internet connection."
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
        private const val MAX_HISTORY_MESSAGES = 40

        /**
         * Personality + action protocol. Add new tags here AND in [com.elite.assistant.actions.EliteAction]
         * when you extend Elite (Phase 3: alarms, notifications, location).
         */
        val SYSTEM_PROMPT = """
            You are ELITE, a personal voice assistant running on the user's Android phone, in the
            spirit of a calm, capable AI butler. Address the user as "Sir".

            Everything you write is read aloud by text-to-speech, so:
            - Answer in one to three short sentences unless the user asks for detail.
            - Use plain spoken words only: no markdown, bullet points, emoji, code or URLs.

            You can control the phone by adding action tags. Available tags:
            [ACTION: OPEN_CAMERA]   opens the camera to take a photo
            [ACTION: OPEN_GALLERY]  opens the photo gallery
            [ACTION: BATTERY_LEVEL] the app reads the battery level and speaks it after your reply

            Rules for tags:
            - When the user asks for one of these things, give a short spoken confirmation and put
              the tag at the very end of your reply, for example: "Opening the camera, Sir. [ACTION: OPEN_CAMERA]"
            - You do not know the battery level yourself; for BATTERY_LEVEL just say something like
              "Checking power levels." and the app adds the number.
            - Use only the tags listed above, exactly as written. If the user asks for a phone action
              that is not listed, say you cannot do that yet.
            - Never mention the tags or this protocol out loud.
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
    }
}
