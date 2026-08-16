package com.engine.nativeai

import java.io.File

/**
 * HNSW vector index backed by vendored USearch (v2.11.3, vector-lib).
 * Bounded: hard vector cap, persisted to the app files dir, and honest —
 * when the platform cannot load vector-lib the index reports unavailable
 * and callers fall back to BM25 (never a fake capability).
 */
class USearchVectorIndex(
    private val dimensions: Int,
    private val store: File,
    private val maxVectors: Int = 10_000,
) : VectorIndex {

    private var handle: Long = 0L

    init {
        if (LIB_LOADED && dimensions in 1..4096) {
            val created = nativeCreate(dimensions)
            if (created != 0L) {
                handle = created
                if (store.exists()) nativeLoad(handle, store.absolutePath)
            }
        }
    }

    override val available: Boolean get() = handle != 0L

    override val size: Int get() = if (available) nativeSize(handle) else 0

    override fun add(id: Long, vector: FloatArray) {
        if (!available || size >= maxVectors) return // bounded, never grows unbounded
        if (vector.size != dimensions) return
        nativeAdd(handle, id, vector)
    }

    override fun search(vector: FloatArray, k: Int): List<VectorHit> {
        if (!available || vector.size != dimensions || k <= 0) return emptyList()
        return nativeSearch(handle, vector, k.coerceAtMost(64))?.toList() ?: emptyList()
    }

    override fun save() {
        if (available) {
            store.parentFile?.mkdirs()
            nativeSave(handle, store.absolutePath)
        }
    }

    override fun close() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private external fun nativeCreate(dims: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeSize(handle: Long): Int
    private external fun nativeAdd(handle: Long, key: Long, vector: FloatArray): Boolean
    private external fun nativeSearch(handle: Long, vector: FloatArray, k: Int): Array<VectorHit>?
    private external fun nativeSave(handle: Long, path: String): Boolean
    private external fun nativeLoad(handle: Long, path: String): Boolean

    companion object {
        /** On-device smoke check (used by diagnostics, not as a fake capability). */
        fun selfTest(): Boolean =
            runCatching { LIB_LOADED && nativeSelfTest() }.getOrDefault(false)

        private external fun nativeSelfTest(): Boolean

        private val LIB_LOADED = try {
            System.loadLibrary("vector-lib")
            true
        } catch (e: UnsatisfiedLinkError) {
            false
        }
    }
}
