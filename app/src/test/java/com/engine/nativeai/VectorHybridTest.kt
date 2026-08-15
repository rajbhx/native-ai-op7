package com.engine.nativeai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hybrid BM25 + HNSW merge tests (S7): dormant gate + RRF behavior. */
class VectorHybridTest {

    private class FakeEmbedder : EmbeddingProvider {
        override val available = true
        var query: String = ""
        override suspend fun embed(text: String): FloatArray {
            query = text
            return floatArrayOf(0.1f, 0.2f, 0.3f)
        }
    }

    private class FakeVectorIndex(
        private val results: List<VectorHit> = emptyList(),
    ) : VectorIndex {
        override val available = true
        override val size: Int get() = results.size
        override fun add(id: Long, vector: FloatArray) {}
        override fun search(vector: FloatArray, k: Int): List<VectorHit> = results
        override fun save() {}
        override fun close() {}
    }

    private fun storeWithChunks(): FakeSourceStore {
        val store = FakeSourceStore()
        val collectionId = store.upsertSourceCollection("AI")
        val sourceId = store.saveSource(
            Source(0, collectionId, "uBlock Origin", SourceType.GITHUB_REPO, status = SourceStatus.INDEXED),
        )
        val fileId = store.upsertSourceFile(SourceFile(0, sourceId, "src/main.js"))
        store.replaceSourceChunks(sourceId, fileId, listOf("blocklist matcher in main.js"))
        return store
    }

    @Test
    fun bm25OnlyWhenNoVectorComponents() = runBlocking {
        val store = storeWithChunks()
        val hits = SourceSearch(store).search("blocklist", 3)
        assertEquals(1, hits.size)
        assertTrue(hits[0].content.contains("blocklist"))
    }

    @Test
    fun hybridAddsVectorOnlyChunk() = runBlocking {
        val store = storeWithChunks()
        // BM25 misses "pickle"; the vector index knows chunk 1 is relevant.
        val chunkId = store.chunks.values.flatten().single().id
        val search = SourceSearch(
            store,
            vectorIndex = FakeVectorIndex(listOf(VectorHit(chunkId, 0.01f))),
            embeddingProvider = FakeEmbedder(),
        )
        val hits = search.search("pickle", 3)
        assertTrue("expected the vector-only chunk, got $hits", hits.any { it.chunkId == chunkId })
        assertTrue(hits[0].sourceTitle.contains("uBlock"))
    }

    @Test
    fun staleVectorKeysAreIgnored() = runBlocking {
        val store = storeWithChunks()
        val search = SourceSearch(
            store,
            vectorIndex = FakeVectorIndex(listOf(VectorHit(999L, 0.5f))),
            embeddingProvider = FakeEmbedder(),
        )
        val hits = search.search("pickle", 3)
        assertTrue("stale keys must not crash or appear", hits.none { it.chunkId == 999L })
    }

    @Test
    fun unavailableComponentsFallBackToBm25() = runBlocking {
        val store = storeWithChunks()
        val off = object : VectorIndex {
            override val available = false
            override val size get() = 0
            override fun add(id: Long, vector: FloatArray) {}
            override fun search(vector: FloatArray, k: Int): List<VectorHit> = emptyList()
            override fun save() {}
            override fun close() {}
        }
        val hits = SourceSearch(store, vectorIndex = off, embeddingProvider = FakeEmbedder())
            .search("blocklist", 3)
        assertEquals(1, hits.size)
    }
}
