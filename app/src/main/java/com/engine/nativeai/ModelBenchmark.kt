package com.engine.nativeai

import kotlinx.coroutines.flow.first

/**
 * Controlled model benchmarks (spec §23). Results are measured, stored
 * locally, and never used to claim one model is better without evidence.
 * Local and remote metrics are tracked separately (spec §22) — local
 * tokens/sec is never compared directly to remote latency.
 */
class ModelBenchmark(
    private val registry: ModelRegistry,
    private val memory: MemoryDatabase? = null,
) {
    data class BenchmarkResult(
        val providerId: String,
        val category: String,
        val tokens: Int,
        val durationMs: Long,
        val tokensPerSec: Double?,
        val firstTokenMs: Long?,
        val remoteLatencyMs: Long?,
        val ok: Boolean,
        val error: String? = null,
    )

    private val prompts: Map<String, String> = linkedMapOf(
        "reasoning" to "If a train leaves at 9:00 and travels 60 km/h for 45 minutes, how far does it go? Explain briefly.",
        "coding" to "Write a small Kotlin function that returns the max of three integers.",
        "summarization" to "Summarize in one sentence: memory should stay bounded, verified, and privacy-safe.",
        "tool_use" to "Use the calculator tool to compute (7 + 5) * 3.",
        "instruction_following" to "Reply with exactly the word: pineapple",
        "context" to "List the numbers 1 through 20 separated by commas.",
    )

    suspend fun runAll(): List<BenchmarkResult> =
        registry.list().filter { registry.provider(it.id) != null }
            .flatMap { run(it.id) }

    suspend fun run(providerId: String): List<BenchmarkResult> {
        val provider = registry.provider(providerId) ?: return emptyList()
        return prompts.map { (category, prompt) ->
            val request = ModelRequest(prompt = prompt, maxTokens = 32)
            if (provider.descriptor.kind == ModelKind.LOCAL) {
                runLocal(provider, category, request)
            } else {
                runRemote(provider, category, request)
            }
        }.also { results ->
            memory?.let { m ->
                results.forEach { r ->
                    m.storeToolResult(
                        "benchmark:${r.category}",
                        providerId,
                        "tokens=${r.tokens} dur=${r.durationMs}ms " +
                            "tps=${r.tokensPerSec?.let { "%.1f".format(it) } ?: "n/a"} " +
                            "first=${r.firstTokenMs ?: r.remoteLatencyMs ?: 0}ms ok=${r.ok}",
                        r.ok,
                    )
                }
            }
        }
    }

    private suspend fun runLocal(
        provider: ModelProvider,
        category: String,
        request: ModelRequest,
    ): BenchmarkResult {
        val started = System.currentTimeMillis()
        var firstTokenMs: Long? = null
        var tokenCount = 0
        return try {
            provider.stream(request).collect { ev ->
                if (ev is ModelStreamEvent.Token) {
                    tokenCount++
                    if (firstTokenMs == null) firstTokenMs = System.currentTimeMillis() - started
                }
            }
            val duration = System.currentTimeMillis() - started
            BenchmarkResult(
                providerId = provider.descriptor.id,
                category = category,
                tokens = tokenCount,
                durationMs = duration,
                tokensPerSec = if (duration > 0) tokenCount * 1000.0 / duration else null,
                firstTokenMs = firstTokenMs,
                remoteLatencyMs = null,
                ok = tokenCount > 0,
            )
        } catch (e: Exception) {
            BenchmarkResult(
                providerId = provider.descriptor.id, category = category,
                tokens = tokenCount, durationMs = System.currentTimeMillis() - started,
                tokensPerSec = null, firstTokenMs = firstTokenMs, remoteLatencyMs = null,
                ok = false, error = e.message,
            )
        }
    }

    private suspend fun runRemote(
        provider: ModelProvider,
        category: String,
        request: ModelRequest,
    ): BenchmarkResult {
        val started = System.currentTimeMillis()
        return try {
            val result = provider.complete(request)
            BenchmarkResult(
                providerId = provider.descriptor.id,
                category = category,
                tokens = result.tokens,
                durationMs = result.durationMs,
                tokensPerSec = null, // remote metrics are latency, not local tps
                firstTokenMs = null,
                remoteLatencyMs = result.durationMs,
                ok = result.text.isNotBlank(),
            )
        } catch (e: Exception) {
            BenchmarkResult(
                providerId = provider.descriptor.id, category = category,
                tokens = 0, durationMs = System.currentTimeMillis() - started,
                tokensPerSec = null, firstTokenMs = null, remoteLatencyMs = null,
                ok = false, error = e.message,
            )
        }
    }
}
