package com.engine.nativeai

/**
 * Observability contract (spec §16/§21). A DiagnosticsProvider yields one
 * snapshot of engine + process health; future Matrix/GodEye-style sources
 * plug in behind the same interface without touching the UI.
 */
interface DiagnosticsProvider {
    val id: String
    suspend fun snapshot(): DiagnosticsSnapshot
}

data class DiagnosticsSnapshot(
    val metrics: MetricsSnapshot,
    val rssBytes: Long,
    val ceilingBytes: Long,
    val overLimit: Boolean,
    val engineLoaded: Boolean,
    val backend: String?,
)

/** Default provider: RuntimeMetrics + engine RSS/backend against the 1.5 GB ceiling. */
class RuntimeDiagnostics(
    private val metrics: RuntimeMetrics,
    private val engine: NativeEngine,
) : DiagnosticsProvider {

    override val id = "runtime"

    override suspend fun snapshot(): DiagnosticsSnapshot {
        val m = metrics.snapshot()
        var rss = 0L
        var backend: String? = null
        if (engine.isLoaded) {
            try {
                rss = engine.rssBytes()
                backend = engine.backendInfo().backend
            } catch (_: Exception) {
                // stats are best-effort; never crash the diagnostics panel
            }
        } else {
            try {
                rss = engine.rssBytes()
            } catch (_: Exception) {
            }
        }
        val ceiling = Op7SystemProfile.MEMORY_LIMIT_BYTES
        return DiagnosticsSnapshot(
            metrics = m,
            rssBytes = rss,
            ceilingBytes = ceiling,
            overLimit = rss > ceiling,
            engineLoaded = engine.isLoaded,
            backend = backend,
        )
    }
}
