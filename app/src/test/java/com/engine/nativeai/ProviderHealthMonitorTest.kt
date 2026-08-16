package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHealthMonitorTest {

    @Test
    fun unknownProviderIsHealthy() {
        val m = ProviderHealthMonitor()
        assertTrue(m.isHealthy("free-model"))
        assertNull(m.latencyMs("free-model"))
    }

    @Test
    fun repeatedFailuresMarkUnhealthyUntilCooldown() {
        val m = ProviderHealthMonitor(failureThreshold = 2, cooldownMs = 60_000)
        m.reportFailure("p", "boom")
        assertTrue("one failure is tolerated", m.isHealthy("p"))
        m.reportFailure("p", "boom")
        assertFalse("two failures exceed the threshold", m.isHealthy("p"))
        assertEquals("boom", m.lastError("p"))
    }

    @Test
    fun successClearsFailureState() {
        val m = ProviderHealthMonitor(failureThreshold = 1, cooldownMs = 60_000)
        m.reportFailure("p", "x")
        assertFalse(m.isHealthy("p"))
        m.reportSuccess("p")
        assertTrue(m.isHealthy("p"))
        assertEquals("", m.lastError("p"))
    }

    @Test
    fun latencyRecordedAndNegativeIgnored() {
        val m = ProviderHealthMonitor()
        assertNull(m.latencyMs("p"))
        m.reportLatency("p", 1234L)
        assertEquals(1234L, m.latencyMs("p") ?: -1L)
        m.reportLatency("p", -5L)
        assertEquals("negative latency must be ignored", 1234L, m.latencyMs("p") ?: -1L)
    }
}
