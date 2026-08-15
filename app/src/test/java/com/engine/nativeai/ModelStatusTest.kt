package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelStatusTest {

    private fun remote() = ModelDescriptor(
        id = "r1",
        displayName = "R1",
        provider = "zen",
        endpoint = "https://example.com/v1",
        modelType = "chat",
        kind = ModelKind.REMOTE,
        costTier = ModelCostTier.FREE,
        availability = ModelAvailability.AVAILABLE,
    )

    private fun local() = ModelDescriptor(
        id = "local-llama",
        displayName = "Local",
        provider = "local",
        endpoint = "",
        modelType = "llama",
        kind = ModelKind.LOCAL,
        costTier = ModelCostTier.UNKNOWN,
        availability = ModelAvailability.AVAILABLE,
    )

    @Test
    fun localStatusLineIsHonest() {
        assertEquals(
            "READY \u00b7 LOCAL \u00b7 GGUF",
            ModelStatus.line(local(), localLoaded = true, modelFileExists = true, networkAvailable = true),
        )
        assertEquals(
            "AVAILABLE \u00b7 LOCAL \u00b7 GGUF",
            ModelStatus.line(local(), localLoaded = false, modelFileExists = true, networkAvailable = true),
        )
        assertEquals(
            "NO MODEL FILE \u00b7 LOCAL",
            ModelStatus.line(local(), localLoaded = false, modelFileExists = false, networkAvailable = true),
        )
    }

    @Test
    fun remoteStatusReflectsNetwork() {
        val d = remote()
        assertEquals("ONLINE \u00b7 REMOTE \u00b7 FREE", ModelStatus.line(d, false, false, true))
        assertEquals("OFFLINE \u00b7 REMOTE \u00b7 FREE", ModelStatus.line(d, false, false, false))
    }

    @Test
    fun nullModelIsUnknown() {
        assertEquals("UNKNOWN \u00b7 NO MODEL", ModelStatus.line(null, false, false, true))
    }

    @Test
    fun quantTagParsing() {
        assertEquals("q4_k_m", ModelStatus.quantTag("qwen-1b-q4_k_m.gguf"))
        assertEquals("f16", ModelStatus.quantTag("model-f16.gguf"))
        assertNull(ModelStatus.quantTag("model.gguf"))
    }
}
