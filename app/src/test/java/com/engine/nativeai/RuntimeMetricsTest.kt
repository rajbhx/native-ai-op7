package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeMetricsTest {

    @Test
    fun recordsModelLoadAndFirstTokenOnce() {
        val m = RuntimeMetrics()
        m.recordModelLoad(1200)
        m.recordFirstToken(340)
        m.recordFirstToken(999) // first wins

        val s = m.snapshot()
        assertEquals(1200L, s.modelLoadMs)
        assertEquals(340L, s.firstTokenMs)
    }

    @Test
    fun recordRunComputesTokensPerSec() {
        val m = RuntimeMetrics()
        m.recordRun(tokens = 40, durationMs = 20_000)

        val s = m.snapshot()
        assertEquals(40, s.lastRunTokens)
        assertEquals(20_000L, s.lastRunDurationMs)
        assertEquals(2.0, s.tokensPerSec!!, 0.001)
    }

    @Test
    fun recordToolAccumulatesCallsAndFailures() {
        val m = RuntimeMetrics()
        m.recordTool("calculator", 45, ok = true)
        m.recordTool("calculator", 60, ok = true)
        m.recordTool("web_search", 5000, ok = false)

        val tools = m.snapshot().tools
        assertEquals(2, tools["calculator"]?.calls)
        assertEquals(105L, tools["calculator"]?.totalMs)
        assertEquals(0, tools["calculator"]?.failures)
        assertEquals(1, tools["web_search"]?.failures)
    }

    @Test
    fun recordsErrorsRetriesRestartsAndFrames() {
        val m = RuntimeMetrics()
        m.recordError()
        m.recordRetry()
        m.recordServiceRestart()
        m.recordFrames(12, 2)

        val s = m.snapshot()
        assertEquals(1, s.errors)
        assertEquals(1, s.retries)
        assertEquals(1, s.serviceRestarts)
        assertEquals(12L, s.droppedFrames)
        assertEquals(2L, s.jankyFrames)
    }

    @Test
    fun resetClearsEverything() {
        val m = RuntimeMetrics()
        m.recordModelLoad(1)
        m.recordRun(10, 100)
        m.recordTool("x", 1, true)
        m.recordError()
        m.reset()

        val s = m.snapshot()
        assertNull(s.modelLoadMs)
        assertEquals(0, s.lastRunTokens)
        assertNull(s.tokensPerSec)
        assertNull(s.tools["x"])
        assertEquals(0, s.errors)
    }
}
