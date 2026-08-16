package com.engine.nativeai

import java.io.File

/** One installed local GGUF in the on-device model library. */
data class LocalModelEntry(
    val id: String,
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * Multi-model local GGUF library (spec: LOCAL models). Every *.gguf in
 * `filesDir/models` is its own entry/descriptor; the single NativeEngine is
 * shared and only the selected model is loaded. Pure JVM so the scan, id
 * scheme, and sync behavior are unit-tested on CI.
 */
class LocalModelLibrary(private val modelsDir: File) {

    fun scan(): List<LocalModelEntry> {
        if (!modelsDir.exists() || !modelsDir.isDirectory) return emptyList()
        return modelsDir.listFiles { f ->
            f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && !f.name.endsWith(".tmp")
        }?.sortedBy { it.name.lowercase() }?.map { entryFor(it) } ?: emptyList()
    }

    fun entryFor(file: File): LocalModelEntry = LocalModelEntry(
        id = stableId(file.name),
        file = file,
        name = displayName(file.name),
        sizeBytes = file.length(),
        lastModified = file.lastModified(),
    )

    fun resolve(id: String): File? = scan().firstOrNull { it.id == id }?.file

    fun delete(id: String): Boolean {
        val entry = scan().firstOrNull { it.id == id } ?: return false
        return entry.file.delete()
    }

    fun descriptorFor(entry: LocalModelEntry, contextLength: Int = 2048): ModelDescriptor =
        ModelDescriptor(
            id = entry.id,
            displayName = entry.name,
            provider = "local",
            endpoint = "native://llama.cpp",
            modelType = "chat",
            kind = ModelKind.LOCAL,
            costTier = ModelCostTier.FREE,
            availability = ModelAvailability.AVAILABLE,
            contextLength = contextLength,
            maxOutputTokens = 256,
            supportsStreaming = true,
            supportsTools = false,
            supportsStructuredOutput = true,
            mutable = false,
        )

    /** One LOCAL descriptor per installed file (metadata only, no provider). */
    fun descriptors(contextLength: Int = 2048): List<ModelDescriptor> =
        scan().map { descriptorFor(it, contextLength) }

    /**
     * Syncs the library into the registry: one LocalModelProvider per entry
     * sharing the single engine, providers dropped for deleted files, and a
     * `local-llama` placeholder descriptor when no GGUF is installed so the
     * LOCAL section stays visible (backward compat with the original model).
     */
    /** Total bytes of all local GGUF files (measured, for storage UI). */
    fun storageUsedBytes(): Long =
        if (!modelsDir.isDirectory) 0L
        else modelsDir.listFiles { f ->
            f.isFile && f.name.endsWith(".gguf", ignoreCase = true) && !f.name.endsWith(".tmp")
        }?.sumOf { it.length() } ?: 0L

    fun syncInto(
        registry: ModelRegistry,
        engine: NativeEngine,
        nativeLibDir: String,
        contextLength: Int = 2048,
    ) {
        syncInto(registry, contextLength) { entry ->
            LocalModelProvider(
                engine,
                EngineConfig(entry.file.absolutePath, nativeLibDir = nativeLibDir),
                descriptorFor(entry, contextLength),
            )
        }
    }

    /** Registry bookkeeping only; providerFactory builds the live provider per entry. */
    fun syncInto(
        registry: ModelRegistry,
        contextLength: Int = 2048,
        providerFactory: (LocalModelEntry) -> ModelProvider,
    ) {
        val present = scan()
        val presentIds = present.map { it.id }.toSet()
        registry.list()
            .filter { it.kind == ModelKind.LOCAL && it.id !in presentIds }
            .forEach { registry.remove(it.id) }
        present.forEach { entry ->
            val d = descriptorFor(entry, contextLength)
            if (registry.provider(d.id) == null) {
                registry.register(providerFactory(entry))
            } else {
                registry.updateDescriptor(d.id, d)
            }
        }
        if (present.isEmpty() && registry.get(LocalModelProvider.LOCAL_MODEL_ID) == null) {
            registry.addDescriptor(placeholderDescriptor(contextLength))
        }
    }

    companion object {
        const val DEFAULT_MODEL_NAME = "model.gguf"

        /** `model.gguf` keeps the historic `local-llama` id; others are `local-<stem>`. */
        fun stableId(fileName: String): String {
            val stem = fileName.removeSuffix(".gguf").removeSuffix(".GGUF")
            return if (stem == "model") LocalModelProvider.LOCAL_MODEL_ID else "local-$stem"
        }

        fun displayName(fileName: String): String =
            fileName.removeSuffix(".gguf").removeSuffix(".GGUF")

        fun placeholderDescriptor(contextLength: Int = 2048): ModelDescriptor =
            ModelDescriptor(
                id = LocalModelProvider.LOCAL_MODEL_ID,
                displayName = "Local llama.cpp (OP7)",
                provider = "local",
                endpoint = "native://llama.cpp",
                modelType = "chat",
                kind = ModelKind.LOCAL,
                costTier = ModelCostTier.FREE,
                availability = ModelAvailability.AVAILABLE,
                contextLength = contextLength,
                maxOutputTokens = 256,
                supportsStreaming = true,
                supportsTools = false,
                supportsStructuredOutput = true,
                mutable = false,
            )
    }
}
