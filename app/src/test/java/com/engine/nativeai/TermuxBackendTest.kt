package com.engine.nativeai

import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deterministic bridge that simulates Termux writing result files. */
class FakeTermuxBridge(
    var installed: Boolean = true,
    var launchRejected: Boolean = false,
    var writeDelayMs: Long = 0,
) : TermuxBridge {
    var exitCode = 0
    var stdout = "termux-ok"
    var stderr = ""
    val launched = mutableListOf<String>()
    private val root = File.createTempFile("termux-backend-test", "").apply {
        delete()
        mkdirs()
    }

    override fun isInstalled(): Boolean = installed

    override fun launch(runId: String, wrappedCommand: String, workingDirectory: String?): Boolean {
        launched += runId
        if (launchRejected) return false
        if (runId.startsWith("kill-")) return true // kills never produce output
        val dir = resultDir(runId).also { it.mkdirs() }
        fun write() {
            runCatching {
                File(dir, "out.txt").writeText(stdout)
                File(dir, "err.txt").writeText(stderr)
                File(dir, "code.txt").writeText(exitCode.toString())
            }
        }
        if (writeDelayMs > 0) {
            kotlinx.coroutines.GlobalScope.launch { delay(writeDelayMs); write() }
        } else {
            write()
        }
        return true
    }

    override fun resultDir(runId: String): File = File(root, runId)
}

class TermuxBackendTest {

    @Test
    fun statusReportsNotInstalled() {
        val backend = TermuxBackend(FakeTermuxBridge(installed = false))
        assertEquals(TermuxStatus.NOT_INSTALLED, backend.status)
        assertFalse(backend.available)
    }

    @Test
    fun probeReturnsReadyWhenEchoWorks() = runBlocking {
        val backend = TermuxBackend(FakeTermuxBridge(), pollIntervalMs = 50)
        assertEquals(TermuxStatus.READY, backend.probe())
        assertTrue(backend.available)
    }

    @Test
    fun probeReturnsSetupRequiredWhenLaunchRejected() = runBlocking {
        val backend = TermuxBackend(FakeTermuxBridge(launchRejected = true), pollIntervalMs = 50)
        assertEquals(TermuxStatus.SETUP_REQUIRED, backend.probe())
        assertFalse(backend.available)
    }

    @Test
    fun probeReturnsSetupRequiredOnTimeout() = runBlocking {
        val backend = TermuxBackend(
            FakeTermuxBridge(writeDelayMs = 5_000),
            pollIntervalMs = 50,
            probeTimeoutMs = 300,
        )
        assertEquals(TermuxStatus.SETUP_REQUIRED, backend.probe())
    }

    @Test
    fun executeCapturesStdoutAndExit() = runBlocking {
        val bridge = FakeTermuxBridge().apply {
            stdout = "hello from termux"
            exitCode = 7
        }
        val backend = TermuxBackend(bridge, pollIntervalMs = 50)
        val r = backend.execute(ExecutionRequest(command = "echo hi", timeoutMs = 3_000))
        assertEquals(7, r.exitCode)
        assertTrue(r.stdout.contains("hello from termux"))
        assertFalse(r.timedOut)
        assertTrue(bridge.launched.isNotEmpty())
    }

    @Test
    fun executeTimesOutAndCleansUp() = runBlocking {
        val bridge = FakeTermuxBridge(writeDelayMs = 5_000)
        val backend = TermuxBackend(bridge, pollIntervalMs = 50)
        val r = backend.execute(ExecutionRequest(command = "sleep 5", timeoutMs = 400))
        assertTrue(r.timedOut)
        val leftovers = bridge.resultDir("x").parentFile?.listFiles()
            ?.filter { it.isDirectory } ?: emptyList()
        assertTrue("run dirs should be cleaned up: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun executeCancellationEndsPromptly() = runBlocking {
        val bridge = FakeTermuxBridge(writeDelayMs = 10_000)
        val backend = TermuxBackend(bridge, pollIntervalMs = 50)
        val job = launch {
            backend.execute(ExecutionRequest(command = "sleep 30", timeoutMs = 30_000))
        }
        delay(200)
        job.cancel()
        val finished = withTimeoutOrNull(2000) {
            job.join()
            true
        }
        assertEquals(true, finished)
    }
}
