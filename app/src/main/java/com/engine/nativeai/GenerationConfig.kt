package com.engine.nativeai

/** Sampler + generation limits passed to the native sampler chain. */
data class GenerationConfig(
    val maxTokens: Int = 128,
    val temperature: Float = 0.8f,
    val topP: Float = 0.95f,
    val topK: Int = 40,
    val repetitionPenalty: Float = 1.0f,
    val penaltyLastN: Int = 64,
    val stopSequences: List<String> = emptyList(),
)
