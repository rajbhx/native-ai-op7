package com.engine.nativeai

data class InferenceResult(
    val text: String,
    val cancelled: Boolean = false,
    val tokens: Int = 0,
)

data class MemoryStats(
    val modelBytes: Long,
    val nCtx: Int,
    val kvTypeK: String,
    val kvTypeV: String,
    val threads: Int,
    val gpuLayers: Int,
    val gpuOffloadSupported: Boolean,
    val rssBytes: Long = 0,
    val rssLimitBytes: Long = 0,
    val rssOverLimit: Boolean = false,
)

data class BackendInfo(
    val backend: String,
    val nCtx: Int,
    val typeK: String,
    val typeV: String,
    val threads: Int,
    val gpuLayers: Int,
)
