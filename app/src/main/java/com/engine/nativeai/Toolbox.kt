package com.engine.nativeai

import android.content.Context
import java.io.File

/**
 * Standard tool set for the agent + settings inventory (core-hardening C1).
 * One construction path so the UI and the agent always see the same tools.
 */
class Toolbox(
    context: Context,
    engine: NativeEngine,
    registry: ModelRegistry,
    prefs: ModelPreferencesStore,
    executionManager: ExecutionManager,
) {
    /** MiniLM-class embedder dimension (see docs/EMBEDDINGS.md). */
    companion object {
        const val EMBEDDING_DIMENSIONS = 384
    }
    val memory: MemoryDatabase = MemoryDatabase(context)
    val sources: SourceRegistry = SourceRegistry(memory).apply {
        seed(SourceSeedLoader(context).load()) // uBO-style default catalog, first-run only
    }
    // Hybrid search plumbing (roadmap Phase 6): USearch HNSW + MNN embeddings.
    // Both report available only behind real gates (lib probe + model asset +
    // benchmark), so SourceSearch stays BM25-only until then — never faked.
    val vectorIndex: VectorIndex = USearchVectorIndex(
        dimensions = EMBEDDING_DIMENSIONS,
        store = File(context.filesDir, "vectors/vectors.usearch"),
    )
    val embeddingProvider: EmbeddingProvider = MnnEmbeddingProvider(
        modelFile = File(context.filesDir, "models/embeddings/embedding.mnn"),
    )
    val sourceSearch: SourceSearch = SourceSearch(
        memory,
        vectorIndex = vectorIndex,
        embeddingProvider = embeddingProvider,
    )
    val skillManager: SkillManager = SkillManager().apply {
        init(File(context.filesDir, "skills"))
    }
    val sourceUpdater: SourceUpdater = SourceUpdater(
        sources,
        memory,
        textExtractor = TermuxDocumentTextExtractor(
            backend = executionManager.backend(),
            scratchDir = File(context.getExternalFilesDir(null), "pdf"),
        ),
        // Index chunks at ingest once the embedding benchmark gate opens.
        vectorIndex = vectorIndex,
        embeddingProvider = embeddingProvider,
    )
    val tools: ToolRegistry = ToolRegistry().apply {
        register(MemorySearchTool(memory))
        register(CalculatorTool())
        register(SystemInfoTool(engine, memory))
        register(WebSearchTool(LocalFallbackProvider()))
        register(FileSearchTool(context.filesDir))
        register(ModelInfoTool(registry))
        register(SourceSearchTool(sourceSearch))
        register(FinalAnswerTool())
        register(
            TerminalTool(
                backend = executionManager.backend(),
                policy = ExecutionPolicy(allowList = prefs.terminalAllowlist),
            ).apply { setEnabled(prefs.terminalEnabled) },
        )
    }
}
