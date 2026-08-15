package com.engine.nativeai

/**
 * Re-hydrates remote providers at startup and after catalog refreshes.
 * Discovery and the catalog cache store descriptors only; providers are
 * stateless OpenAI-compatible adapters rebuilt from descriptor + runtime
 * key at any time. Keys stay memory-only; blank keys use the Zen anonymous
 * free tier.
 */
object RemoteProviderBootstrap {

    /** Registers a provider for every remote descriptor that lacks one. */
    fun registerRemoteProviders(registry: ModelRegistry, providerRegistry: ProviderRegistry) {
        registry.list()
            .filter { it.kind == ModelKind.REMOTE && registry.provider(it.id) == null }
            .forEach { d ->
                registry.register(
                    OpenAICompatibleProvider(
                        descriptor = d,
                        apiKey = providerRegistry.apiKey(d.provider),
                    ),
                )
            }
    }

    /**
     * Makes a persisted model selection routable again after a process
     * restart, before discovery has run: existing descriptors get providers,
     * and a missing remote id is re-seeded as a Zen descriptor (never for
     * the local model).
     */
    fun ensurePersistedSelection(
        registry: ModelRegistry,
        providerRegistry: ProviderRegistry,
        id: String?,
    ) {
        if (id.isNullOrBlank() || id.startsWith("local-")) return
        if (registry.get(id) == null) {
            registry.addDescriptor(ModelCatalog.zenDescriptor(id))
        }
        registerRemoteProviders(registry, providerRegistry)
    }
}
