package com.engine.nativeai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionManagerTest {
    @Test
    fun safePolicyAllowsReadOnlyAndSafe() {
        val pm = PermissionManager(PermissionPolicy(allowedMax = ToolPermission.SAFE))
        assertTrue(pm.canExecute(ToolPermission.READ_ONLY))
        assertTrue(pm.canExecute(ToolPermission.SAFE))
        assertFalse(pm.canExecute(ToolPermission.REQUIRES_APPROVAL))
        assertFalse(pm.canExecute(ToolPermission.PRIVILEGED))
    }

    @Test
    fun privilegedPolicyAllowsEverything() {
        val pm = PermissionManager(PermissionPolicy(allowedMax = ToolPermission.PRIVILEGED))
        assertTrue(pm.canExecute(ToolPermission.PRIVILEGED))
    }

    @Test
    fun denialReasonIsExplicit() {
        val pm = PermissionManager()
        assertTrue(pm.denialReason("file_write", ToolPermission.REQUIRES_APPROVAL)
            .contains("permission denied"))
    }
}
