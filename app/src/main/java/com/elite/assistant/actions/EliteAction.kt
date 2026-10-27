package com.elite.assistant.actions

/**
 * Every phone action Elite knows. The tag name must match what the system prompt in
 * [com.elite.assistant.ai.EliteBrain] tells Claude to emit, e.g. [ACTION: OPEN_CAMERA]
 * or, with an argument, [ACTION: OPEN_APP: YouTube].
 */
enum class EliteAction(val tag: String) {
    OPEN_CAMERA("OPEN_CAMERA"),
    OPEN_GALLERY("OPEN_GALLERY"),
    BATTERY_LEVEL("BATTERY_LEVEL"),
    OPEN_APP("OPEN_APP"),
    SET_ALARM("SET_ALARM"),
    SET_TIMER("SET_TIMER"),
    CALL("CALL"),
    WHATSAPP("WHATSAPP"),
    READ_NOTIFICATIONS("READ_NOTIFICATIONS"),
    LOOK("LOOK");

    companion object {
        fun fromTag(tag: String): EliteAction? =
            entries.firstOrNull { it.tag.equals(tag.trim(), ignoreCase = true) }
    }
}

/** One tag found in a reply: the action plus its argument text (may be empty). */
data class ActionCall(val action: EliteAction, val arg: String = "") {
    /** Argument split on "|", e.g. "06:30 | Gym" -> ["06:30", "Gym"]. */
    val parts: List<String> get() = arg.split('|').map { it.trim() }
}
