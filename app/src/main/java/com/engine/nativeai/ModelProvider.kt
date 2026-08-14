package com.engine.nativeai

import kotlinx.coroutines.flow.Flow

/**
 * Provider-neutral inference contract (spec §1, §12). The agent kernel only
 * ever sees this interface — never a specific provider implementation.
 */
interface ModelProvider {
    val descriptor: ModelDescriptor

    /** Unified streaming event stream (Token / Reasoning / Done / Error). */
    fun stream(request: ModelRequest): Flow<ModelStreamEvent>

    /** One-shot completion (used by benchmarks and non-streaming providers). */
    suspend fun complete(request: ModelRequest): ModelResult

    /** Live health probe; never fabricates availability. */
    suspend fun health(): ProviderHealth
}

/** Unified streaming events consumed by the agent loop and the UI. */
sealed class ModelStreamEvent {
    data class Token(val text: String) : ModelStreamEvent()
    data class Reasoning(val text: String) : ModelStreamEvent()
    data class Done(val tokens: Int) : ModelStreamEvent()
    data class Error(val message: String) : ModelStreamEvent()
}

/** Non-streaming result. */
data class ModelResult(
    val text: String,
    val tokens: Int,
    val durationMs: Long,
    val providerId: String,
    val cancelled: Boolean = false,
)

/** Health probe result (spec §13). */
data class ProviderHealth(
    val available: Boolean,
    val latencyMs: Long = 0,
    val detail: String = "",
)
