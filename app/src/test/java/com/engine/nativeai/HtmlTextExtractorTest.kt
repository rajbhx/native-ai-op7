package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextExtractorTest {

    @Test
    fun stripsTagsScriptsAndStyles() {
        val html = """
            <html><head><title>t</title></head><body>
            <script>alert(1)</script>
            <style>body{color:red}</style>
            <h1>Title</h1><p>Body <b>text</b> here</p>
            <!-- comment -->
            </body></html>
        """.trimIndent()
        val text = HtmlTextExtractor.toText(html)
        assertTrue(text.contains("Title"))
        assertTrue(text.contains("Body text here"))
        assertTrue(!text.contains("alert"))
        assertTrue(!text.contains("color:red"))
        assertTrue(!text.contains("comment"))
    }

    @Test
    fun decodesEntities() {
        val html = "<p>a &amp; b &lt; c &gt; d &quot;e&quot; &#39;f&#39; nbsp&nbsp;end</p>"
        val text = HtmlTextExtractor.toText(html)
        assertEquals("a & b < c > d \"e\" 'f' nbsp end", text)
    }

    @Test
    fun collapsesWhitespace() {
        val html = "<p>  one\n\ttwo   three  </p>"
        assertEquals("one two three", HtmlTextExtractor.toText(html))
    }

    @Test
    fun blankInputStaysBlank() {
        assertEquals("", HtmlTextExtractor.toText(""))
        assertEquals("", HtmlTextExtractor.toText("   "))
    }

    @Test
    fun keepsLinkTextSearchable() {
        val html = """<a href="https://fmhy.net/ai">Artificial Intelligence</a> guide"""
        val text = HtmlTextExtractor.toText(html)
        assertEquals("Artificial Intelligence guide", text)
    }
}
