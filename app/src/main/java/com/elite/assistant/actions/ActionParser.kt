package com.elite.assistant.actions

/** Claude's reply split into what to say out loud and what to do. */
data class ParsedReply(
    val speech: String,
    val actions: List<ActionCall>,
)

/**
 * Pulls [ACTION: NAME] and [ACTION: NAME: argument] tags out of Claude's reply.
 *
 * "Setting it, Sir. [ACTION: SET_ALARM: 06:30 | Gym]"
 *   -> speech = "Setting it, Sir.", actions = [SET_ALARM("06:30 | Gym")]
 *
 * Unknown tags are removed from the speech and ignored, so a typo never gets read aloud.
 */
object ActionParser {

    private val TAG = Regex(
        """\[\s*ACTION\s*:\s*([A-Za-z_]+)\s*(?::\s*([^\]]*))?]""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(reply: String): ParsedReply {
        val actions = TAG.findAll(reply)
            .mapNotNull { m ->
                EliteAction.fromTag(m.groupValues[1])?.let { ActionCall(it, m.groupValues[2].trim()) }
            }
            .distinct()
            .toList()
        return ParsedReply(stripTags(reply), actions)
    }

    /** The reply with every tag removed and spacing tidied. */
    fun stripTags(text: String): String =
        text.replace(TAG, "").replace(Regex("""\s+"""), " ").trim()
}
