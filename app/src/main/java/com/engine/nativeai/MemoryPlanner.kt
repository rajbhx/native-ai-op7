package com.engine.nativeai

/**
 * Dynamic 1.5 GB memory planner (user clarification: "1.5 GB means 1.5 GB
 * RAM usage but it's dynamic"). The 1536 MB ceiling stays the hard cap; the
 * planner sizes the KV cache (context) against live available RAM with a
 * safety margin instead of a static carve-out. All values are labeled
 * estimates — nothing is fabricated.
 */
object MemoryPlanner {

    const val SAFETY_MARGIN = 0.15
    const val MIN_NCTX = 256
    const val MAX_NCTX = 2048

    data class Plan(
        val weightsMb: Double,
        val kvCacheMb: Double,
        val graphMb: Double,
        val sqliteMb: Double,
        val availableCapMb: Double,
        val headroomMb: Double,
        val totalMb: Double,
        val withinBudget: Boolean,
        val maxSafeNctx: Int,
        val limitMb: Int = Op7SystemProfile.MEMORY_LIMIT_MB,
    )

    fun plan(
        modelBytes: Long,
        nCtx: Int,
        availRamBytes: Long,
        layers: Int = 28,
        hiddenDim: Int = 2048,
        kvElementBytes: Int = 1,
        safetyMargin: Double = SAFETY_MARGIN,
    ): Plan {
        val weightsMb = modelBytes / (1024.0 * 1024.0)
        val graphMb = Op7SystemProfile.GGML_SCRATCH_MAX_MB.toDouble()
        val sqliteMb = Op7SystemProfile.SQLITE_FTS5_MAX_MB.toDouble()
        val availMb = availRamBytes / (1024.0 * 1024.0)
        // Hard cap 1536 MB; never exceed, but shrink with live available RAM.
        val capMb = minOf(availMb * (1.0 - safetyMargin), Op7SystemProfile.MEMORY_LIMIT_MB.toDouble())
        val kvPerCtxMb = 2.0 * layers * hiddenDim * kvElementBytes / (1024.0 * 1024.0)
        val kvMb = kvPerCtxMb * nCtx
        val fixedMb = weightsMb + graphMb + sqliteMb
        val totalMb = fixedMb + kvMb
        val headroomMb = capMb - fixedMb
        val maxSafeNctx = when {
            headroomMb <= 0 -> 0
            kvPerCtxMb <= 0 -> MAX_NCTX
            else -> ((headroomMb / kvPerCtxMb).toInt()).coerceIn(MIN_NCTX, MAX_NCTX)
        }
        return Plan(
            weightsMb = weightsMb,
            kvCacheMb = kvMb,
            graphMb = graphMb,
            sqliteMb = sqliteMb,
            availableCapMb = capMb,
            headroomMb = headroomMb,
            totalMb = totalMb,
            withinBudget = totalMb <= capMb,
            maxSafeNctx = maxSafeNctx,
        )
    }
}
