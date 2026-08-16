package com.engine.nativeai

/**
 * Lightweight HTML-to-text extraction for SITE sources. No JS, no network:
 * strips script/style/head/comments and tags, decodes common entities, then
 * collapses whitespace so link-heavy guides (fmhy.net) stay searchable.
 * Pure and deterministic (unit-tested).
 */
object HtmlTextExtractor {

    fun toText(html: String): String {
        if (html.isBlank()) return ""
        var s = html
        s = s.replace(Regex("(?is)<!--.*?-->"), " ")
        s = s.replace(Regex("(?is)<(script|style|noscript|head|title)[^>]*>.*?</\\1>"), " ")
        s = s.replace(Regex("(?is)<[^>]+>"), " ")
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.let { code -> runCatching { Char(code).toString() }.getOrNull() } ?: ""
            }
        s = s.replace(Regex("[ \\t\\r\\n]+"), " ").trim()
        return s
    }
}
