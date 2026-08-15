package com.engine.nativeai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionLayerTest {

    @Test
    fun localBackendRunsEchoAndCapturesStdout() = runBlocking {
        val r = LocalProcessBackend().execute(ExecutionRequest(command = "echo hello-op7"))
        assertEquals(0, r.exitCode)
        assertTrue(r.stdout.contains("hello-op7"))
        assertFalse(r.timedOut)
    }

    @Test
    fun localBackendReportsNonZeroExit() = runBlocking {
        val r = LocalProcessBackend().execute(ExecutionRequest(command = "exit 3"))
        assertEquals(3, r.exitCode)
    }

    @Test
    fun localBackendTimesOutAndDestroys() = runBlocking {
        val r = LocalProcessBackend().execute(
            ExecutionRequest(command = "sleep 30", timeoutMs = 500),
        )
        assertTrue(r.timedOut)
        assertTrue(r.exitCode != 0)
    }

    @Test
    fun policyDefaultsToDenyAll() {
        val policy = ExecutionPolicy()
        assertEquals(ExecutionStatus.DENIED, policy.status("ls -la"))
        assertEquals(ExecutionStatus.DENIED, policy.status(""))
    }

    @Test
    fun policyAllowsExactAllowlist() {
        val policy = ExecutionPolicy(allowList = setOf("ls", "echo"))
        assertEquals(ExecutionStatus.ALLOWED, policy.status("ls -la"))
        assertEquals(ExecutionStatus.ALLOWED, policy.status("echo hi"))
        assertEquals(ExecutionStatus.DENIED, policy.status("rm -rf /"))
        assertFalse(policy.allows("disallowed"))
    }

    @Test
    fun terminalToolDisabledByDefaultAndPolicyGuarded() = runBlocking {
        val tool = TerminalTool(LocalProcessBackend())
        assertFalse(tool.available)
        // Disabled: rejected before policy.
        val disabled = tool.execute("echo hi")
        assertFalse(disabled.ok)

        tool.setEnabled(true)
        assertTrue(tool.available)
        // Policy still denies un-allowlisted commands.
        val denied = tool.execute("rm -rf /")
        assertFalse(denied.ok)
        assertTrue(denied.output.contains("policy denied") || denied.error != null)
    }

    @Test
    fun terminalToolRunsAllowlistedCommand() = runBlocking {
        val tool = TerminalTool(
            backend = LocalProcessBackend(),
            policy = ExecutionPolicy(allowList = setOf("echo")),
        )
        tool.setEnabled(true)
        val ok = tool.execute("echo terminal-ok")
        assertTrue("expected ok, got: ${ok.error} ${ok.output}", ok.ok)
        assertTrue(ok.output.contains("terminal-ok"))
    }
}
