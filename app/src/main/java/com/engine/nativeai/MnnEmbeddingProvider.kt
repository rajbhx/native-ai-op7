package com.engine.nativeai

import java.io.File

/**
 * MNN-backed embedding provider (roadmap Phase 6, catalog: alibaba/MNN).
 *
 * Honest capability gate — nothing is claimed until every condition is real:
 *  1. libMNN.so loads (MnnBackend probe),
 *  2. an MNN-converted embedding model exists at
 *     `models/embeddings/embedding.mnn` (see docs/EMBEDDINGS.md for
 *     conversion/placement),
 *  3. the on-device embedding benchmark gate validates it (field rule:
 *     measure before claiming).
 *
 * Until then `available == false` and `embed()` returns null, so SourceSearch
 * stays BM25-only — never a fabricated vector capability. The MNN session
 * inference call is the documented next phase (asset + gate), not a stub
 * that pretends to work.
 */
class MnnEmbeddingProvider(
    private val modelFile: File,
    private val backend: MnnBackend = MnnBackend(),
    private val gatePassed: Boolean = EMBEDDING_GATE_PASSED,
) : EmbeddingProvider {

    override val available: Boolean get() = backend.available && modelFile.isFile && gatePassed

    override suspend fun embed(text: String): FloatArray? {
        if (!available) return null
        // MNN session inference lands with the validated model asset:
        // load Interpreter via the dlopen'd libMNN, run `input_ids` forward
        // pass, and return the pooled CLS vector (see docs/EMBEDDINGS.md).
        return null
    }

    /** Human-readable reason when the gate is not satisfied (Diagnostics). */
    fun reason(): String = when {
        !backend.available -> "MNN lib unavailable"
        !modelFile.isFile -> "missing models/embeddings/embedding.mnn"
        !gatePassed -> "embedding benchmark gate not passed"
        else -> "ready"
    }

    companion object {
        /**
         * Flipped only after the on-device embedding benchmark validates a
         * real model (record the measurement in docs/field-notes/ first).
         */
        const val EMBEDDING_GATE_PASSED = false
    }
}
