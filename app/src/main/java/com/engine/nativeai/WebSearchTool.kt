package com.engine.nativeai

/**
 * Search provider abstraction (spec §13): no brittle HTML scraping in the
 * agent. Providers plug in behind this interface; the local fallback returns
 * nothing so the agent degrades gracefully without network keys.
 */
interface SearchProvider {
    /** True when a real search backend is configured (key/endpoint present).
     *  The web_search tool reports UNAVAILABLE/NOT CONFIGURED until then —
     *  no fake capabilities (golden spec §30). */
    val configured: Boolean
        get() = true

    /** @return result text plus an ok flag; ok=false when no provider answered */
    suspend fun search(query: String): SearchResult
}

data class SearchResult(val text: String, val ok: Boolean)

class LocalFallbackProvider : SearchProvider {
    override val configured: Boolean = false

    override suspend fun search(query: String): SearchResult =
        SearchResult("web search unavailable (no provider configured)", ok = false)
}

class WebSearchTool(private val provider: SearchProvider) : AgentTool {
    override val name = "web_search"
    override val description =
        "Search the web for up-to-date information. Input: a query string."

    /** Honest availability: only a configured provider exposes the tool.
     *  The local fallback keeps execute() degrading gracefully for safety. */
    override val available: Boolean get() = provider.configured
    override val unavailableReason: String?
        get() = if (provider.configured) null else "no search provider configured"

    override suspend fun execute(input: String): ToolOutput {
        val result = provider.search(input)
        return ToolOutput(name, result.text, result.ok && result.text.isNotBlank())
    }
}
