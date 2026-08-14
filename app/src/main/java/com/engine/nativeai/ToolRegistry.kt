package com.engine.nativeai

class ToolRegistry {
    private val tools = LinkedHashMap<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    operator fun get(name: String): AgentTool? = tools[name]

    fun descriptions(): String =
        tools.values.joinToString("\n") { "- ${it.name}: ${it.description}" }
}
