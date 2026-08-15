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
    val memory: MemoryDatabase = MemoryDatabase(context)
    val sources: SourceRegistry = SourceRegistry(memory).apply {
        seed(SourceSeedLoader(context).load()) // uBO-style default catalog, first-run only
    }
    val sourceSearch: SourceSearch = SourceSearch(memory)
    val sourceUpdater: SourceUpdater = SourceUpdater(
        sources,
        memory,
        textExtractor = TermuxDocumentTextExtractor(
            backend = executionManager.backend(),
            scratchDir = File(context.getExternalFilesDir(null), "pdf"),
        ),
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
