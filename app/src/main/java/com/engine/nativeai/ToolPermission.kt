package com.engine.nativeai

/**
 * Tool permission levels (master prompt §21). The model can never bypass
 * these checks — ToolExecutor enforces them before execution.
 */
enum class ToolPermission {
    READ_ONLY,
    SAFE,
    REQUIRES_APPROVAL,
    PRIVILEGED,
}

/** Runtime permission policy. Default allows READ_ONLY + SAFE tools only. */
data class PermissionPolicy(
    val allowedMax: ToolPermission = ToolPermission.SAFE,
)

class PermissionManager(private val policy: PermissionPolicy = PermissionPolicy()) {

    fun canExecute(permission: ToolPermission): Boolean =
        permission.ordinal <= policy.allowedMax.ordinal

    fun denialReason(toolName: String, permission: ToolPermission): String =
        "permission denied: '$toolName' requires $permission (policy allows up to ${policy.allowedMax})"
}
