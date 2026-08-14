package com.engine.nativeai

data class ToolResult(
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
    suspend fun execute(input: String): ToolResult
}
