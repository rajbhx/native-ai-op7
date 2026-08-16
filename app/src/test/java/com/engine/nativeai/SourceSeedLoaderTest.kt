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
    private val v2 = """
        {
          "version": 2,
          "sources": [
            {"collection": "Knowledge", "title": "FMHY", "type": "SITE",
             "contentUrl": "https://fmhy.net/sitemap.xml", "updateAfterHours": 24},
            {"collection": "My Projects", "title": "OP7 Special Build Playbook", "type": "GITHUB_REPO",
             "owner": "rajbhx", "repo": "op7-special-build-playbook", "updateAfterHours": 12}
          ]
        }
    """.trimIndent()

    @Test
    fun v2CatalogIsFmhyAndPlaybookOnly() {
        val seeds = SourceSeedLoader.parse(v2)
        assertEquals(listOf("FMHY", "OP7 Special Build Playbook"), seeds.map { it.title })
        val fmhy = seeds.first { it.title == "FMHY" }
        assertEquals(SourceType.SITE, fmhy.type)
        assertEquals("https://fmhy.net/sitemap.xml", fmhy.contentUrl)
        assertEquals("Knowledge", fmhy.collection)
        assertEquals(24, fmhy.updateAfterHours)
        val playbook = seeds.first { it.title == "OP7 Special Build Playbook" }
        assertEquals("rajbhx", playbook.owner)
        assertEquals("op7-special-build-playbook", playbook.repo)
    }

    @Test
    fun catalogVersionParsedWithDefaultOne() {
        assertEquals(2, SourceSeedLoader.parseVersion(v2))
        assertEquals(1, SourceSeedLoader.parseVersion("{\"version\": 1, \"sources\": []}"))
        assertEquals(1, SourceSeedLoader.parseVersion(""))
        assertEquals(1, SourceSeedLoader.parseVersion("not json"))
    }

}
