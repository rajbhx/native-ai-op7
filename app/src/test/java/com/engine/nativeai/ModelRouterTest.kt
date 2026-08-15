package com.engine.nativeai

import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelRouterTest {

    private fun fakeProvider(descriptor: ModelDescriptor): ModelProvider = object : ModelProvider {
        override val descriptor: ModelDescriptor = descriptor
        override fun stream(request: ModelRequest) =
            flowOf(ModelStreamEvent.Token("ok") as ModelStreamEvent)
        override suspend fun complete(request: ModelRequest) =
            ModelResult("ok", 1, 1, descriptor.id)
        override suspend fun health() = ProviderHealth(true)
    }

    private fun registry(local: Boolean = true, freeRemote: Boolean = false, paid: Boolean = false): ModelRegistry {
        val r = ModelRegistry()
        if (local) {
            r.register(fakeProvider(ModelDescriptor(
                id = "local", displayName = "Local", provider = "local", endpoint = "",
                modelType = "chat", kind = ModelKind.LOCAL, costTier = ModelCostTier.FREE,
                availability = ModelAvailability.AVAILABLE, contextLength = 2048, mutable = false,
            )))
        }
        if (freeRemote) {
            r.register(fakeProvider(ModelDescriptor(
                id = "remote-free", displayName = "Free Remote", provider = "remote", endpoint = "https://x",
                modelType = "chat", kind = ModelKind.REMOTE, costTier = ModelCostTier.FREE,
                availability = ModelAvailability.AVAILABLE, contextLength = 32000,
            )))
        }
        if (paid) {
            r.register(fakeProvider(ModelDescriptor(
                id = "remote-paid", displayName = "Paid Remote", provider = "remote", endpoint = "https://x",
                modelType = "chat", kind = ModelKind.REMOTE, costTier = ModelCostTier.PAID,
                availability = ModelAvailability.AVAILABLE, contextLength = 128000,
            )))
        }
        return r
    }

    private fun task(
        network: Boolean = true,
        type: TaskType = TaskType.CHAT,
        allowPaid: Boolean = false,
        context: Int = 2048,
    ) = AgentTask(
        prompt = "test", taskType = type, contextLength = context,
        networkAvailable = network, allowPaid = allowPaid,
    )

    @Test
    fun offlineOnlySelectsLocal() {
        val router = ModelRouter(RoutingMode.OFFLINE_ONLY)
        val d = router.route(task(), registry(local = true, freeRemote = true))
        assertNotNull(d)
        assertEquals(ModelKind.LOCAL, d?.kind)
    }

    @Test
    fun freeFirstPrefersFreeRemote() {
        val router = ModelRouter(RoutingMode.FREE_FIRST)
        val d = router.route(task(network = true), registry(local = true, freeRemote = true))
        assertNotNull(d)
        assertEquals("remote-free", d?.id)
    }

    @Test
    fun freeFirstFallsBackToLocalWithoutNetwork() {
        val router = ModelRouter(RoutingMode.FREE_FIRST)
        val d = router.route(task(network = false), registry(local = true, freeRemote = true))
        assertNotNull(d)
        assertEquals(ModelKind.LOCAL, d?.kind)
    }

    @Test
    fun paidRejectedWithoutPermission() {
        val router = ModelRouter(RoutingMode.FREE_FIRST)
        assertNull(router.route(task(network = true), registry(local = false, paid = true)))
    }

    @Test
    fun paidAllowedWithPermission() {
        val router = ModelRouter(RoutingMode.FREE_FIRST)
        val d = router.route(
            task(network = true, allowPaid = true),
            registry(local = false, paid = true),
        )
        assertNotNull(d)
        assertEquals("remote-paid", d?.id)
    }

    @Test
    fun unhealthyProviderIsSkipped() {
        val router = ModelRouter(RoutingMode.FREE_FIRST)
        router.reportFailure("remote-free", "boom")
        router.reportFailure("remote-free", "boom")
        val d = router.route(task(network = true), registry(local = true, freeRemote = true))
        assertNotNull(d)
        assertEquals(ModelKind.LOCAL, d?.kind)
    }

    @Test
    fun excludedIdIsSkipped() {
        val router = ModelRouter(RoutingMode.FREE_FIRST)
        val d = router.route(
            task(network = true),
            registry(local = true, freeRemote = true),
            excludeIds = setOf("remote-free"),
        )
        assertNotNull(d)
        assertEquals(ModelKind.LOCAL, d?.kind)
    }

    @Test
    fun hybridRoutesSimpleChatToLocal() {
        val router = ModelRouter(RoutingMode.HYBRID)
        val d = router.route(task(network = true), registry(local = true, freeRemote = true))
        assertNotNull(d)
        assertEquals(ModelKind.LOCAL, d?.kind)
    }

    @Test
    fun hybridRoutesCodingToFreeRemoteWhenLocalCantFit() {
        val router = ModelRouter(RoutingMode.HYBRID)
        val d = router.route(
            task(network = true, type = TaskType.CODING, context = 4096),
            registry(local = true, freeRemote = true),
        )
        assertNotNull(d)
        assertEquals("remote-free", d?.id)
    }

    @Test
    fun explicitPreferredRemoteWinsDespiteHealthMarks() {
        val router = ModelRouter(RoutingMode.HYBRID)
        router.reportFailure("remote-free", "429 rate limited")
        router.reportFailure("remote-free", "429 rate limited")
        val d = router.route(
            task(network = true),
            registry(local = true, freeRemote = true),
            preferredId = "remote-free",
        )
        assertNotNull(d)
        assertEquals("remote-free", d?.id)
    }

    @Test
    fun explicitPreferredRemoteWinsForSimpleChatInHybrid() {
        val router = ModelRouter(RoutingMode.HYBRID)
        val d = router.route(
            task(network = true),
            registry(local = true, freeRemote = true),
            preferredId = "remote-free",
        )
        assertNotNull(d)
        assertEquals("remote-free", d?.id)
    }

    @Test
    fun explicitPreferredPaidNeverAllowedInFreeOnly() {
        val router = ModelRouter(RoutingMode.FREE_ONLY)
        val d = router.route(
            task(network = true, allowPaid = true),
            registry(local = true, freeRemote = true, paid = true),
            preferredId = "remote-paid",
        )
        assertNotNull(d)
        assertEquals("remote-free", d?.id)
    }
}
