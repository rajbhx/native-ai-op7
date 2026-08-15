package com.engine.nativeai

/** Per-tool accumulated timing/failure stats. */
data class ToolMetric(
    val calls: Int = 0,
    val totalMs: Long = 0L,
    val failures: Int = 0,
)

/** Point-in-time view of everything RuntimeMetrics tracks. */
data class MetricsSnapshot(
    val modelLoadMs: Long? = null,
    val firstTokenMs: Long? = null,
    val lastRunTokens: Int = 0,
    val lastRunDurationMs: Long = 0L,
    val tokensPerSec: Double? = null,
    val tools: Map<String, ToolMetric> = emptyMap(),
    val errors: Int = 0,
    val retries: Int = 0,
    val serviceRestarts: Int = 0,
    val droppedFrames: Long = 0L,
    val jankyFrames: Long = 0L,
)

/**
 * In-memory runtime metrics (spec §16 observability). Every value is a real
 * measurement recorded by the engine/trace layer — nothing is fabricated.
 * v1 keeps metrics in memory; a persisted diagnostics store is a later phase.
 */
class RuntimeMetrics {
    private var modelLoadMs: Long? = null
    private var firstTokenMs: Long? = null
    private var lastRunTokens = 0
    private var lastRunDurationMs = 0L
    private var tokensPerSec: Double? = null
    private val toolMetrics = LinkedHashMap<String, ToolMetric>()
    private var errors = 0
    private var retries = 0
    private var serviceRestarts = 0
    private var droppedFrames = 0L
    private var jankyFrames = 0L

    fun recordModelLoad(durationMs: Long) {
        modelLoadMs = durationMs
    }

    fun recordFirstToken(durationMs: Long) {
        if (firstTokenMs == null) firstTokenMs = durationMs
    }

    fun recordRun(tokens: Int, durationMs: Long) {
        lastRunTokens = tokens
        lastRunDurationMs = durationMs
        tokensPerSec = if (durationMs > 0) tokens * 1000.0 / durationMs else null
    }

    fun recordTool(tool: String, durationMs: Long, ok: Boolean) {
        val cur = toolMetrics[tool] ?: ToolMetric()
        toolMetrics[tool] = ToolMetric(
            calls = cur.calls + 1,
            totalMs = cur.totalMs + durationMs,
            failures = cur.failures + if (ok) 0 else 1,
        )
    }

    fun recordError() {
        errors++
    }

    fun recordRetry() {
        retries++
    }

    fun recordServiceRestart() {
        serviceRestarts++
    }

    fun recordFrames(dropped: Long, janky: Long) {
        droppedFrames += dropped
        jankyFrames += janky
    }

    fun reset() {
        modelLoadMs = null
        firstTokenMs = null
        lastRunTokens = 0
        lastRunDurationMs = 0L
        tokensPerSec = null
        toolMetrics.clear()
        errors = 0
        retries = 0
        serviceRestarts = 0
        droppedFrames = 0L
        jankyFrames = 0L
    }

    fun snapshot(): MetricsSnapshot = MetricsSnapshot(
        modelLoadMs = modelLoadMs,
        firstTokenMs = firstTokenMs,
        lastRunTokens = lastRunTokens,
        lastRunDurationMs = lastRunDurationMs,
        tokensPerSec = tokensPerSec,
        tools = toolMetrics.toMap(),
        errors = errors,
        retries = retries,
        serviceRestarts = serviceRestarts,
        droppedFrames = droppedFrames,
        jankyFrames = jankyFrames,
    )
}
