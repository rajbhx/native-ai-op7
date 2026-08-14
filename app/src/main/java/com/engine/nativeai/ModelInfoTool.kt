package com.engine.nativeai

/** Agent tool exposing the model catalog (spec §11: model_info). */
class ModelInfoTool(private val registry: ModelRegistry) : AgentTool {
    override val name = "model_info"
    override val description =
        "List available models, providers, cost tiers and capabilities. Input: ignored."

    override val permission: ToolPermission
        get() = ToolPermission.READ_ONLY

    override suspend fun execute(input: String): ToolOutput {
        val lines = registry.list().map { d ->
            "${d.id} | ${d.provider} | ${d.kind} | ${d.costTier} | ctx=${d.contextLength ?: "UNKNOWN"} | " +
                "tools=${d.supportsTools} | reasoning=${d.supportsReasoning} | ${d.availability}"
        }
        return if (lines.isEmpty()) {
            ToolOutput(name, "no models registered", false, null)
        } else {
            ToolOutput(name, lines.joinToString("\n"), true)
        }
    }
}
