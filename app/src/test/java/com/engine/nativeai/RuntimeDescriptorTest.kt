package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Test

/** Runtime metadata must survive persistence and never be assumed (S6). */
class RuntimeDescriptorTest {

    @Test
    fun runtimeSurvivesJsonRoundTrip() {
        val d = ModelDescriptor(
            id = "mnn-model", displayName = "MNN Model", provider = "local", endpoint = "native://mnn",
            modelType = "chat", kind = ModelKind.LOCAL, runtime = RuntimeKind.MNN,
            costTier = ModelCostTier.FREE, availability = ModelAvailability.UNKNOWN,
        )
        val restored = ModelDescriptor.fromJson(d.toJson())
        assertEquals(RuntimeKind.MNN, restored.runtime)
        assertEquals(ModelKind.LOCAL, restored.kind)
    }

    @Test
    fun missingRuntimeParsesAsUnknownNotCrash() {
        val d = ModelDescriptor(
            id = "m", displayName = "M", provider = "p", endpoint = "e",
            modelType = "chat", kind = ModelKind.REMOTE,
            costTier = ModelCostTier.FREE, availability = ModelAvailability.UNKNOWN,
        )
        assertEquals(RuntimeKind.UNKNOWN, d.runtime)
        val json = d.toJson()
        json.remove("runtime") // old persisted catalogs have no runtime key
        assertEquals(RuntimeKind.UNKNOWN, ModelDescriptor.fromJson(json).runtime)
    }

    @Test
    fun apiRuntimeForRemoteModels() {
        val d = ModelDescriptor(
            id = "big-pickle", displayName = "Big Pickle", provider = "opencode", endpoint = "https://x",
            modelType = "chat", kind = ModelKind.REMOTE, runtime = RuntimeKind.API,
            costTier = ModelCostTier.FREE, availability = ModelAvailability.UNKNOWN,
        )
        assertEquals(RuntimeKind.API, d.runtime)
        assertEquals("API", d.runtime.name)
    }
}
