package com.engine.nativeai

/**
 * Execution-time consent boundary (golden UX): the agent suspends on a tool
 * call that needs approval; the user (or a persisted "always allow" rule)
 * decides. The executor never decides for the user.
 */
data class ToolApprovalRequest(
    val tool: String,
    val input: String,
    val permission: ToolPermission,
)

enum class ApprovalDecision {
    ALLOW_ONCE,
    ALWAYS_ALLOW,
    DENY,
}
