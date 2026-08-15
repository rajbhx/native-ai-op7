package com.engine.nativeai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Source-aware search (roadmap Phase 6): FTS5/BM25 with LIKE fallback. */
class SourceSearch(private val db: SourceStore) {

    suspend fun search(query: String, limit: Int = 5): List<SourceSearchHit> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) emptyList() else db.searchSources(query.trim(), limit)
        }
}

/** [source/file] citations for the hybrid agent context (roadmap Phase 6). */
fun formatSourceHits(hits: List<SourceSearchHit>): String =
    hits.joinToString("\n\n") {
        "[${it.sourceTitle}/${it.filePath}] ${it.content.trim().take(400)}"
    }
