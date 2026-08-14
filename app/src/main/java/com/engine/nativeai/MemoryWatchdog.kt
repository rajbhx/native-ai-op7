package com.engine.nativeai

/**
 * Blueprint Phase 2 watchdog: polls native RSS (/proc/self/statm) against the
 * 1.5 GB AI runtime ceiling (Op7SystemProfile). Reports, never silently
 * exceeds, and can be used by the foreground service to reduce context /
 * abort safely.
 */
class MemoryWatchdog(
    private val engine: NativeEngine,
    private val ceilingBytes: Long = Op7SystemProfile.MEMORY_LIMIT_BYTES,
) {
    data class Snapshot(
        val rssBytes: Long,
        val ceilingBytes: Long,
        val overLimit: Boolean,
        val utilization: Double,
    )

    suspend fun snapshot(): Snapshot {
        val rss = engine.rssBytes()
        return Snapshot(
            rssBytes = rss,
            ceilingBytes = ceilingBytes,
            overLimit = rss > ceilingBytes,
            utilization = if (ceilingBytes > 0) rss.toDouble() / ceilingBytes else 0.0,
        )
    }
}
