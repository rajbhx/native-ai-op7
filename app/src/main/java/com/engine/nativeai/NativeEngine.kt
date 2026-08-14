package com.engine.nativeai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * JNI bridge to llama.cpp (pinned submodule commit b10428). All native calls
 * run on Dispatchers.IO. Draft baseline: streaming (Flow<String>) and
 * ModelManager arrive in Phase 1 proper (docs/GOLD-STANDARD-SPEC.md).
 */
class NativeEngine : AutoCloseable {
    private var loaded = false

    init {
        System.loadLibrary("native-lib")
    }

    suspend fun init(config: EngineConfig): Unit = withContext(Dispatchers.IO) {
        require(!loaded) { "engine already initialized" }
        loaded = nativeInit(
            config.modelPath,
            config.threads,
            config.gpuLayers,
            config.contextSize,
        )
        check(loaded) { "nativeInit failed for ${config.modelPath}" }
    }

    suspend fun generate(prompt: String, maxTokens: Int = 128): InferenceResult =
        withContext(Dispatchers.IO) {
            check(loaded) { "engine not initialized" }
            val json = JSONObject(nativeGenerate(prompt, maxTokens))
            InferenceResult(
                text = json.optString("text"),
                cancelled = json.optBoolean("cancelled"),
                tokens = json.optInt("tokens"),
            )
        }

    /** Requests cancellation; the next sampled token stops generation. */
    fun cancel() {
        nativeCancel()
    }

    suspend fun memoryStats(): MemoryStats = withContext(Dispatchers.IO) {
        check(loaded) { "engine not initialized" }
        val j = JSONObject(nativeGetMemoryStats())
        MemoryStats(
            modelBytes = j.optLong("model_bytes"),
            nCtx = j.optInt("n_ctx"),
            kvTypeK = j.optString("kv_type_k"),
            kvTypeV = j.optString("kv_type_v"),
            threads = j.optInt("threads"),
            gpuLayers = j.optInt("gpu_layers"),
            gpuOffloadSupported = j.optBoolean("gpu_offload_supported"),
        )
    }

    suspend fun backendInfo(): BackendInfo = withContext(Dispatchers.IO) {
        check(loaded) { "engine not initialized" }
        val j = JSONObject(nativeGetBackendInfo())
        BackendInfo(
            backend = j.optString("backend"),
            nCtx = j.optInt("n_ctx"),
            typeK = j.optString("type_k"),
            typeV = j.optString("type_v"),
            threads = j.optInt("threads"),
            gpuLayers = j.optInt("gpu_layers"),
        )
    }

    override fun close() {
        if (loaded) {
            nativeUnload()
            loaded = false
        }
    }

    private external fun nativeInit(modelPath: String, threads: Int, gpuLayers: Int, nCtx: Int): Boolean
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
    private external fun nativeCancel()
    private external fun nativeGetMemoryStats(): String
    private external fun nativeGetBackendInfo(): String
    private external fun nativeUnload()
}
