package com.engine.nativeai

/** Provider-neutral inference request (spec §12, §15). */
data class ModelRequest(
    val system: String = "",
    val prompt: String,
    val maxTokens: Int = 128,
    val temperature: Float = 0.8f,
    val stopSequences: List<String> = emptyList(),
)
