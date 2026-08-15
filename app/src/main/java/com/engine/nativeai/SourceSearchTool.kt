package com.engine.nativeai

/** Agent tool over the local source knowledge base (roadmap Phase 6). */
class SourceSearchTool(private val search: SourceSearch) : AgentTool {
    override val name = "source_search"
    override val description =
        "Search the locally indexed source knowledge base (repos, docs, web pages). Input: a short query."

    override val permission: ToolPermission
        get() = ToolPermission.READ_ONLY

    override suspend fun execute(input: String): ToolOutput {
        val hits = search.search(input.trim(), 5)
        if (hits.isEmpty()) return ToolOutput(name, "no source matches found", false, null)
        val text = hits.joinToString("\n\n") {
            "[${it.sourceTitle} / ${it.filePath}] ${it.content.take(400)}"
        }
        return ToolOutput(name, text.take(2000), true)
    }
}
