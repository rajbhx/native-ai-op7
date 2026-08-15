package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolRegistryTest {

    @Test
    fun snapshotListsRegisteredToolsWithPermission() {
        val registry = ToolRegistry().apply {
            register(CalculatorTool())
            register(TerminalTool(LocalProcessBackend()))
        }
        val snap = registry.snapshot()
        assertEquals(2, snap.size)
        val calc = snap.first { it.name == "calculator" }
        assertEquals(ToolPermission.SAFE, calc.permission)
        assertTrue(calc.enabled)
        assertTrue(calc.available)
        assertEquals("low", calc.riskLevel)
        val term = snap.first { it.name == "terminal" }
        assertEquals(ToolPermission.REQUIRES_APPROVAL, term.permission)
        assertFalse("terminal disabled by default", term.enabled)
        assertEquals("medium", term.riskLevel)
    }

    @Test
    fun terminalBackendLabelReportsLocal() {
        val local = TerminalTool(LocalProcessBackend())
        assertEquals("local", local.backendLabel)
        assertFalse(local.enabled)
    }
}
