package com.engine.nativeai

import android.view.Choreographer

/**
 * BlockCanary-style dropped-frame counter without any dependency: samples
 * Choreographer frame deltas, counts dropped frames vs 60 fps, and flushes
 * totals into RuntimeMetrics on demand (start/stop lifecycle guarded).
 */
class FrameJankMonitor(private val metrics: RuntimeMetrics) {

    private var lastFrameNanos = 0L
    private var dropped = 0L
    private var janky = 0L
    private var running = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (running) {
                if (lastFrameNanos != 0L) {
                    val deltaMs = (frameTimeNanos - lastFrameNanos) / 1_000_000L
                    if (deltaMs > 16L) {
                        dropped += (deltaMs + 15L) / 16L - 1L
                        if (deltaMs > 100L) janky++
                    }
                }
                lastFrameNanos = frameTimeNanos
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    fun start() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        running = false
    }

    fun flush() {
        metrics.recordFrames(dropped, janky)
        dropped = 0L
        janky = 0L
    }
}
