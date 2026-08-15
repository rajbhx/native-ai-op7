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
        zenDescriptor("big-pickle", "Big Pickle"),
        zenDescriptor("deepseek-v4-flash-free", "DeepSeek V4 Flash Free"),
        zenDescriptor("mimo-v2.5-free", "MiMo-V2.5 Free"),
        zenDescriptor("laguna-s-2.1-free", "Laguna S 2.1 Free"),
        zenDescriptor("ling-3.0-flash-free", "Ling-3.0-flash Free"),
        zenDescriptor("north-mini-code-free", "North Mini Code Free"),
        zenDescriptor("nemotron-3-ultra-free", "Nemotron 3 Ultra Free"),
    )

    /** Public descriptor builder for any discovered or persisted Zen model id. */
    fun zenDescriptor(id: String, displayName: String? = null) = ModelDescriptor(
        id = id,
        displayName = displayName ?: ModelDiscoveryService.prettify(id),
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
