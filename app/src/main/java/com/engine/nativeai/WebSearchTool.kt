package com.engine.nativeai

/**
 * Search provider abstraction (spec §13): no brittle HTML scraping in the
 * agent. Providers plug in behind this interface; the local fallback returns
 * nothing so the agent degrades gracefully without network keys.
 */
interface SearchProvider {
    suspend fun search(query: String): String
}

class LocalFallbackProvider : SearchProvider {
    override suspend fun search(query: String): String =
        "web search unavailable (no provider configured)"
}

class WebSearchTool(private val provider: SearchProvider) : AgentTool {
    override val name = "web_search"
    override val description =
        "Search the web for up-to-date information. Input: a query string."

    override suspend fun execute(input: String): ToolOutput {
        val result = provider.search(input)
        return ToolOutput(name, result, result.isNotBlank() && !result.startsWith("web search unavailable"))
    }
}
