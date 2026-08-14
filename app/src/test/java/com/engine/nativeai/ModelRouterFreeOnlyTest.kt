package com.engine.nativeai

import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ModelRouterFreeOnlyTest {

    private fun fakeProvider(descriptor: ModelDescriptor): ModelProvider = object : ModelProvider {
        override val descriptor: ModelDescriptor = descriptor
        override fun stream(request: ModelRequest) =
            flowOf(ModelStreamEvent.Token("ok") as ModelStreamEvent)
        override suspend fun complete(request: ModelRequest) =
            ModelResult("ok", 1, 1, descriptor.id)
        override suspend fun health() = ProviderHealth(true)
    }

    private fun registry(
        local: Boolean = true,
        freeRemote: Boolean = false,
        paid: Boolean = false,
        freeRemoteAvailable: Boolean = true,
    ): ModelRegistry {
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
                availability = if (freeRemoteAvailable) ModelAvailability.AVAILABLE else ModelAvailability.UNAVAILABLE,
                contextLength = 32000,
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

    private fun task(network: Boolean = true, allowPaid: Boolean = false) = AgentTask(
        prompt = "test", taskType = TaskType.CHAT,
        networkAvailable = network, allowPaid = allowPaid,
    )

    @Test
    fun freeOnlyPrefersFreeRemoteOverLocal() {
        val router = ModelRouter(RoutingMode.FREE_ONLY)
        val d = router.route(task(), registry(local = true, freeRemote = true, paid = true))
        assertNotNull(d)
        assertEquals("remote-free", d?.id)
    }

    @Test
    fun freeOnlyNeverSelectsPaid() {
        val router = ModelRouter(RoutingMode.FREE_ONLY)
        val d = router.route(
            task(allowPaid = true), // even explicit permission must not auto-select paid
            registry(local = false, freeRemote = false, paid = true),
        )
        assertNull(d)
    }

    @Test
    fun freeOnlyFallsBackToLocalWhenFreeRemoteUnavailable() {
        val router = ModelRouter(RoutingMode.FREE_ONLY)
        val d = router.route(
            task(),
            registry(local = true, freeRemote = true, freeRemoteAvailable = false),
        )
        assertNotNull(d)
        assertEquals(ModelKind.LOCAL, d?.kind)
    }

    @Test
    fun freeOnlyFallsBackToLocalWithoutNetwork() {
        val router = ModelRouter(RoutingMode.FREE_ONLY)
        val d = router.route(task(network = false), registry(local = true, freeRemote = true))
        assertNotNull(d)
        assertEquals(ModelKind.LOCAL, d?.kind)
    }

    @Test
    fun freeOnlyReturnsNullWhenNothingAllowed() {
        val router = ModelRouter(RoutingMode.FREE_ONLY)
        assertNull(router.route(task(), registry(local = false, freeRemote = false)))
    }
}
