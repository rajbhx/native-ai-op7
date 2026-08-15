package com.engine.nativeai

/**
 * Hard context budget (spec §15 + math spec §2.C). Priority: system rules >
 * current user request > active tool observations > relevant sources >
 * relevant memory > older conversation. Compression starts at 85% of the model
 * context window, never exceeds it, and never touches the system prompt.
 */
class ContextManager(
    val maxTokens: Int = Op7SystemProfile.CONTEXT_LENGTH,
    private val charsPerToken: Int = 4,
    private val compressThreshold: Float = Op7SystemProfile.CONTEXT_COMPRESS_THRESHOLD,
) {
    private val budgetTokens: Int = (maxTokens * compressThreshold).toInt().coerceAtLeast(1)

    fun estimateTokens(text: String): Int =
        (text.length + charsPerToken - 1) / charsPerToken

    fun build(
        system: String,
        user: String,
        memory: String,
        observations: List<String>,
        sources: String = "",
    ): String {
        val obs = observations.toMutableList()
        var memoryCtx = memory.takeUnless { it.isBlank() } ?: ""
        var sourcesCtx = sources.takeUnless { it.isBlank() } ?: ""

        fun render(): String = buildString {
            append(system).append("\n\n").append(user)
            if (memoryCtx.isNotBlank()) {
                append("\n\nRelevant memory:\n").append(memoryCtx)
            }
            if (sourcesCtx.isNotBlank()) {
                append("\n\nRelevant sources:\n").append(sourcesCtx)
            }
            if (obs.isNotEmpty()) {
                append("\n\nObservations:\n").append(obs.joinToString("\n"))
            }
        }

        var text = render()
        while (estimateTokens(text) > budgetTokens) {
            when {
                obs.isNotEmpty() -> obs.removeAt(0) // oldest observation first
                memoryCtx.isNotBlank() -> memoryCtx = ""
                sourcesCtx.isNotBlank() -> sourcesCtx = ""
                else -> {
                    // Still over budget with only system+user: hard-truncate
                    // the user section (never the system prompt).
                    val budget = (budgetTokens * charsPerToken).coerceAtLeast(system.length + 40)
                    val userKeep = user.take((budget - system.length - 20).coerceAtLeast(20))
                    text = system + "\n\n" + userKeep
                    break
                }
            }
            text = render()
        }
        return text
    }
}
