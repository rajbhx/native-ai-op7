package com.engine.nativeai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelDiscoveryServiceTest {

    // Shape captured from https://opencode.ai/zen/v1/models (live endpoint).
    private val sampleList = """
        {"object":"list","data":[
          {"id":"big-pickle","object":"model","created":1786723381,"owned_by":"opencode"},
          {"id":"deepseek-v4-flash-free","object":"model","created":1786723381,"owned_by":"opencode"},
          {"id":"mimo-v2.5-free","object":"model","created":1786723381,"owned_by":"opencode"},
          {"id":"gpt-5.6-sol","object":"model","created":1786723381,"owned_by":"opencode"}
        ]}
    """.trimIndent()

    private fun descriptorsFrom(json: String): List<ModelDescriptor> {
        val arr = org.json.JSONObject(json).getJSONArray("data")
        val out = mutableListOf<ModelDescriptor>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val id = item.getString("id")
            out += ModelDescriptor(
                id = id, displayName = ModelDiscoveryService.prettify(id),
                provider = "opencode-zen", endpoint = "https://opencode.ai/zen/v1",
                modelType = "chat", kind = ModelKind.REMOTE,
                costTier = ModelDiscoveryService.costTierFor(id, null),
                availability = ModelAvailability.UNKNOWN,
                contextLength = null, supportsStreaming = true,
                supportsTools = false, supportsVision = false,
                supportsReasoning = false, supportsStructuredOutput = true,
                mutable = true,
                lastUpdated = 1_700_000_000_000L,
            )
        }
        return out
    }

    @Test
    fun parsesOpenAiCompatibleModelList() {
        val parsed = descriptorsFrom(sampleList)
        assertEquals(4, parsed.size)
        assertEquals("big-pickle", parsed[0].id)
        assertEquals("Big Pickle", parsed[0].displayName)
        assertEquals("Deepseek V4 Flash Free", parsed[1].displayName)
    }

    @Test
    fun freeTierDetectedBySuffixOnly() {
        assertEquals(ModelCostTier.FREE, ModelDiscoveryService.costTierFor("deepseek-v4-flash-free", null))
        assertEquals(ModelCostTier.FREE, ModelDiscoveryService.costTierFor("big-pickle", null))
        assertEquals(ModelCostTier.UNKNOWN, ModelDiscoveryService.costTierFor("gpt-5.6-sol", null))
    }

    @Test
    fun unknownCapabilitiesStayUnknown() {
        val d = descriptorsFrom(sampleList).first { it.id == "big-pickle" }
        assertNull(d.contextLength)
        assertNull(d.codingScore)
        assertNull(d.reasoningScore)
        assertNull(d.speedScore)
        assertEquals(ModelAvailability.UNKNOWN, d.availability)
        assertTrue(!d.supportsTools)
        assertTrue(!d.supportsVision)
        assertTrue(!d.supportsReasoning)
    }

    @Test
    fun existingMetadataSurvivesDiscovery() {
        val existing = descriptorsFrom(sampleList).first { it.id == "big-pickle" }.copy(
            contextLength = 128_000,
            supportsTools = true,
            costTier = ModelCostTier.PAID, // configured tier wins
        )
        val discovered = descriptorsFrom(sampleList).first { it.id == "big-pickle" }
        val merged = discovered.copy(
            contextLength = existing.contextLength,
            supportsTools = existing.supportsTools,
            costTier = ModelDiscoveryService.costTierFor(discovered.id, existing),
        )
        assertEquals(128_000, merged.contextLength)
        assertTrue(merged.supportsTools)
        assertEquals(ModelCostTier.PAID, merged.costTier)
    }

    @Test
    fun upsertRemoteDedupesAndPreservesLocal() {
        val dir = File(System.getProperty("java.io.tmpdir"), "registry-test-${System.nanoTime()}")
        val registry = ModelRegistry(File(dir, "catalog.json"))
        registry.addDescriptor(ModelDescriptor(
            id = "local", displayName = "Local", provider = "local", endpoint = "",
            modelType = "chat", kind = ModelKind.LOCAL, costTier = ModelCostTier.FREE,
            availability = ModelAvailability.AVAILABLE, contextLength = 2048, mutable = false,
        ))
        val discovered = descriptorsFrom(sampleList) +
            descriptorsFrom(sampleList) // duplicates on purpose
        val added = registry.upsertRemote(discovered)
        assertEquals(4, added) // 4 unique new ids; duplicates + local ignored
        assertEquals(ModelKind.LOCAL, registry.get("local")?.kind)
        assertEquals(ModelKind.REMOTE, registry.get("big-pickle")?.kind)
        assertTrue(registry.get("big-pickle")!!.lastUpdated > 0)
    }

    @Test
    fun duplicateIdsDoNotDuplicate() {
        val a = descriptorsFrom(sampleList)
        val registry = ModelRegistry()
        registry.upsertRemote(a)
        registry.upsertRemote(a)
        assertEquals(4, registry.list().size)
    }

    @Test
    fun catalogSaveLoadRoundTripsLastRefresh() {
        val dir = File(System.getProperty("java.io.tmpdir"), "registry-test-${System.nanoTime()}")
        val file = File(dir, "catalog.json")
        val r1 = ModelRegistry(file)
        r1.upsertRemote(descriptorsFrom(sampleList))
        r1.lastRefreshMs = 1_700_000_000_000L
        assertTrue(r1.saveCatalog())

        val r2 = ModelRegistry(file)
        assertEquals(4, r2.loadCatalog())
        assertEquals(1_700_000_000_000L, r2.lastRefreshMs)
        assertNotNull(r2.get("deepseek-v4-flash-free"))
        assertEquals(ModelCostTier.FREE, r2.get("deepseek-v4-flash-free")?.costTier)
    }
}
