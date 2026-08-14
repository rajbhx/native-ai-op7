package com.engine.nativeai

/**
 * Hard context budget (spec §15). Priority: system > current user request >
 * current tool results > relevant memory > older conversation. Never exceeds
 * maxTokens (4 chars/token heuristic; fine-tuned by profiling in phase 7).
 */
class ContextManager(
    private val maxTokens: Int = 2048,
    private val charsPerToken: Int = 4,
) {
    fun estimateTokens(text: String): Int =
        (text.length + charsPerToken - 1) / charsPerToken

    fun build(
        system: String,
        user: String,
        memory: String,
        observations: List<String>,
    ): String {
        val obs = observations.toMutableList()
        var memoryCtx = memory.takeUnless { it.isBlank() } ?: ""

        fun render(): String = buildString {
            append(system).append("\n\n").append(user)
            if (memoryCtx.isNotBlank()) {
                append("\n\nRelevant memory:\n").append(memoryCtx)
            }
            if (obs.isNotEmpty()) {
                append("\n\nObservations:\n").append(obs.joinToString("\n"))
            }
        }

        var text = render()
        while (estimateTokens(text) > maxTokens) {
            when {
                obs.isNotEmpty() -> obs.removeAt(0) // oldest first
                memoryCtx.isNotBlank() -> memoryCtx = ""
                else -> {
                    // Still over budget with only system+user: hard-truncate
                    // the user section (never the system prompt).
                    val budget = (maxTokens * charsPerToken).coerceAtLeast(system.length + 40)
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
