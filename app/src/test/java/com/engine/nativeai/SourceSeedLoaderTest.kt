package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceSeedLoaderTest {

    private val json = """
        {
          "version": 1,
          "sources": [
            {"collection": "Android", "title": "Termux", "type": "GITHUB_REPO",
             "owner": "termux", "repo": "termux-app", "updateAfterHours": 24},
            {"collection": "Knowledge", "title": "uBlock Origin", "type": "GITHUB_REPO",
             "owner": "gorhill", "repo": "uBlock", "updateAfterHours": 48},
            {"collection": "Knowledge", "title": "MemPalace", "type": "GITHUB_REPO",
             "owner": "MemPalace", "repo": "mempalace", "updateAfterHours": 48},
            {"collection": "Bad", "title": "", "type": "GITHUB_REPO", "owner": "x", "repo": "y"},
            {"collection": "Bad2", "title": "no-type", "type": "NOT_A_TYPE", "owner": "x", "repo": "y"}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesSeedsIncludingKnowledgeSources() {
        val seeds = SourceSeedLoader.parse(json)
        assertEquals(3, seeds.size)
        val titles = seeds.map { it.title }
        assertTrue("uBlock Origin" in titles)
        assertTrue("MemPalace" in titles)
        val ublock = seeds.first { it.title == "uBlock Origin" }
        assertEquals("gorhill", ublock.owner)
        assertEquals("uBlock", ublock.repo)
        assertEquals(48, ublock.updateAfterHours)
    }

    @Test
    fun malformedEntriesAreSkipped() {
        val seeds = SourceSeedLoader.parse(json)
        assertTrue(seeds.none { it.title.isBlank() })
        assertTrue(seeds.none { it.type == SourceType.GITHUB_REPO && it.repo == "y" })
    }

    @Test
    fun blankInputIsEmpty() {
        assertTrue(SourceSeedLoader.parse("").isEmpty())
        assertTrue(SourceSeedLoader.parse("not json").isEmpty())
        assertTrue(SourceSeedLoader.parse("{}").isEmpty())
    }
}
