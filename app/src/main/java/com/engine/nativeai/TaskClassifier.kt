package com.engine.nativeai

/**
 * Lightweight prompt classifier (spec §7). Deterministic keyword heuristics —
 * no model round-trip, no claims of accuracy.
 */
object TaskClassifier {

    private val LONG_CONTEXT_CHARS = 1500

    fun classify(prompt: String, networkAvailable: Boolean = true): TaskType {
        val p = prompt.lowercase()
        if (!networkAvailable) return TaskType.OFFLINE_TASK
        if (p.length > LONG_CONTEXT_CHARS) return TaskType.LONG_CONTEXT
        return when {
            containsAny(p, "image", "photo", "picture", "screenshot", "vision", "see this") ->
                TaskType.VISION
            containsAny(p, "summarize", "summary", "tl;dr", "short version", "key points") ->
                TaskType.SUMMARIZATION
            containsAny(p, "debug", "crash", "exception", "stacktrace", "not working", "why does", "error") ->
                TaskType.DEBUGGING
            containsAny(p, "code", "function", "kotlin", "c++", "python", "implement", "class", "api", "refactor") ->
                TaskType.CODING
            containsAny(p, "reason", "logic", "puzzle", "explain why", "math", "calculate", "solve") ->
                TaskType.REASONING
            containsAny(p, "research", "search", "latest", "news", "compare", "find out", "web") ->
                TaskType.RESEARCH
            containsAny(p, "document", "pdf", "extract", "analyze this") ->
                TaskType.DOCUMENT_ANALYSIS
            containsAny(p, "use tool", "run ", "execute", "memory_search", "file_search", "calculator") ->
                TaskType.TOOL_EXECUTION
            else -> TaskType.CHAT
        }
    }

    private fun containsAny(text: String, vararg words: String): Boolean =
        words.any { text.contains(it) }
}
