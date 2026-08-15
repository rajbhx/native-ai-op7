package com.engine.nativeai

/**
 * Derived, single-sourced status line for the active model card (C3).
 * Never infers state from UI text — the inputs are the real model/file/network
 * state. Remote readiness beyond ONLINE is shown by the health badge, which
 * is checked live; nothing here fabricates availability.
 */
object ModelStatus {

    fun line(
        d: ModelDescriptor?,
        localLoaded: Boolean,
        modelFileExists: Boolean,
        networkAvailable: Boolean,
    ): String {
        if (d == null) return "UNKNOWN \u00b7 NO MODEL"
        return if (d.kind == ModelKind.LOCAL) {
            when {
                localLoaded -> "READY \u00b7 LOCAL \u00b7 GGUF"
                modelFileExists -> "AVAILABLE \u00b7 LOCAL \u00b7 GGUF"
                else -> "NO MODEL FILE \u00b7 LOCAL"
            }
        } else {
            val access = tier(d)
            if (!networkAvailable) "OFFLINE \u00b7 REMOTE \u00b7 $access"
            else "ONLINE \u00b7 REMOTE \u00b7 $access"
        }
    }

    /** Best-effort quant tag from a GGUF filename; null when not identifiable. */
    fun quantTag(fileName: String): String? {
        val n = fileName.lowercase()
        val known = listOf(
            "iq4_xs", "q4_k_m", "q4_0", "q4_1", "q5_k_m", "q5_0", "q5_1",
            "q6_k", "q8_0", "q3_k_m", "q2_k", "f16", "f32",
        )
        return known.firstOrNull { it in n }
    }

    private fun tier(d: ModelDescriptor): String = when (d.costTier) {
        ModelCostTier.FREE -> "FREE"
        ModelCostTier.PAID -> "PAID"
        ModelCostTier.UNKNOWN -> "?"
    }
}
