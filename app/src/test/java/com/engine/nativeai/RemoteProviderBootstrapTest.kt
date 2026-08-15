package com.engine.nativeai

import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class RemoteProviderBootstrapTest {

    private fun fakeProvider(descriptor: ModelDescriptor): ModelProvider = object : ModelProvider {
        override val descriptor: ModelDescriptor = descriptor
        override fun stream(request: ModelRequest) =
            flowOf(ModelStreamEvent.Token("ok") as ModelStreamEvent)
        override suspend fun complete(request: ModelRequest) =
            ModelResult("ok", 1, 1, descriptor.id)
        override suspend fun health() = ProviderHealth(true)
    }

    private fun remoteDescriptor(id: String) = ModelDescriptor(
        id = id,
        displayName = id,
        provider = ModelCatalog.ZEN_PROVIDER,
        endpoint = ModelCatalog.ZEN_BASE_URL,
        modelType = "chat",
        kind = ModelKind.REMOTE,
        costTier = ModelCostTier.FREE,
        availability = ModelAvailability.UNKNOWN,
        supportsStreaming = true,
        mutable = true,
    )

    @Test
    fun registersProvidersForEveryRemoteDescriptorWithoutOne() {
        val registry = ModelRegistry()
        registry.addDescriptor(remoteDescriptor("big-pickle"))
        registry.addDescriptor(remoteDescriptor("deepseek-v4-flash-free"))
        assertNull(registry.provider("big-pickle"))

        RemoteProviderBootstrap.registerRemoteProviders(registry, ProviderRegistry())

        assertNotNull(registry.provider("big-pickle"))
        assertNotNull(registry.provider("deepseek-v4-flash-free"))
        assertEquals(
            ModelCatalog.ZEN_PROVIDER,
            registry.provider("big-pickle")!!.descriptor.provider,
        )
    }

    @Test
    fun leavesExistingAndLocalProvidersUntouched() {
        val registry = ModelRegistry()
        val local = fakeProvider(
            remoteDescriptor("local-llama").copy(kind = ModelKind.LOCAL, provider = "local"),
        )
        registry.register(local)
        val remote = remoteDescriptor("big-pickle")
        registry.register(fakeProvider(remote))

        RemoteProviderBootstrap.registerRemoteProviders(registry, ProviderRegistry())

        assertSame(local, registry.provider("local-llama"))
        assertSame(remote, registry.provider("big-pickle")?.descriptor)
    }

    @Test
    fun ensurePersistedSelectionReSeedsMissingRemoteId() {
        val registry = ModelRegistry()

        RemoteProviderBootstrap.ensurePersistedSelection(
            registry,
            ProviderRegistry(),
            "nemotron-3.5-lightning-free",
        )

        val d = registry.get("nemotron-3.5-lightning-free")
        assertNotNull(d)
        assertEquals(ModelKind.REMOTE, d?.kind)
        assertEquals(ModelCatalog.ZEN_PROVIDER, d?.provider)
        assertNotNull(registry.provider("nemotron-3.5-lightning-free"))
    }

    @Test
    fun ensurePersistedSelectionIgnoresLibraryLocalIds() {
        val registry = ModelRegistry()

        RemoteProviderBootstrap.ensurePersistedSelection(
            registry,
            ProviderRegistry(),
            "local-qwen2-0.5b",
        )

        assertNull(registry.get("local-qwen2-0.5b"))
        assertEquals(0, registry.list().size)
    }

    @Test
    fun ensurePersistedSelectionIgnoresLocalModel() {
        val registry = ModelRegistry()

        RemoteProviderBootstrap.ensurePersistedSelection(
            registry,
            ProviderRegistry(),
            LocalModelProvider.LOCAL_MODEL_ID,
        )

        assertNull(registry.get(LocalModelProvider.LOCAL_MODEL_ID))
        assertEquals(0, registry.list().size)
    }

    @Test
    fun ensurePersistedSelectionKeepsExistingDescriptorAndRegistersProvider() {
        val registry = ModelRegistry()
        registry.addDescriptor(remoteDescriptor("deepseek-v4-flash-free"))

        RemoteProviderBootstrap.ensurePersistedSelection(
            registry,
            ProviderRegistry(),
            "deepseek-v4-flash-free",
        )

        assertEquals("deepseek-v4-flash-free", registry.get("deepseek-v4-flash-free")?.id)
        assertNotNull(registry.provider("deepseek-v4-flash-free"))
    }
}
