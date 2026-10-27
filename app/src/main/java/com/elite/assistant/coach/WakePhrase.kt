package com.elite.assistant.coach

/**
 * Detects the coach trigger: the user saying "Elite Elite".
 *
 * Speech recognizers often mishear a made-up name, so a few common spellings
 * ("a lead", "e lite", "elight") also count.
 */
object WakePhrase {

    private const val WORD = """(?:elite|e lite|e light|elight|a lead|alight|ilite|elit)"""
    private val TRIGGER = Regex("""\b$WORD[\s,.!?-]*$WORD\b""", RegexOption.IGNORE_CASE)

    /** Returns the text spoken before the trigger, or null if the trigger isn't present. */
    fun textBefore(phrase: String): String? =
        TRIGGER.find(phrase)?.let { phrase.substring(0, it.range.first).trim() }
}
