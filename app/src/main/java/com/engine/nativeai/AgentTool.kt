package com.engine.nativeai

/** Result of one bounded tool execution (spec §12). */
data class ToolOutput(
    val toolName: String,
    val output: String,
    val ok: Boolean,
    val error: String? = null,
    val durationMs: Long = 0,
)

/** Pluggable agent tool (spec §12): name, description, bounded execution. */
interface AgentTool {
    val name: String
    val description: String
    val permission: ToolPermission
        get() = ToolPermission.SAFE
    suspend fun execute(input: String): ToolOutput
}
