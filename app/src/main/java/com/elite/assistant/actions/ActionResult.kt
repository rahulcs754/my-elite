package com.elite.assistant.actions

/** What the voice loop should do after an action ran (or tried to). */
sealed interface ActionResult {
    /** Nothing more to say or do. */
    data object None : ActionResult

    /** Speak this extra sentence (a reading like battery level, or an error). */
    data class Say(val text: String) : ActionResult

    /** Run once Elite has finished talking (opening another app would cut speech off). */
    data class AfterSpeech(val run: () -> String?) : ActionResult

    /** A runtime permission is missing; ask for it and explain with [message]. */
    data class NeedPermission(val permission: String, val message: String) : ActionResult

    /** Notifications collected; Claude should turn them into a spoken briefing. */
    data class Notifications(val digest: String) : ActionResult

    /** Open the camera so Claude can look at what the user is asking about. */
    data object TakePhoto : ActionResult
}
