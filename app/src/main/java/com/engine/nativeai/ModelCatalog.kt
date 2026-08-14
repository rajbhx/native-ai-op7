package com.engine.nativeai

/**
 * Built-in catalog seeds (spec §4). These are *refreshable* examples from the
 * OpenCode Zen free catalog — never a permanent hard-coded list. Discovery
 * (ModelDiscoveryService) replaces/updates them from the live endpoint;
 * availability and capabilities stay UNKNOWN until actually checked.
 */
object ModelCatalog {

    /** OpenCode Zen OpenAI-compatible base URL (public models list). */
    const val ZEN_BASE_URL = "https://opencode.ai/zen/v1"

    /** Provider id used for OpenCode Zen-compatible models. */
    const val ZEN_PROVIDER = "opencode-zen"

    fun freeRemoteSeeds(): List<ModelDescriptor> = listOf(
        zenSeed("big-pickle", "Big Pickle"),
        zenSeed("deepseek-v4-flash-free", "DeepSeek V4 Flash Free"),
        zenSeed("mimo-v2.5-free", "MiMo-V2.5 Free"),
        zenSeed("laguna-s-2.1-free", "Laguna S 2.1 Free"),
        zenSeed("ling-3.0-flash-free", "Ling-3.0-flash Free"),
        zenSeed("north-mini-code-free", "North Mini Code Free"),
        zenSeed("nemotron-3-ultra-free", "Nemotron 3 Ultra Free"),
    )

    private fun zenSeed(id: String, displayName: String) = ModelDescriptor(
        id = id,
        displayName = displayName,
        provider = ZEN_PROVIDER,
        endpoint = ZEN_BASE_URL,
        modelType = "chat",
        kind = ModelKind.REMOTE,
        costTier = ModelCostTier.FREE,
        availability = ModelAvailability.UNKNOWN,
        contextLength = null,
        maxOutputTokens = 256,
        supportsStreaming = true,
        supportsTools = false,
        supportsVision = false,
        supportsReasoning = false,
        supportsStructuredOutput = true,
        supportsEmbeddings = false,
        codingScore = null,
        reasoningScore = null,
        speedScore = null,
        reliabilityScore = null,
        mutable = true,
    )
}
