package com.engine.nativeai

import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime provider configuration (spec: dynamic catalog). Holds base URLs
 * and in-memory-only API keys. Keys are never persisted, never logged, and
 * never included in the APK.
 */
class ProviderRegistry {
    private val baseUrls = ConcurrentHashMap<String, String>()
    private val apiKeys = ConcurrentHashMap<String, String>()
    private val lastRefreshes = ConcurrentHashMap<String, Long>()

    fun defaultBaseUrl(): String =
        baseUrls[ModelCatalog.ZEN_PROVIDER] ?: ModelCatalog.ZEN_BASE_URL

    fun baseUrl(provider: String): String? = baseUrls[provider]

    fun setBaseUrl(provider: String, url: String) {
        baseUrls[provider] = url.trimEnd('/')
    }

    fun apiKey(provider: String): String = apiKeys[provider] ?: ""

    fun setApiKey(provider: String, key: String) {
        if (key.isNotBlank()) apiKeys[provider] = key
    }

    fun lastRefresh(provider: String): Long = lastRefreshes[provider] ?: 0L

    fun setLastRefresh(provider: String, at: Long) {
        lastRefreshes[provider] = at
    }
}
