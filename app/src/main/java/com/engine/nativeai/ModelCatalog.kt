package com.engine.nativeai

/**
 * Built-in catalog seeds (spec §4). These are *examples*, fully mutable and
 * removable; they are disabled (no provider registered) until the user
 * configures an endpoint/key at runtime. Capabilities are never fabricated:
 * unknown metadata stays UNKNOWN/false.
 */
object ModelCatalog {

    fun freeRemoteSeeds(): List<ModelDescriptor> = listOf(
        ModelDescriptor(
            id = "deepseek-chat",
            displayName = "DeepSeek (free tier)",
            provider = "openai-compatible",
            endpoint = "https://api.deepseek.com/v1",
            modelType = "chat",
            kind = ModelKind.REMOTE,
            costTier = ModelCostTier.FREE,
            availability = ModelAvailability.UNKNOWN,
            contextLength = 64_000,
            maxOutputTokens = 4096,
            supportsStreaming = true,
            supportsTools = false,
            supportsReasoning = false,
            supportsStructuredOutput = true,
            codingScore = 7,
            reasoningScore = 7,
            speedScore = 6,
            reliabilityScore = 5,
        ),
        ModelDescriptor(
            id = "openrouter-auto",
            displayName = "OpenRouter (free models)",
            provider = "openai-compatible",
            endpoint = "https://openrouter.ai/api/v1",
            modelType = "chat",
            kind = ModelKind.REMOTE,
            costTier = ModelCostTier.UNKNOWN,
            availability = ModelAvailability.UNKNOWN,
            contextLength = 32_000,
            maxOutputTokens = 2048,
            supportsStreaming = true,
            supportsTools = true,
            supportsStructuredOutput = true,
            codingScore = 6,
            reasoningScore = 6,
            speedScore = 7,
            reliabilityScore = 6,
        ),
    )
}
