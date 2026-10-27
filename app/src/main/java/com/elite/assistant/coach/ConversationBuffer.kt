package com.elite.assistant.coach

/**
 * Rolling, in-memory transcript of the conversation for Coach mode.
 * Keeps only the last few minutes; never written to disk.
 */
class ConversationBuffer(
    private val maxAgeMs: Long = 5 * 60_000L,
    private val maxChars: Int = 4000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Line(val at: Long, val text: String)

    private val lines = ArrayDeque<Line>()

    fun add(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        lines.addLast(Line(clock(), clean))
        prune()
    }

    /** Transcript oldest-first, one recognised phrase per line. */
    fun text(): String {
        prune()
        return lines.joinToString("\n") { it.text }
    }

    fun isEmpty(): Boolean = text().isBlank()

    fun clear() = lines.clear()

    private fun prune() {
        val cutoff = clock() - maxAgeMs
        while (lines.isNotEmpty() && lines.first().at < cutoff) lines.removeFirst()
        while (lines.sumOf { it.text.length } > maxChars && lines.size > 1) lines.removeFirst()
    }
}
