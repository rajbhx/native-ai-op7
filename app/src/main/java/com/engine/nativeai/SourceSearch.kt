package com.engine.nativeai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Source-aware search (roadmap Phase 6): FTS5/BM25 with LIKE fallback.
 * Hybrid mode (BM25 + HNSW vectors) is dormant by design: it only engages
 * when BOTH a real EmbeddingProvider and a VectorIndex are supplied and
 * report available. Until an embedding model passes the on-device benchmark
 * gate, callers see pure BM25 — never a fabricated vector capability.
 */
class SourceSearch(
    private val db: SourceStore,
    private val vectorIndex: VectorIndex? = null,
    private val embeddingProvider: EmbeddingProvider? = null,
) {

    suspend fun search(query: String, limit: Int = 5): List<SourceSearchHit> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()
            val bm25 = db.searchSources(query.trim(), limit)
            val vectorsReady = vectorIndex?.available == true && embeddingProvider?.available == true
            if (!vectorsReady) return@withContext bm25
            val index = vectorIndex ?: return@withContext bm25
            val embedder = embeddingProvider ?: return@withContext bm25
            val embedded = runCatching { embedder.embed(query.trim()) }.getOrNull()
                ?: return@withContext bm25
            val vectorHits = index.search(embedded, limit)
            if (vectorHits.isEmpty()) return@withContext bm25
            merge(bm25, vectorHits, limit)
        }

    /**
     * Reciprocal-rank fusion of BM25 + vector hits. Vector keys are chunk
     * ids; unknown keys are ignored so stale vectors never poison results.
     */
    private fun merge(
        bm25: List<SourceSearchHit>,
        vectorHits: List<VectorHit>,
        limit: Int,
    ): List<SourceSearchHit> {
        val fused = mutableMapOf<Long, Double>()
        val byChunk = mutableMapOf<Long, SourceSearchHit>()
        fun rrf(rank: Int): Double = 1.0 / (60.0 + rank + 1)

        bm25.forEachIndexed { i, hit ->
            if (hit.chunkId > 0L) {
                byChunk[hit.chunkId] = hit
                fused[hit.chunkId] = (fused[hit.chunkId] ?: 0.0) + rrf(i)
            }
        }
        vectorHits.forEachIndexed { i, vh ->
            val hit = byChunk[vh.key] ?: chunkHit(vh.key) ?: return@forEachIndexed
            byChunk[vh.key] = hit
            fused[vh.key] = (fused[vh.key] ?: 0.0) + rrf(i)
        }
        return fused.entries
            .sortedByDescending { it.value }
            .take(limit)
            .mapNotNull { byChunk[it.key] }
            .distinctBy { it.chunkId }
    }

    private fun chunkHit(chunkId: Long): SourceSearchHit? {
        val chunk = db.chunkById(chunkId) ?: return null
        return SourceSearchHit(
            sourceTitle = db.sourceById(chunk.sourceId)?.title ?: "?",
            filePath = db.sourceFiles(chunk.sourceId)
                .firstOrNull { it.id == chunk.sourceFileId }?.path ?: "?",
            content = chunk.content,
            score = 0f,
            chunkId = chunk.id,
        )
    }
}

/** [source/file] citations for the hybrid agent context (roadmap Phase 6). */
fun formatSourceHits(hits: List<SourceSearchHit>): String =
    hits.joinToString("\n\n") {
        "[${it.sourceTitle}/${it.filePath}] ${it.content.trim().take(400)}"
    }
