package com.engine.nativeai

/**
 * Strict fallback parser for the agent's structured output (spec §11):
 * prefers `{"action": "...", "input": "..."}`; never trusts prose.
 */
object ActionParser {

    data class Action(val name: String, val input: String)

    fun parse(text: String): Action? {
        var i = text.indexOf('{')
        while (i >= 0) {
            val end = findClosingBrace(text, i)
            if (end > i) {
                val obj = text.substring(i, end + 1)
                val name = extract(obj, "action")
                val input = extract(obj, "input")
                if (name != null && input != null) {
                    return Action(name, input)
                }
            }
            i = text.indexOf('{', i + 1)
        }
        return null
    }

    private fun findClosingBrace(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (j in start until text.length) {
            val c = text[j]
            when {
                inString -> {
                    if (escaped) escaped = false
                    else if (c == '\\') escaped = true
                    else if (c == '"') inString = false
                }
                c == '"' -> inString = true
                c == '{' -> depth++
                c == '}' -> {
                    depth--
                    if (depth == 0) return j
                }
            }
        }
        return -1
    }

    private fun extract(obj: String, key: String): String? {
        val regex = Regex("\"$key\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
        val m = regex.find(obj) ?: return null
        return m.groupValues[1]
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
            .replace("\\t", "\t")
    }
}
