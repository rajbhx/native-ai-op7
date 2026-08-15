package com.engine.nativeai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local llama.cpp backend behind the provider interface (spec §2). Offline,
 * private, free; stays usable when the network or remote providers fail.
 */
class LocalModelProvider(
    private val engine: NativeEngine,
    private val config: EngineConfig,
) : ModelProvider {

    companion object {
        const val LOCAL_MODEL_ID = "local-llama"
    }

    override val descriptor = ModelDescriptor(
        id = LOCAL_MODEL_ID,
        displayName = "Local llama.cpp (OP7)",
        provider = "local",
        endpoint = "native://llama.cpp",
        modelType = "chat",
        kind = ModelKind.LOCAL,
        costTier = ModelCostTier.FREE,
        availability = ModelAvailability.AVAILABLE,
        contextLength = config.contextSize,
        maxOutputTokens = config.maxTokens,
        supportsStreaming = true,
        supportsTools = false, // ReAct text actions work, native tool calls do not
        supportsStructuredOutput = true,
        mutable = false,
    )

    override fun stream(request: ModelRequest): Flow<ModelStreamEvent> {
        val full = buildPrompt(request)
        return engine.generateStream(
            full,
            GenerationConfig(
                maxTokens = request.maxTokens,
                temperature = request.temperature,
                stopSequences = request.stopSequences,
            ),
        ).map { ModelStreamEvent.Token(it) as ModelStreamEvent }
    }

    override suspend fun complete(request: ModelRequest): ModelResult {
        val started = System.currentTimeMillis()
        val full = buildPrompt(request)
        val result = engine.generate(full, request.maxTokens)
        return ModelResult(
            text = result.text,
            tokens = result.tokens,
            durationMs = System.currentTimeMillis() - started,
            providerId = descriptor.id,
            cancelled = result.cancelled,
        )
    }

    override suspend fun health(): ProviderHealth {
        if (!engine.isLoaded) {
            return ProviderHealth(false, 0, "model not loaded")
        }
        return try {
            val started = System.currentTimeMillis()
            engine.memoryStats()
            ProviderHealth(true, System.currentTimeMillis() - started, "local engine ready")
        } catch (e: Exception) {
            ProviderHealth(false, 0, e.message ?: "local engine error")
        }
    }

    private fun buildPrompt(request: ModelRequest): String =
        if (request.system.isBlank()) request.prompt
        else request.system.trim() + "\n\n" + request.prompt
}
