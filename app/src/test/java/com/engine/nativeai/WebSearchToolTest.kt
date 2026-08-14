package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolTest {
    @Test
    fun localFallbackIsExplicitlyUnavailable() {
        val out = WebSearchTool(LocalFallbackProvider()).execute("test query")
        assertFalse(out.ok)
        assertTrue(out.text.contains("unavailable"))
    }

    @Test
    fun providerHitMarksToolOk() {
        val provider = object : SearchProvider {
            override suspend fun search(query: String) = SearchResult("result for $query", ok = true)
        }
        val out = WebSearchTool(provider).execute("weather")
        assertTrue(out.ok)
        assertEquals("result for weather", out.text)
    }

    @Test
    fun blankProviderTextIsNotOkEvenIfFlagged() {
        val provider = object : SearchProvider {
            override suspend fun search(query: String) = SearchResult("   ", ok = true)
        }
        assertFalse(WebSearchTool(provider).execute("q").ok)
    }
}
