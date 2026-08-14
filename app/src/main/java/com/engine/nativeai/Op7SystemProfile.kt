package com.engine.nativeai

/**
 * OnePlus 7 system parameters (math/hardware spec §4). Single source of
 * truth for device constraints — the agent, watchdog and UI read these.
 */
object Op7SystemProfile {
    const val DEVICE = "OnePlus 7"
    const val CHIPSET = "Snapdragon 855"

    // Kryo 485: 1x Prime 2.84 GHz, 3x Gold 2.42 GHz, 4x Silver 1.78 GHz.
    const val PRIME_CORES = 1
    const val GOLD_CORES = 3
    const val SILVER_CORES = 4

    // 4 threads pinned to Gold/Prime cores 4-7 -> POSIX mask 0xF0
    // (spec text shows 0x70/0x80; combined they are bits 4-7 = 0xF0).
    const val PINNED_AFFINITY_MASK = 0xF0

    // Hard RAM ceiling: 1.5 GB = 1536 MB (AI runtime footprint).
    const val MEMORY_LIMIT_MB = 1536
    const val MEMORY_LIMIT_BYTES = MEMORY_LIMIT_MB.toLong() * 1024 * 1024

    // Memory budget breakdown (spec §2.A).
    const val MODEL_WEIGHTS_MAX_MB = 1050
    const val KV_CACHE_MAX_MB = 224
    const val GGML_SCRATCH_MAX_MB = 64
    const val SQLITE_FTS5_MAX_MB = 16

    // Inference config (spec §4).
    const val CONTEXT_LENGTH = 2048
    const val BATCH_SIZE = 512
    const val KV_QUANT_TYPE = "Q8_0"
    const val THREADS = 4
    const val USE_MMAP = true

    // Memory decay (spec §2.B): lambda = 0.05 -> half-life ~13.8 days.
    const val DECAY_LAMBDA = 0.05

    // Context compression watermark (spec §2.C): 85% of n_ctx.
    const val CONTEXT_COMPRESS_THRESHOLD = 0.85f
}
