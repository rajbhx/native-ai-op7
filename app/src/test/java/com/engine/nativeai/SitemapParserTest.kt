package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SitemapParserTest {

    private val flat = """
        <?xml version="1.0" encoding="UTF-8"?>
        <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
          <url><loc>https://fmhy.net/ai</loc></url>
          <url><loc>https://fmhy.net/audio</loc></url>
          <url><loc>https://fmhy.net/downloading</loc></url>
        </urlset>
    """.trimIndent()

    @Test
    fun parsesFlatUrlsetInOrder() {
        assertEquals(
            listOf("https://fmhy.net/ai", "https://fmhy.net/audio", "https://fmhy.net/downloading"),
            SitemapParser.parseUrls(flat),
        )
    }

    @Test
    fun dedupesAndFiltersNonHttp() {
        val xml = """
            <urlset>
              <url><loc>https://fmhy.net/ai</loc></url>
              <url><loc>https://fmhy.net/ai</loc></url>
              <url><loc>ftp://not-http/x</loc></url>
              <url><loc>javascript:void(0)</loc></url>
              <url><loc>https://fmhy.net/audio</loc></url>
            </urlset>
        """.trimIndent()
        assertEquals(
            listOf("https://fmhy.net/ai", "https://fmhy.net/audio"),
            SitemapParser.parseUrls(xml),
        )
    }

    @Test
    fun capsAtLimit() {
        val urls = SitemapParser.parseUrls(flat, limit = 2)
        assertEquals(2, urls.size)
        assertTrue(urls.first().startsWith("https://"))
    }

    @Test
    fun sitemapIndexIsNotSupportedYet() {
        val index = """
            <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
              <sitemap><loc>https://example.com/sitemap-1.xml</loc></sitemap>
            </sitemapindex>
        """.trimIndent()
        assertTrue(SitemapParser.parseUrls(index).isEmpty())
    }

    @Test
    fun malformedInputIsEmptyNotCrash() {
        assertTrue(SitemapParser.parseUrls("").isEmpty())
        assertTrue(SitemapParser.parseUrls("not xml at all <").isEmpty())
        assertTrue(SitemapParser.parseUrls("<urlset><url><loc>https://x/</loc>").isEmpty())
    }
}
