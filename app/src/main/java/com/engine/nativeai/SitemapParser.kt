package com.engine.nativeai

import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal flat-sitemap parser for SITE sources (fmhy-type knowledge sites).
 * v1 handles a single <urlset>; a <sitemapindex> or malformed XML yields an
 * empty list so the updater can fail honestly ("no pages") instead of
 * fabricating content. Uses tagName (namespace-prefix-stripped) because the
 * default DocumentBuilderFactory is not namespace-aware, so localName would
 * be null for xmlns-bearing sitemaps like fmhy.net's.
 */
object SitemapParser {

    /** Page URLs in document order; deduped, http(s) only, capped at [limit]. */
    fun parseUrls(xml: String, limit: Int = 60): List<String> {
        if (xml.isBlank() || limit <= 0) return emptyList()
        val root = try {
            parseRoot(xml) ?: return emptyList()
        } catch (e: Exception) {
            return emptyList() // malformed XML is honest "no pages", never a crash
        }
        // Flat urlset only. sitemapindex support is deferred (Phase 6 scope).
        if (name(root) != "urlset") return emptyList()
        val out = LinkedHashSet<String>()
        for (child in children(root)) {
            if (name(child) != "url") continue
            val loc = children(child).firstOrNull { name(it) == "loc" }?.textContent
                ?.trim() ?: continue
            if (!loc.startsWith("https://") && !loc.startsWith("http://")) continue
            out += loc
            if (out.size >= limit) break
        }
        return out.toList()
    }

    private fun parseRoot(xml: String): Element? {
        val factory = DocumentBuilderFactory.newInstance()
        // Best-effort XXE hardening; ignore features a platform factory lacks.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val doc = factory.newDocumentBuilder().parse(
            org.xml.sax.InputSource(java.io.StringReader(xml)),
        )
        return doc.documentElement
    }

    private fun name(node: Node): String? = (node as? Element)?.tagName?.substringAfterLast(':')

    private fun children(node: Node): List<Element> {
        val out = mutableListOf<Element>()
        var n = node.firstChild
        while (n != null) {
            if (n is Element) out += n
            n = n.nextSibling
        }
        return out
    }
}
