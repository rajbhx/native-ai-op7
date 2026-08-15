package com.engine.nativeai

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * ReAct-style orchestrator following the formal state machine (math spec §3):
 * IDLE -> UNDERSTAND -> PLAN -> EXECUTE -> OBSERVE -> VERIFY
 *   -> (valid) FINALIZE -> STORE
 *   -> (invalid) REPLAN -> PLAN
 *
 * The kernel depends only on ModelRouter/ModelRegistry — never on a specific
 * provider (spec §1). Fallback chain per step: primary -> next candidate
 * (free remote) -> local -> graceful failure, max 3 attempts (spec §14).
 */
class ThinkingAgent(
    private val router: ModelRouter,
    private val registry: ModelRegistry,
    private val memory: MemoryDatabase,
    private val tools: ToolRegistry,
    private val maxIterations: Int = 5,
    private val contextManager: ContextManager = ContextManager(),
    private val networkAvailable: Boolean = true,
    private val allowPaid: Boolean = false,
    private val preferLocal: Boolean = false,
    private val preferredId: String? = null,
    private val systemPromptOverride: String? = null,
) {
    fun run(
        userPrompt: String,
        config: GenerationConfig = GenerationConfig(maxTokens = 256),
    ): Flow<AgentEvent> = flow {
        var state = AgentState.IDLE
        var replan = false
        val observations = mutableListOf<String>()
        val executor = ToolExecutor(tools, memory)
        val taskType = TaskClassifier.classify(userPrompt, networkAvailable)

        repeat(maxIterations) {
            if (state == AgentState.CANCELLED) return@flow

            if (!replan) {
                state = AgentState.UNDERSTAND
                emit(AgentEvent.Stage(state))
            }

            val task = AgentTask(
                prompt = userPrompt,
                taskType = taskType,
                requiredCapabilities = setOf(ModelCapability.TOOLS, ModelCapability.STREAMING),
                contextLength = contextManager.maxTokens,
                networkAvailable = networkAvailable,
                allowPaid = allowPaid,
                preferLocal = preferLocal,
            )

            var descriptor = router.route(task, registry, preferredId = preferredId) ?: run {
                emit(AgentEvent.Error("no model available (mode=${router.mode}, network=$networkAvailable)"))
                return@flow
            }
            emit(AgentEvent.Routed(descriptor.id, descriptor.provider, descriptor.costTier, taskType))

            val memoryCtx = try {
                withContext(Dispatchers.IO) { memory.searchContext(userPrompt, topK = 3) }
            } catch (e: Exception) {
                "" // memory failure must never kill the agent loop (A20/A27)
            }.let {
                if (descriptor.kind == ModelKind.REMOTE) MemoryPrivacyFilter.forRemote(it) else it
            }

            state = AgentState.PLAN
            emit(AgentEvent.Stage(state))

            val sb = StringBuilder()
            var provider = registry.providerFor(descriptor) ?: run {
                emit(AgentEvent.Error("provider for ${descriptor.id} not registered"))
                return@flow
            }

            var attempts = 0
            var generated = false
            while (attempts < 3 && !generated) {
                attempts++
                val ctx = if (descriptor.kind == ModelKind.REMOTE) {
                    MemoryPrivacyFilter.forRemote(memoryCtx)
                } else {
                    memoryCtx
                }
                val userCtx = contextManager.build("", userPrompt, ctx, observations)
                val request = ModelRequest(
                    system = systemPromptOverride ?: systemPrompt(),
                    prompt = ContextAdapter.fit(userCtx.trim(), descriptor.contextLength ?: 2048),
                    maxTokens = config.maxTokens,
                    temperature = config.temperature,
                    stopSequences = config.stopSequences,
                )
                try {
                    provider.stream(request).collect { ev ->
                        when (ev) {
                            is ModelStreamEvent.Token -> {
                                sb.append(ev.text)
                                emit(AgentEvent.Token(ev.text))
                            }
                            is ModelStreamEvent.Reasoning -> emit(AgentEvent.Token(ev.text))
                            is ModelStreamEvent.Done -> generated = true
                            is ModelStreamEvent.Error -> throw IOException(ev.message)
                        }
                    }
                    router.reportSuccess(descriptor.id)
                    generated = true
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    router.reportFailure(descriptor.id, e.message ?: "generation failed")
                    if (attempts >= 3) {
                        emit(AgentEvent.Error("provider ${descriptor.id} failed after $attempts attempts: ${e.message}"))
                        return@flow
                    }
                    val fallback = router.route(task, registry, excludeIds = setOf(descriptor.id))
                    if (fallback == null) {
                        emit(AgentEvent.Error("no fallback model available: ${e.message}"))
                        return@flow
                    }
                    descriptor = fallback
                    provider = registry.providerFor(descriptor) ?: continue
                    emit(AgentEvent.Routed(descriptor.id, descriptor.provider, descriptor.costTier, taskType))
                }
            }
            if (!generated) {
                emit(AgentEvent.Error("generation produced no output"))
                return@flow
            }

            val action = ActionParser.parse(sb.toString())
            if (action == null) {
                // No structured action: treat the generated text as the answer.
                state = AgentState.FINALIZE
                emit(AgentEvent.Stage(state))
                val answer = sb.toString().trim()
                state = AgentState.STORE
                emit(AgentEvent.Stage(state))
                withContext(Dispatchers.IO) {
                    memory.storeExperience(
                        userPrompt, "no tool", "final_answer", answer.take(500),
                        answer.isNotBlank(),
                    )
                }
                emit(AgentEvent.Final(answer))
                return@flow
            }

            state = AgentState.EXECUTE
            emit(AgentEvent.Stage(state))
            emit(AgentEvent.ToolCall(action.name, action.input))
            val result = executor.execute(action.name, action.input)
            observations.add("${action.name}: ${result.output.take(400)}")

            state = AgentState.OBSERVE
            emit(AgentEvent.Stage(state))
            emit(AgentEvent.Observation(action.name, result.output))

            state = AgentState.VERIFY
            emit(AgentEvent.Stage(state))
            val verification = Verifier.verifyTool(result.output, result.ok, action.name)
            emit(AgentEvent.Verification(action.name, verification.passed))

            if (action.name == "final_answer") {
                state = AgentState.FINALIZE
                emit(AgentEvent.Stage(state))
                state = AgentState.STORE
                emit(AgentEvent.Stage(state))
                withContext(Dispatchers.IO) {
                    memory.storeExperience(
                        userPrompt, "agent", "final_answer",
                        result.output.take(500), result.ok,
                    )
                }
                emit(AgentEvent.Final(result.output))
                return@flow
            }

            if (!verification.passed) {
                state = AgentState.REPLAN
                replan = true
                emit(AgentEvent.Stage(state))
            } else {
                replan = false
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
