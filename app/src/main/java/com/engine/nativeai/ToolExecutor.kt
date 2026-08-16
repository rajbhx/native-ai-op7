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
    private val permissionManager: PermissionManager = PermissionManager(),
    private val onApproval: suspend (ToolApprovalRequest) -> ApprovalDecision =
        { ApprovalDecision.DENY },
    private val maxInputLength: Int = 500,
    private val maxOutputLength: Int = 2000,
    private val timeoutMs: Long = 15_000,
) {
    suspend fun execute(name: String, input: String): ToolOutput {
        val tool = registry[name]
            ?: return ToolOutput(name, "", false, "unknown tool: $name")
        if (!tool.available) {
            logTool(name, input, "", "tool unavailable", false)
            return ToolOutput(name, "", false, "tool unavailable")
        }
        if (!permissionManager.canExecute(tool.permission)) {
            // Approval gate (golden UX): REQUIRES_APPROVAL tools may be
            // granted at execution time through the injected callback
            // (default: deny, so behavior is unchanged without a callback).
            // PRIVILEGED stays policy-blocked — the model can never bypass it.
            val decision = if (tool.permission == ToolPermission.REQUIRES_APPROVAL) {
                onApproval(ToolApprovalRequest(name, input.take(maxInputLength), tool.permission))
            } else {
                ApprovalDecision.DENY
            }
            if (decision == ApprovalDecision.DENY) {
                logTool(name, input, "", permissionManager.denialReason(name, tool.permission), false)
                return ToolOutput(name, "", false, permissionManager.denialReason(name, tool.permission))
            }
        }
        if (input.length > maxInputLength) {
            logTool(name, input, "", "input too long (${input.length} chars)", false)
            return ToolOutput(name, "", false, "input too long (${input.length} chars)")
        }
        val started = System.currentTimeMillis()
        return try {
            val result = withTimeout(timeoutMs) { tool.execute(input) }
            val output = result.output.take(maxOutputLength)
            logTool(name, input, output, result.error, result.ok)
            result.copy(output = output, durationMs = System.currentTimeMillis() - started)
        } catch (e: TimeoutCancellationException) {
            logTool(name, input, "", "tool timeout after ${timeoutMs}ms", false)
            ToolOutput(name, "", false, "tool timeout after ${timeoutMs}ms", durationMs = timeoutMs)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            logTool(name, input, "", e.message ?: "tool error", false)
            ToolOutput(name, "", false, e.message ?: "tool error",
                durationMs = System.currentTimeMillis() - started)
        }
    }

    private fun logTool(name: String, input: String, output: String, error: String?, ok: Boolean) {
        memory?.storeToolResult(name, input.take(500), output.take(500), error?.take(300), ok)
    }
}
