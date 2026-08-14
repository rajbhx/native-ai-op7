package com.engine.nativeai

/**
 * Conservative defaults per the gold-standard spec: context 2048, GPU offload
 * 0 until Phase 1 benchmarks prove the Adreno 640 layer count, 4 threads.
 */
data class EngineConfig(
    val modelPath: String,
    val threads: Int = 4,
    val gpuLayers: Int = 0,
    val contextSize: Int = 2048,
    val maxTokens: Int = 128,
    // Blueprint Phase 2: pin llama worker threads to Gold/Prime cores (4-7).
    // Measured on-device before any default is trusted (ADR-009).
    val pinHighCores: Boolean = true,
)
