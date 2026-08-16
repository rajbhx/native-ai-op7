package com.engine.nativeai

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Fake tool that records whether it actually executed. */
private class RecordingTool(
    override val name: String,
    override val permission: ToolPermission,
    val result: String = "ok",
    val delayMs: Long = 0,
) : AgentTool {
    var executed = false
    override val description: String = "test tool"
    override suspend fun execute(input: String): ToolOutput {
        executed = true
        if (delayMs > 0) delay(delayMs)
        return ToolOutput(name, result, true)
    }
}

/** Approval gate tests (golden UX P0: in-loop consent). */
class ToolExecutorTest {

    @Test
    fun approvalRequiredToolDeniedByDefault() = runTest {
        val tool = RecordingTool("terminal", ToolPermission.REQUIRES_APPROVAL)
        val registry = ToolRegistry().apply { register(tool) }
        val executor = ToolExecutor(registry, memory = null)
        val out = executor.execute("terminal", "ls")
        assertFalse(out.ok)
        assertTrue(out.error?.contains("permission denied") == true)
        assertFalse("tool must not run without approval", tool.executed)
    }

    @Test
    fun allowOnceProceedsAndRunsTool() = runTest {
        val tool = RecordingTool("terminal", ToolPermission.REQUIRES_APPROVAL)
        val registry = ToolRegistry().apply { register(tool) }
        var requests = 0
        var lastRequest: ToolApprovalRequest? = null
        val executor = ToolExecutor(
            registry,
            memory = null,
            onApproval = { req ->
                requests++
                lastRequest = req
                ApprovalDecision.ALLOW_ONCE
            },
        )
        val out = executor.execute("terminal", "echo hi")
        assertTrue(out.ok)
        assertTrue(tool.executed)
        assertEquals(1, requests)
        assertEquals("terminal", lastRequest?.tool)
        assertEquals("echo hi", lastRequest?.input)
    }

    @Test
    fun denyStopsExecutionAndDoesNotRunTool() = runTest {
        val tool = RecordingTool("terminal", ToolPermission.REQUIRES_APPROVAL)
        val registry = ToolRegistry().apply { register(tool) }
        val executor = ToolExecutor(
            registry,
            memory = null,
            onApproval = { ApprovalDecision.DENY },
        )
        val out = executor.execute("terminal", "rm -rf /")
        assertFalse(out.ok)
        assertFalse(tool.executed)
    }

    @Test
    fun privilegedToolNeverBypassedEvenWhenApproved() = runTest {
        val tool = RecordingTool("shell", ToolPermission.PRIVILEGED)
        val registry = ToolRegistry().apply { register(tool) }
        // An approval callback that would say yes must still be ignored:
        // PRIVILEGED is policy-blocked and the model can never bypass it.
        val executor = ToolExecutor(
            registry,
            memory = null,
            onApproval = { ApprovalDecision.ALWAYS_ALLOW },
        )
        val out = executor.execute("shell", "id")
        assertFalse(out.ok)
        assertFalse(tool.executed)
    }

    @Test
    fun safeToolRunsWithoutApprovalGate() = runTest {
        val tool = RecordingTool("calculator", ToolPermission.SAFE)
        val registry = ToolRegistry().apply { register(tool) }
        var gates = 0
        val executor = ToolExecutor(
            registry,
            memory = null,
            onApproval = { req ->
                gates++
                ApprovalDecision.ALLOW_ONCE
            },
        )
        val out = executor.execute("calculator", "2+2")
        assertTrue(out.ok)
        assertTrue(tool.executed)
        assertEquals("safe tools never hit the approval gate", 0, gates)
    }

    @Test
    fun cancellationPropagatesNotSwallowed() = runTest {
        val tool = RecordingTool("terminal", ToolPermission.REQUIRES_APPROVAL, delayMs = 10_000)
        val registry = ToolRegistry().apply { register(tool) }
        val executor = ToolExecutor(
            registry,
            memory = null,
            onApproval = { ApprovalDecision.ALLOW_ONCE },
        )
        val job = async { executor.execute("terminal", "slow") }
        delay(50)
        job.cancel()
        try {
            job.await()
            fail("cancellation must propagate, not be swallowed")
        } catch (e: CancellationException) {
            assertTrue("expected cancellation", true)
        }
    }

    @Test
    fun timeoutReturnsStructuredError() = runTest {
        val tool = RecordingTool("terminal", ToolPermission.REQUIRES_APPROVAL, delayMs = 5_000)
        val registry = ToolRegistry().apply { register(tool) }
        val executor = ToolExecutor(
            registry,
            memory = null,
            onApproval = { ApprovalDecision.ALLOW_ONCE },
            timeoutMs = 100,
        )
        val out = executor.execute("terminal", "sleep")
        assertFalse(out.ok)
        assertTrue(out.error?.contains("timeout") == true)
    }
}
