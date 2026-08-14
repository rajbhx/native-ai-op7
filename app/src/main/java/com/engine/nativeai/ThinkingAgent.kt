package com.engine.nativeai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * ReAct-style orchestrator (spec §11): memory retrieval -> model -> tool
 * selection (JSON) -> execution -> observation -> loop -> final answer.
 * Structured actions only; max iterations; every step is streamed as events.
 */
class ThinkingAgent(
    private val engine: NativeEngine,
    private val memory: MemoryDatabase,
    private val tools: ToolRegistry,
    private val maxIterations: Int = 5,
    private val contextManager: ContextManager = ContextManager(),
) {
    fun run(
        userPrompt: String,
        config: GenerationConfig = GenerationConfig(maxTokens = 256),
    ): Flow<AgentEvent> = flow {
        var state = AgentState.THINKING
        val observations = mutableListOf<String>()
        val executor = ToolExecutor(tools, memory)

        repeat(maxIterations) {
            if (state == AgentState.CANCELLED) return@flow

            val memoryCtx = withContext(Dispatchers.IO) {
                memory.searchContext(userPrompt, topK = 3)
            }
            val prompt = contextManager.build(
                systemPrompt(),
                userPrompt,
                memoryCtx,
                observations,
            )

            state = AgentState.THINKING
            val sb = StringBuilder()
            engine.generateStream(prompt, config).collect { token ->
                sb.append(token)
                emit(AgentEvent.Token(token))
            }

            val action = ActionParser.parse(sb.toString())
            if (action == null) {
                // No structured action: treat the generated text as the answer.
                state = AgentState.FINAL
                val answer = sb.toString().trim()
                withContext(Dispatchers.IO) {
                    memory.storeExperience(
                        userPrompt, "no tool", "final_answer", answer.take(500),
                        answer.isNotBlank(),
                    )
                }
                emit(AgentEvent.Final(answer))
                return@flow
            }

            emit(AgentEvent.ToolCall(action.name, action.input))
            state = AgentState.TOOL_CALL
            val result = executor.execute(action.name, action.input)
            observations.add("${action.name}: ${result.output.take(400)}")
            state = AgentState.OBSERVING
            emit(AgentEvent.Observation(action.name, result.output))

            if (action.name == "final_answer") {
                state = AgentState.FINAL
                withContext(Dispatchers.IO) {
                    memory.storeExperience(
                        userPrompt, "agent", "final_answer",
                        result.output.take(500), result.ok,
                    )
                }
                emit(AgentEvent.Final(result.output))
                return@flow
            }
        }
        emit(AgentEvent.Error("max iterations ($maxIterations) reached without a final answer"))
    }

    private fun systemPrompt(): String =
        """
        You are a local resource-constrained agent running on a phone.
        Think step by step, then reply with ONLY one JSON object and no prose:
        {"action": "<tool_name>", "input": "<tool input>"}
        Use final_answer when you have enough information.
        Available tools:
        ${tools.descriptions()}
        """.trimIndent()
}
