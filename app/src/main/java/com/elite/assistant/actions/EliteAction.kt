package com.elite.assistant.actions

/**
 * Every phone action Elite knows. The tag name must match what the system prompt in
 * [com.elite.assistant.ai.EliteBrain] tells Claude to emit, e.g. [ACTION: OPEN_CAMERA].
 *
 * [launchesApp] actions open another screen, so they run after Elite finishes speaking.
 */
enum class EliteAction(val tag: String, val launchesApp: Boolean) {
    OPEN_CAMERA("OPEN_CAMERA", launchesApp = true),
    OPEN_GALLERY("OPEN_GALLERY", launchesApp = true),
    BATTERY_LEVEL("BATTERY_LEVEL", launchesApp = false);

    companion object {
        fun fromTag(tag: String): EliteAction? =
            entries.firstOrNull { it.tag.equals(tag.trim(), ignoreCase = true) }
    }
}
