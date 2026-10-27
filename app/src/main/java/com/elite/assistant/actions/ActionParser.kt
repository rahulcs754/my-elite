package com.elite.assistant.actions

/** Claude's reply split into what to say out loud and what to do. */
data class ParsedReply(
    val speech: String,
    val actions: List<EliteAction>,
)

/**
 * Pulls [ACTION: XYZ] tags out of Claude's reply.
 *
 * "Opening the camera, Sir. [ACTION: OPEN_CAMERA]"
 *   -> speech = "Opening the camera, Sir.", actions = [OPEN_CAMERA]
 *
 * Unknown tags are removed from the speech and ignored, so a typo never gets read aloud.
 */
object ActionParser {

    private val TAG = Regex("""\[\s*ACTION\s*:\s*([A-Za-z_]+)\s*]""", RegexOption.IGNORE_CASE)

    fun parse(reply: String): ParsedReply {
        val actions = TAG.findAll(reply)
            .mapNotNull { EliteAction.fromTag(it.groupValues[1]) }
            .distinct()
            .toList()

        val speech = reply.replace(TAG, "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return ParsedReply(speech, actions)
    }
}
