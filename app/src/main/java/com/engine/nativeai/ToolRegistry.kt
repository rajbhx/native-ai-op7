package com.engine.nativeai

/** UI-facing tool inventory entry (core-hardening C1). */
data class ToolDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val permission: ToolPermission,
    val available: Boolean,
    val enabled: Boolean,
) {
    val riskLevel: String
        get() = when (permission) {
            ToolPermission.READ_ONLY, ToolPermission.SAFE -> "low"
            ToolPermission.REQUIRES_APPROVAL -> "medium"
            ToolPermission.PRIVILEGED -> "high"
        }
}

class ToolRegistry {
    private val tools = LinkedHashMap<String, AgentTool>()

    fun register(tool: AgentTool) {
        tools[tool.name] = tool
    }

    operator fun get(name: String): AgentTool? = tools[name]

    fun available(name: String): Boolean = tools[name]?.available ?: false

    /** Live inventory for the UI — never fabricates state. */
    fun snapshot(): List<ToolDescriptor> =
        tools.values.map { t ->
            ToolDescriptor(
                id = t.name,
                name = t.name,
                description = t.description,
                permission = t.permission,
                available = t.available,
                enabled = t.enabled,
            )
        }

    /** Descriptions used to build the agent's tool prompt: available only. */
    fun descriptions(): String =
        tools.values.filter { it.available }
            .joinToString("\n") { "- ${it.name}: ${it.description}" }
}
