package com.engine.nativeai

/**
 * Deterministic text chunker for source ingestion (roadmap Phase 5).
 * Bounded: 512-char chunks, 64-char overlap, word-boundary aware, hard caps
 * per file (1 MB) and per source (500 files) so a hostile/giant repo can
 * never blow the 1.5 GB runtime budget.
 */
object SourceChunker {

    /** Split text into overlapping chunks. Pure and deterministic (tested). */
    fun chunk(text: String, chunkChars: Int = SourceCapabilities.CHUNK_CHARS,
              overlapChars: Int = SourceCapabilities.CHUNK_OVERLAP_CHARS): List<String> {
        if (text.isEmpty()) return emptyList()
        if (chunkChars <= 0) return listOf(text)
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + chunkChars).coerceAtMost(text.length)
            if (end < text.length) {
                // Step back to the last whitespace inside the window so
                // chunks break on word boundaries instead of mid-token.
                var ws = -1
                var i = end - 1
                val floor = start + chunkChars / 2
                while (i > floor) {
                    if (text[i] == ' ') { ws = i; break }
                    i--
                }
                if (ws > start) end = ws
            }
            val piece = text.substring(start, end).trim()
            if (piece.isNotEmpty()) chunks += piece
            if (end >= text.length) break
            // Overlap: next window starts overlapChars before this end so
            // retrieval never loses the boundary context between chunks.
            start = (end - overlapChars).coerceAtLeast(start + 1)
        }
        return chunks
    }

    /** Crude binary sniff: NUL byte in the first 8 KiB => not text. */
    fun isLikelyBinary(bytes: ByteArray): Boolean {
        val n = minOf(bytes.size, 8192)
        for (i in 0 until n) if (bytes[i] == 0.toByte()) return true
        return false
    }

    /** File-level ingestion guard (1 MB cap, extension allowlist for code). */
    fun shouldIngest(bytes: ByteArray, path: String): Boolean {
        if (bytes.isEmpty() || bytes.size > SourceCapabilities.MAX_FILE_BYTES) return false
        if (isLikelyBinary(bytes)) return false
        val lower = path.lowercase()
        val ext = lower.substringAfterLast('.', "").ifEmpty { "" }
        val allowed = setOf(
            "txt", "md", "markdown", "kt", "java", "c", "cpp", "h", "hpp", "cc",
            "py", "rs", "go", "js", "ts", "json", "xml", "yml", "yaml", "toml",
            "ini", "cfg", "sh", "bash", "sql", "html", "htm", "css", "gradle",
            "properties", "csv", "log", "rst", "adoc", "cmake",
        )
        return ext in allowed || lower.contains("readme") || lower.contains("license") ||
            lower.contains("changelog") || lower.contains("makefile") || lower.contains("cmake")
    }
}
