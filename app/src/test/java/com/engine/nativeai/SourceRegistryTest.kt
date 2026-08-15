package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRegistryTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    @Test
    fun seedIsIdempotent() {
        val registry = SourceRegistry(FakeSourceStore())
        val seed = listOf(
            SourceRegistry.SeedSource(
                collection = "AI", title = "llama.cpp", type = SourceType.GITHUB_REPO,
                owner = "ggerganov", repo = "llama.cpp",
            ),
        )
        assertEquals(1, registry.seed(seed))
        assertEquals(0, registry.seed(seed))
        assertEquals(1, registry.sources().size)
    }

    @Test
    fun updateCandidatesOnlyReturnsDueSources() {
        val store = FakeSourceStore()
        val registry = SourceRegistry(store)
        store.saveSource(Source(0, 1, "due10h", SourceType.RAW_TEXT, writeTime = now - 34 * hour, updateAfterHours = 24))
        store.saveSource(Source(0, 1, "due2h", SourceType.RAW_TEXT, writeTime = now - 26 * hour, updateAfterHours = 24))
        store.saveSource(Source(0, 1, "fresh", SourceType.RAW_TEXT, writeTime = now - hour, updateAfterHours = 24))
        val candidates = registry.updateCandidates(now)
        assertEquals(listOf("due10h", "due2h"), candidates.map { it.title })
    }

    @Test
    fun addAndRemoveSourceRoundTrip() {
        val store = FakeSourceStore()
        val registry = SourceRegistry(store)
        val id = registry.addSource(Source(0, 1, "x", SourceType.RAW_TEXT))
        assertEquals(1, store.sources().size)
        registry.removeSource(id)
        assertTrue(store.sources().isEmpty())
    }
}
