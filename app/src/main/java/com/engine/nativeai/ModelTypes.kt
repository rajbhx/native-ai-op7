package com.engine.nativeai

/** Model classification enums for the provider abstraction layer. */

/** Local on-device model vs remote (network) model. */
enum class ModelKind { LOCAL, REMOTE }

/**
 * Inference runtime for a local model. Never assume GGUF-only: a future
 * model may run on MNN or another backend. Remote models are API-based by
 * definition; UNKNOWN means the runtime has not been confirmed.
 */
enum class RuntimeKind { LLAMA_GGUF, MNN, API, UNKNOWN }

/** Cost tier declared by a provider; PAID requires explicit user permission. */
enum class ModelCostTier { FREE, PAID, UNKNOWN }

/** Availability is live metadata, never assumed. */
enum class ModelAvailability { AVAILABLE, LIMITED, UNAVAILABLE, UNKNOWN }

/** Provider-neutral capability flags (spec §5). */
enum class ModelCapability {
    TOOLS, VISION, REASONING, STREAMING, STRUCTURED_OUTPUT, EMBEDDINGS, CODING, LONG_CONTEXT,
}

/** Task classification produced by TaskClassifier (spec §7). */
enum class TaskType {
    CHAT, RESEARCH, CODING, DEBUGGING, REASONING, SUMMARIZATION,
    DOCUMENT_ANALYSIS, TOOL_EXECUTION, VISION, LONG_CONTEXT, OFFLINE_TASK;

    /** Knowledge-seeking tasks are augmented with local source-KB hits (roadmap Phase 6). */
    fun consultsSources(): Boolean = when (this) {
        RESEARCH, SUMMARIZATION, DOCUMENT_ANALYSIS, DEBUGGING, CODING -> true
        else -> false
    }
}

/** Routing preference (spec §8-10, §20). */
enum class RoutingMode {
    /** Default: local for simple requests, free remote for complex, local fallback. */
    HYBRID,
    /** Prefer free remote models, then local, then paid only if permitted. */
    FREE_FIRST,
    /** Prefer the local llama.cpp model, remote only when needed. */
    LOCAL_FIRST,
    /** Network forbidden: local models only. */
    OFFLINE_ONLY,
    /** Free remote models + local; paid models are never auto-selected. */
    FREE_ONLY,
}

/** Privacy preference (spec: privacy). */
enum class PrivacyMode {
    /** Local llama.cpp only; remote requests are never sent. */
    LOCAL_ONLY,
    /** Local by default; remote only when needed, with a remote indicator. */
    HYBRID,
    /** Remote providers allowed; a remote indicator is still shown. */
    REMOTE_ALLOWED,
}
