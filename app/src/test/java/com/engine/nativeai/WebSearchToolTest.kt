package com.engine.nativeai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchToolTest {
    @Test
    fun localFallbackIsExplicitlyUnavailable() = runBlocking {
        val out = WebSearchTool(LocalFallbackProvider()).execute("test query")
        assertFalse(out.ok)
        assertTrue(out.output.contains("unavailable"))
    }

    @Test
    fun providerHitMarksToolOk() = runBlocking {
        val provider = object : SearchProvider {
            override suspend fun search(query: String) = SearchResult("result for $query", ok = true)
        }
        val out = WebSearchTool(provider).execute("weather")
        assertTrue(out.ok)
        assertEquals("result for weather", out.output)
    }

    @Test
    fun blankProviderTextIsNotOkEvenIfFlagged() = runBlocking {
        val provider = object : SearchProvider {
            override suspend fun search(query: String) = SearchResult("   ", ok = true)
        }
        assertFalse(WebSearchTool(provider).execute("q").ok)
    }
}
