package com.engine.nativeai

/**
 * Memory allocation equations (math/hardware spec §2.A). The breakdown is an
 * estimate computed from measured inputs (model bytes, n_ctx) plus profile
 * constants; nothing here is fabricated — fields are labeled estimate vs
 * measured.
 */
object MemoryBudget {

    data class Budget(
        val weightsMb: Double,
        val kvCacheMb: Double,
        val graphMb: Double,
        val sqliteMb: Double,
        val totalMb: Double,
        val withinLimit: Boolean,
        val limitMb: Int = Op7SystemProfile.MEMORY_LIMIT_MB,
    )

    /**
     * @param modelBytes measured llama model size (model_bytes from stats)
     * @param nCtx context length
     * @param layers transformer layers (estimate unless measured)
     * @param hiddenDim hidden dimension (estimate unless measured)
     * @param kvElementBytes bytes per KV element (Q8_0 -> 1)
     */
    fun estimate(
        modelBytes: Long,
        nCtx: Int,
        layers: Int = 28,
        hiddenDim: Int = 2048,
        kvElementBytes: Int = 1,
    ): Budget {
        val weightsMb = modelBytes / (1024.0 * 1024.0)
        // M_kv = 2 (K+V) * n_ctx * L * H * B_element
        val kvCacheMb = 2.0 * nCtx * layers * hiddenDim * kvElementBytes / (1024.0 * 1024.0)
        val graphMb = Op7SystemProfile.GGML_SCRATCH_MAX_MB.toDouble() // fixed scratch block
        val sqliteMb = Op7SystemProfile.SQLITE_FTS5_MAX_MB.toDouble()
        val totalMb = weightsMb + kvCacheMb + graphMb + sqliteMb
        val limit = Op7SystemProfile.MEMORY_LIMIT_MB
        return Budget(
            weightsMb = weightsMb,
            kvCacheMb = kvCacheMb,
            graphMb = graphMb,
            sqliteMb = sqliteMb,
            totalMb = totalMb,
            withinLimit = totalMb <= limit,
            limitMb = limit,
        )
    }
}
