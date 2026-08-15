package com.engine.nativeai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextManagerTest {
    @Test
    fun compressesAt85PercentWatermark() {
        val cm = ContextManager(maxTokens = 100)
        val observations = List(20) { "x".repeat(20) }
        val text = cm.build("sys", "user", "", observations)
        assertTrue("expected <= 85 tokens, got ${cm.estimateTokens(text)}", cm.estimateTokens(text) <= 85)
    }

    @Test
    fun systemPromptNeverTruncated() {
        val cm = ContextManager(maxTokens = 40)
        val text = cm.build("SYS", "u".repeat(1000), "", emptyList())
        assertTrue(text.startsWith("SYS"))
        assertTrue(cm.estimateTokens(text) <= 40)
    }

    @Test
    fun dropsMemoryBeforeHardTruncation() {
        val cm = ContextManager(maxTokens = 60)
        val memory = "m".repeat(600)
        val text = cm.build("s", "u", memory, emptyList())
        assertTrue(cm.estimateTokens(text) <= 51) // 60 * 0.85
    }

    @Test
    fun rendersSourcesSectionWhenProvided() {
        val cm = ContextManager(maxTokens = 200)
        val text = cm.build("s", "u", "mem", emptyList(), sources = "[src/file] content")
        assertTrue(text.contains("Relevant sources:"))
        assertTrue(text.contains("[src/file] content"))
        assertTrue(text.contains("Relevant memory:"))
    }

    @Test
    fun dropsSourcesAfterMemoryBeforeUserTruncation() {
        val cm = ContextManager(maxTokens = 60)
        val text = cm.build("s", "u".repeat(1000), "m".repeat(200), emptyList(), sources = "src".repeat(300))
        assertTrue("expected <= 51 tokens, got ${cm.estimateTokens(text)}", cm.estimateTokens(text) <= 51)
        assertTrue("user prompt must survive", text.contains("u".repeat(40)))
        assertFalse("sources must be dropped before hard truncation", text.contains("Relevant sources:"))
    }
}
