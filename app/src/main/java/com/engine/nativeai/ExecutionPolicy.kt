package com.engine.nativeai

/** Execution decision for one command. */
enum class ExecutionStatus { ALLOWED, DENIED, UNAVAILABLE }

/**
 * Terminal execution policy, separate from ToolPermission. The agent may
 * request execution but the policy decides. Default: fully restricted —
 * nothing runs until explicitly allowlisted.
 */
data class ExecutionPolicy(
    private val allowList: Set<String> = emptySet(),
) {
    fun allows(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return false
        return allowList.any { trimmed == it || trimmed.startsWith("$it ") }
    }

    fun status(command: String): ExecutionStatus =
        if (allows(command)) ExecutionStatus.ALLOWED else ExecutionStatus.DENIED
}
