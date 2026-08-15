package com.engine.nativeai

/**
 * Replaceable local inference runtime (master prompt §19). The agent/model
 * layer depends on this seam, never on a concrete backend: llama.cpp today,
 * MNN/other tomorrow. Each backend reports honest availability — a runtime
 * that is not loadable on this device is never presented as available.
 */
interface InferenceBackend {
    val available: Boolean
    val runtime: RuntimeKind

    /** One-line human-readable status for diagnostics/UI. */
    fun status(): String

    /** Release runtime resources. Default no-op for stateless probes. */
    fun shutdown() {}
}
