package com.engine.nativeai

class ToolRegistry {
    private val tools = LinkedHashMap<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    operator fun get(name: String): AgentTool? = tools[name]

    fun available(name: String): Boolean = tools[name]?.available ?: false

    /** Descriptions used to build the agent's tool prompt: available only. */
    fun descriptions(): String =
        tools.values.filter { it.available }
            .joinToString("\n") { "- ${it.name}: ${it.description}" }
}
