package com.engine.nativeai

/**
 * Structured agent trace events (spec §9/§20). Every event carries a
 * timestamp and an optional duration so the trace can be rendered,
 * exported, and diagnosed without guessing. Fields are defaulted so
 * existing emitters keep compiling.
 */
sealed class AgentEvent {
    data class Token(
        val text: String,
        val atMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    ) : AgentEvent()

    data class Stage(
        val state: AgentState,
        val atMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    ) : AgentEvent()

    data class Routed(
        val modelId: String,
        val provider: String,
        val costTier: ModelCostTier,
        val taskType: TaskType,
        /** Honest reason when the router substituted a different model than
         *  the explicit selection (preferred unavailable/rate-limited). */
        val reason: String = "",
        val atMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    ) : AgentEvent()

    data class ToolCall(
        val tool: String,
        val input: String,
        val atMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    ) : AgentEvent()

    data class Observation(
        val tool: String,
        val output: String,
        val atMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    ) : AgentEvent()

    data class Verification(
        val tool: String,
        val passed: Boolean,
        val atMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    ) : AgentEvent()

    data class Final(
        val answer: String,
        val sources: List<String> = emptyList(),
        val atMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    ) : AgentEvent()

    data class Error(
        val message: String,
        val atMs: Long = System.currentTimeMillis(),
        val durationMs: Long = 0,
    ) : AgentEvent()
}
