package com.engine.nativeai

class MemorySearchTool(private val memory: MemoryDatabase) : AgentTool {
    override val name = "memory_search"
    override val description =
        "Search the local memory of past experiences and facts. Input: a short query."

    override suspend fun execute(input: String): ToolOutput {
        val context = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            memory.searchContext(input, topK = 3)
        }
        return if (context.isBlank()) {
            ToolOutput(name, "no relevant memories found", false, null)
        } else {
            ToolOutput(name, context, true)
        }
    }
}
