package com.engine.nativeai

/**
 * Replaceable local vector index (roadmap Phase 6). USearch-backed
 * implementation ships behind an honest availability probe; the interface
 * keeps the store/agent layer decoupled from the concrete engine.
 */
data class VectorHit(val key: Long, val distance: Float)

interface VectorIndex {
    val available: Boolean
    val size: Int

    /** Insert or replace one vector. Bounded by the implementation. */
    fun add(id: Long, vector: FloatArray)

    /** Approximate nearest neighbors (cosine). */
    fun search(vector: FloatArray, k: Int): List<VectorHit>

    /** Persist to the configured file. */
    fun save()

    fun close()
}

/**
 * Local text -> vector embedding seam. Deliberately NOT wired to any model
 * yet: an implementation only ships after the on-device benchmark gate
 * validates it (no fake capabilities). When null/unavailable, source search
 * stays BM25-only.
 */
interface EmbeddingProvider {
    val available: Boolean
    suspend fun embed(text: String): FloatArray?
}
