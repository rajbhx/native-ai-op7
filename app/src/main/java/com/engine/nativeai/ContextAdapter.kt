package com.engine.nativeai

/**
 * Per-provider context limits (spec §15). Before any request is sent, the
 * prompt is fitted to the selected model's context length — never beyond it.
 */
object ContextAdapter {

    private const val CHARS_PER_TOKEN = 4

    fun estimateTokens(text: String): Int =
        (text.length + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN

    /** Hard-fit: truncate the lowest-priority tail (observations/memory). */
    fun fit(text: String, limitTokens: Int): String {
        if (limitTokens <= 0) return ""
        val maxChars = limitTokens * CHARS_PER_TOKEN
        return if (text.length <= maxChars) text else text.take(maxChars)
    }
}
