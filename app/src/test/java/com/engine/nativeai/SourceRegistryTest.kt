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
    @Test
    fun catalogUpgradePrunesRemovedSeedsOnce() {
        val store = FakeSourceStore()
        store.saveSource(Source(0, 1, "Termux", SourceType.GITHUB_REPO, owner = "termux", repo = "termux-app"))
        store.saveSource(Source(0, 1, "llama.cpp", SourceType.GITHUB_REPO))
        store.saveSource(Source(0, 1, "uBlock Origin", SourceType.GITHUB_REPO))
        store.saveSource(Source(0, 1, "MemPalace", SourceType.GITHUB_REPO))
        store.saveSource(Source(0, 1, "LiteRT", SourceType.GITHUB_REPO))
        store.saveSource(Source(0, 1, "My Custom Source", SourceType.RAW_TEXT))
        val registry = SourceRegistry(store)
        val catalog = listOf(
            SourceRegistry.SeedSource("Knowledge", "FMHY", SourceType.SITE, contentUrl = "https://fmhy.net/sitemap.xml"),
            SourceRegistry.SeedSource("My Projects", "OP7 Special Build Playbook", SourceType.GITHUB_REPO, owner = "rajbhx", repo = "op7-special-build-playbook"),
        )

        val added = registry.seed(catalog, catalogVersion = 2)

        val titles = store.sources().map { it.title }
        assertEquals(listOf("My Custom Source", "FMHY", "OP7 Special Build Playbook"), titles)
        assertEquals(2, added)
        // Second run (same version): nothing pruned, nothing added.
        assertEquals(0, registry.seed(catalog, catalogVersion = 2))
        assertEquals(3, store.sources().size)
        // Upgrading from a fresh DB with no legacy rows is a no-op too.
        assertEquals("2", store.metaGet("source_catalog_version"))
    }

    @Test
    fun seedDefaultVersionDoesNotPrune() {
        val store = FakeSourceStore()
        store.saveSource(Source(0, 1, "Termux", SourceType.GITHUB_REPO))
        val registry = SourceRegistry(store)
        registry.seed(emptyList(), catalogVersion = 1)
        assertEquals(1, store.sources().size)
    }

}
