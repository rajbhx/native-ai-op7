package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryBudgetTest {
    @Test
    fun kvCacheFormulaMatchesSpec() {
        // 2 (K+V) * 2048 ctx * 28 layers * 2048 hidden * 1 byte = ~224 MB
        val budget = MemoryBudget.estimate(modelBytes = 0, nCtx = 2048, layers = 28, hiddenDim = 2048)
        assertEquals(224.0, budget.kvCacheMb, 1.0)
    }

    @Test
    fun onePointFiveBillionModelFitsBudget() {
        // ~1.5B params @ Q4_K_M ≈ 0.9 GB on disk
        val budget = MemoryBudget.estimate(
            modelBytes = 900L * 1024 * 1024,
            nCtx = Op7SystemProfile.CONTEXT_LENGTH,
            layers = 28,
            hiddenDim = 2048,
        )
        assertTrue("total=${budget.totalMb} should fit 1536", budget.withinLimit)
    }

    @Test
    fun oversizedWeightsExceedBudget() {
        val budget = MemoryBudget.estimate(modelBytes = 2L * 1024 * 1024 * 1024, nCtx = 2048)
        assertFalse(budget.withinLimit)
    }

    @Test
    fun profileConstantsMatchSpec() {
        assertEquals(1536, Op7SystemProfile.MEMORY_LIMIT_MB)
        assertEquals(0xF0, Op7SystemProfile.PINNED_AFFINITY_MASK)
        assertEquals(0.05, Op7SystemProfile.DECAY_LAMBDA, 1e-9)
        assertEquals(2048, Op7SystemProfile.CONTEXT_LENGTH)
        assertEquals("Q8_0", Op7SystemProfile.KV_QUANT_TYPE)
        assertEquals(4, Op7SystemProfile.THREADS)
    }

    @Test
    fun realMetadataKvFormulaMatchesShippedModel() {
        // Qwen2.5-1B: 24 layers, 1024 hidden -> 2*2048*24*1024 = ~96 MB @ Q8_0
        val budget = MemoryBudget.estimate(
            modelBytes = 491L * 1024 * 1024,
            nCtx = 2048,
            layers = 24,
            hiddenDim = 1024,
        )
        assertEquals(96.0, budget.kvCacheMb, 1.0)
        assertTrue(budget.withinLimit)
    }

    @Test
    fun nativeCeilingMatchesKotlinSingleSourceOfTruth() {
        // MemoryMonitor.cpp kOp7MemoryLimitBytes MUST equal this exact byte count.
        assertEquals(1_610_612_736L, Op7SystemProfile.MEMORY_LIMIT_BYTES)
    }
}
