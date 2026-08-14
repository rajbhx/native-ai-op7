package com.engine.nativeai

class SystemInfoTool(
    private val engine: NativeEngine,
    private val memory: MemoryDatabase,
) : AgentTool {
    override val name = "system_info"
    override val description =
        "Report engine backend, memory stats and local memory counts. Input: ignored."

    override suspend fun execute(input: String): ToolOutput {
        val backend = engine.backendInfo()
        val stats = engine.memoryStats()
        val counts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            memory.counts()
        }
        return ToolOutput(
            name,
            "backend=${backend.backend} ctx=${backend.nCtx} kv=${backend.typeK}/${backend.typeV} " +
                "threads=${backend.threads} gpuLayers=${backend.gpuLayers} | " +
                "model=${stats.modelBytes / (1024 * 1024)}MB | $counts",
            true,
        )
    }
}
