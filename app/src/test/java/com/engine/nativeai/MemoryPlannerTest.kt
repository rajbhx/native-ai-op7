package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPlannerTest {

    private val mb = 1024L * 1024L

    @Test
    fun oneBillionModelFitsWithinHardCap() {
        val plan = MemoryPlanner.plan(modelBytes = 491L * mb, nCtx = 2048, availRamBytes = 6L * 1024 * mb)
        assertTrue(plan.withinBudget)
        assertEquals(2048, plan.maxSafeNctx)
        assertTrue(plan.totalMb <= Op7SystemProfile.MEMORY_LIMIT_MB)
    }

    @Test
    fun hardCapAppliedEvenWithHugeRam() {
        val plan = MemoryPlanner.plan(modelBytes = 491L * mb, nCtx = 2048, availRamBytes = 32L * 1024 * mb)
        assertEquals(Op7SystemProfile.MEMORY_LIMIT_MB.toDouble(), plan.availableCapMb, 0.001)
    }

    @Test
    fun oversizedModelFailsGracefully() {
        val plan = MemoryPlanner.plan(modelBytes = 1600L * mb, nCtx = 1024, availRamBytes = 2L * 1024 * mb)
        assertFalse(plan.withinBudget)
        assertEquals(0, plan.maxSafeNctx)
    }

    @Test
    fun lowRamShrinksSafeContext() {
        val plan = MemoryPlanner.plan(modelBytes = 491L * mb, nCtx = 2048, availRamBytes = 700L * mb)
        assertFalse("2048 ctx must not fit with ~700MB free", plan.withinBudget)
        assertTrue(plan.maxSafeNctx < 2048)
    }

    @Test
    fun smallModelWithPlentyRamStaysWithinBudget() {
        val plan = MemoryPlanner.plan(modelBytes = 200L * mb, nCtx = 2048, availRamBytes = 4L * 1024 * mb)
        assertTrue(plan.withinBudget)
    }
}

    @Test
    fun qwen1bKvEstimateMatchesHandMath() {
        val plan = MemoryPlanner.plan(
            modelBytes = 491L * mb,
            nCtx = 2048,
            availRamBytes = 6L * 1024 * mb,
            layers = 24,
            hiddenDim = 1024,
        )
        // 2 (K+V) * 2048 ctx * 24 layers * 1024 hidden * 1 byte = ~96 MB
        assertEquals(96.0, plan.kvCacheMb, 1.0)
        assertEquals(2048, plan.maxSafeNctx)
    }

    @Test
    fun realMetadataNeverShrinksSafeContext() {
        val withDefaults = MemoryPlanner.plan(491L * mb, 2048, 6L * 1024 * mb)
        val withMeta = MemoryPlanner.plan(491L * mb, 2048, 6L * 1024 * mb, layers = 24, hiddenDim = 1024)
        assertTrue("measured geometry must not over-estimate KV", withMeta.kvCacheMb < withDefaults.kvCacheMb)
        assertTrue(withMeta.maxSafeNctx >= withDefaults.maxSafeNctx)
    }
