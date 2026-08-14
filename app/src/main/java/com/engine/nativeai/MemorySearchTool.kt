package com.engine.nativeai

class MemorySearchTool(private val memory: MemoryDatabase) : AgentTool {
    override val name = "memory_search"
    override val description =
        "Search the local memory of past experiences and facts. Input: a short query."

    override suspend fun execute(input: String): ToolResult {
        val context = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            memory.searchContext(input, topK = 3)
        }
        return if (context.isBlank()) {
            ToolResult(name, "no relevant memories found", false, null)
        } else {
            ToolResult(name, context, true)
        }
    }
}
