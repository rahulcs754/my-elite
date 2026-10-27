package com.elite.assistant.ai

/**
 * Cuts streamed text into whole sentences so Elite can start speaking the first one
 * while Claude is still writing the rest.
 *
 * Splits after . ! ? or the Hindi danda (।) when followed by whitespace, but never inside
 * an [ACTION: ...] tag, so tags with times or labels stay intact.
 */
class SentenceSplitter {
    private val buffer = StringBuilder()

    /** Adds streamed text; returns any sentences that are now complete. */
    fun feed(text: String): List<String> {
        buffer.append(text)
        val done = mutableListOf<String>()
        var depth = 0
        var start = 0
        var i = 0
        while (i < buffer.length) {
            val c = buffer[i]
            when (c) {
                '[' -> depth++
                ']' -> if (depth > 0) depth--
            }
            val isEnd = depth == 0 && (c == '.' || c == '!' || c == '?' || c == '।' || c == '\n')
            if (isEnd && i + 1 < buffer.length && buffer[i + 1].isWhitespace()) {
                done += buffer.substring(start, i + 1)
                start = i + 1
            }
            i++
        }
        buffer.delete(0, start)
        return done.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** Whatever is left once the stream ends. */
    fun flush(): String? = buffer.toString().trim().also { buffer.clear() }.ifEmpty { null }
}
