package com.engine.nativeai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dynamic model discovery (spec: dynamic catalog). Fetches an
 * OpenAI-compatible GET /models list (OpenCode Zen by default) and upserts
 * descriptors into the ModelRegistry. Capabilities are never fabricated:
 * unknown metadata stays UNKNOWN/null. The result is cached by the registry
 * and surfaced to the UI as "Last updated".
 */
class ModelDiscoveryService(
    private val registry: ModelRegistry,
    private val providerRegistry: ProviderRegistry,
    private val connectTimeoutMs: Int = 10_000,
) {
    data class DiscoveryResult(
        val found: Int,
        val endpoint: String,
        val lastUpdated: Long,
        val error: String? = null,
    )

    suspend fun refresh(baseUrl: String? = null): DiscoveryResult =
        withContext(Dispatchers.IO) {
            val endpoint = (baseUrl ?: providerRegistry.defaultBaseUrl()).trimEnd('/')
            val conn = try {
                (URL("$endpoint/models").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = connectTimeoutMs
                    readTimeout = connectTimeoutMs
                }
            } catch (e: Exception) {
                return@withContext DiscoveryResult(
                    0, endpoint, providerRegistry.lastRefresh(ModelCatalog.ZEN_PROVIDER), e.message,
                )
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: "HTTP $code"
                    return@withContext DiscoveryResult(
                        0, endpoint, providerRegistry.lastRefresh(ModelCatalog.ZEN_PROVIDER), err.take(200),
                    )
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val data = json.optJSONArray("data")
                    ?: return@withContext DiscoveryResult(
                        0, endpoint, 0, "response has no data[]",
                    )
                val now = System.currentTimeMillis()
                val discovered = mutableListOf<ModelDescriptor>()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.optString("id").trim().takeIf { it.isNotEmpty() } ?: continue
                    val existing = registry.get(id)
                    discovered += ModelDescriptor(
                        id = id,
                        displayName = existing?.displayName ?: prettify(id),
                        provider = existing?.provider ?: ModelCatalog.ZEN_PROVIDER,
                        endpoint = existing?.endpoint?.takeIf { it.isNotBlank() }
                            ?: endpoint,
                        modelType = existing?.modelType ?: "chat",
                        kind = ModelKind.REMOTE,
                        costTier = costTierFor(id, existing),
                        availability = ModelAvailability.UNKNOWN,
                        contextLength = existing?.contextLength,
                        maxOutputTokens = existing?.maxOutputTokens ?: 256,
                        supportsStreaming = existing?.supportsStreaming ?: true,
                        supportsTools = existing?.supportsTools ?: false,
                        supportsVision = existing?.supportsVision ?: false,
                        supportsReasoning = existing?.supportsReasoning ?: false,
                        supportsStructuredOutput = existing?.supportsStructuredOutput ?: true,
                        supportsEmbeddings = existing?.supportsEmbeddings ?: false,
                        codingScore = existing?.codingScore,
                        reasoningScore = existing?.reasoningScore,
                        speedScore = existing?.speedScore,
                        reliabilityScore = existing?.reliabilityScore,
                        mutable = true,
                        lastUpdated = now,
                    )
                }
                val upserted = registry.upsertRemote(discovered)
                registry.saveCatalog() // best-effort cache for offline restarts
                providerRegistry.setLastRefresh(ModelCatalog.ZEN_PROVIDER, now)
                DiscoveryResult(upserted, endpoint, now)
            } catch (e: Exception) {
                DiscoveryResult(
                    0, endpoint, providerRegistry.lastRefresh(ModelCatalog.ZEN_PROVIDER), e.message,
                )
            } finally {
                conn.disconnect()
            }
        }

    companion object {
        /** "big-pickle" -> "Big Pickle", "deepseek-v4-flash-free" -> "Deepseek V4 Flash Free". */
        fun prettify(id: String): String =
            id.split('-', '_').joinToString(" ") { part ->
                part.replaceFirstChar { it.uppercase() }
            }

        /**
         * Cost tier heuristic: the OpenCode Zen catalog marks free models
         * with the "-free" suffix (big-pickle is the current free flagship).
         * Existing registry metadata wins over the heuristic so a provider
         * change is respected; everything else stays UNKNOWN.
         */
        fun costTierFor(id: String, existing: ModelDescriptor?): ModelCostTier =
            existing?.costTier?.takeIf { it != ModelCostTier.UNKNOWN }
                ?: if (id.endsWith("-free") || id == "big-pickle") ModelCostTier.FREE
                else ModelCostTier.UNKNOWN
    }
}
