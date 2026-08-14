package com.engine.nativeai

import org.json.JSONObject

/**
 * Provider-neutral model metadata (spec §5). Capabilities are declared by the
 * provider/adapter, never guessed; missing metadata stays null/"Unknown".
 */
data class ModelDescriptor(
    val id: String,
    val displayName: String,
    val provider: String,
    val endpoint: String,
    val modelType: String,
    val kind: ModelKind,
    val costTier: ModelCostTier,
    val availability: ModelAvailability,
    val contextLength: Int = 2048,
    val maxOutputTokens: Int = 256,
    val supportsStreaming: Boolean = true,
    val supportsTools: Boolean = false,
    val supportsVision: Boolean = false,
    val supportsReasoning: Boolean = false,
    val supportsStructuredOutput: Boolean = true,
    val supportsEmbeddings: Boolean = false,
    val codingScore: Int? = null,
    val reasoningScore: Int? = null,
    val speedScore: Int? = null,
    val reliabilityScore: Int? = null,
    val mutable: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("display_name", displayName)
        put("provider", provider)
        put("endpoint", endpoint)
        put("model_type", modelType)
        put("kind", kind.name)
        put("cost_tier", costTier.name)
        put("availability", availability.name)
        put("context_length", contextLength)
        put("max_output_tokens", maxOutputTokens)
        put("supports_streaming", supportsStreaming)
        put("supports_tools", supportsTools)
        put("supports_vision", supportsVision)
        put("supports_reasoning", supportsReasoning)
        put("supports_structured_output", supportsStructuredOutput)
        put("supports_embeddings", supportsEmbeddings)
        put("coding_score", codingScore ?: JSONObject.NULL)
        put("reasoning_score", reasoningScore ?: JSONObject.NULL)
        put("speed_score", speedScore ?: JSONObject.NULL)
        put("reliability_score", reliabilityScore ?: JSONObject.NULL)
        put("mutable", mutable)
    }

    companion object {
        fun fromJson(j: JSONObject): ModelDescriptor = ModelDescriptor(
            id = j.getString("id"),
            displayName = j.optString("display_name", j.getString("id")),
            provider = j.optString("provider", ""),
            endpoint = j.optString("endpoint", ""),
            modelType = j.optString("model_type", "chat"),
            kind = runCatching { ModelKind.valueOf(j.getString("kind")) }.getOrDefault(ModelKind.REMOTE),
            costTier = runCatching { ModelCostTier.valueOf(j.getString("cost_tier")) }.getOrDefault(ModelCostTier.UNKNOWN),
            availability = runCatching { ModelAvailability.valueOf(j.getString("availability")) }
                .getOrDefault(ModelAvailability.UNKNOWN),
            contextLength = j.optInt("context_length", 2048),
            maxOutputTokens = j.optInt("max_output_tokens", 256),
            supportsStreaming = j.optBoolean("supports_streaming", true),
            supportsTools = j.optBoolean("supports_tools", false),
            supportsVision = j.optBoolean("supports_vision", false),
            supportsReasoning = j.optBoolean("supports_reasoning", false),
            supportsStructuredOutput = j.optBoolean("supports_structured_output", true),
            supportsEmbeddings = j.optBoolean("supports_embeddings", false),
            codingScore = if (j.isNull("coding_score")) null else j.optInt("coding_score", -1).let { if (it < 0) null else it },
            reasoningScore = if (j.isNull("reasoning_score")) null else j.optInt("reasoning_score", -1).let { if (it < 0) null else it },
            speedScore = if (j.isNull("speed_score")) null else j.optInt("speed_score", -1).let { if (it < 0) null else it },
            reliabilityScore = if (j.isNull("reliability_score")) null else j.optInt("reliability_score", -1).let { if (it < 0) null else it },
            mutable = j.optBoolean("mutable", true),
        )
    }
}
