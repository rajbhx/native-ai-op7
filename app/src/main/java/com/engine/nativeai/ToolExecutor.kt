package com.engine.nativeai

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Enforces the spec §12/§22 safety envelope for every tool call:
 * timeout, input validation, output limits, error handling, logging,
 * cancellation. Never trust model-chosen tool inputs.
 */
class ToolExecutor(
    private val registry: ToolRegistry,
    private val memory: MemoryDatabase? = null,
    private val maxInputLength: Int = 500,
    private val maxOutputLength: Int = 2000,
    private val timeoutMs: Long = 15_000,
) {
    suspend fun execute(name: String, input: String): ToolResult {
        val tool = registry[name]
            ?: return ToolResult(name, "", false, "unknown tool: $name")
        if (input.length > maxInputLength) {
            logTool(name, input, "input too long", false)
            return ToolResult(name, "", false, "input too long (${input.length} chars)")
        }
        val started = System.currentTimeMillis()
        return try {
            val result = withTimeout(timeoutMs) { tool.execute(input) }
            val output = result.output.take(maxOutputLength)
            logTool(name, input, output, result.ok)
            result.copy(output = output, durationMs = System.currentTimeMillis() - started)
        } catch (e: TimeoutCancellationException) {
            logTool(name, input, "timeout", false)
            ToolResult(name, "", false, "tool timeout after ${timeoutMs}ms", durationMs = timeoutMs)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logTool(name, input, e.message ?: "tool error", false)
            ToolResult(name, "", false, e.message ?: "tool error",
                durationMs = System.currentTimeMillis() - started)
        }
    }

    private fun logTool(name: String, input: String, output: String, ok: Boolean) {
        memory?.storeToolResult(name, input.hashCode().toString(), output.take(500), ok)
    }
}
