package com.engine.nativeai

import org.json.JSONObject

/**
 * Structured benchmark output (Phase 7/8 harness): one JSONL line per
 * measured run, emitted to logcat under [TAG]. Pure JSON building so JVM
 * tests can exercise it; the Android side (MainActivity bench hook) is what
 * logs the lines via android.util.Log.
 */
object BenchmarkReporter {
    const val TAG = "NATIVEAI_BENCH"

    /** One measured inference run (mirrors ModelBenchmark.BenchmarkResult plus engine state). */
    data class Report(
        val runId: String,
        val providerId: String,
        val category: String,
        val tokens: Int,
        val durationMs: Long,
        val tokensPerSec: Double?,
        val firstTokenMs: Long?,
        val rssBytes: Long?,
        val kvTypeK: String?,
        val kvTypeV: String?,
        val nCtx: Int?,
        val threads: Int?,
        val gpuLayers: Int?,
        val ok: Boolean,
        val error: String? = null,
    )

    fun line(report: Report): String {
        val j = JSONObject()
        j.put("kind", "bench")
        j.put("run_id", report.runId)
        j.put("provider_id", report.providerId)
        j.put("category", report.category)
        j.put("tokens", report.tokens)
        j.put("duration_ms", report.durationMs)
        j.put("ok", report.ok)
        report.tokensPerSec?.let { j.put("tokens_per_sec", it) }
        report.firstTokenMs?.let { j.put("first_token_ms", it) }
        report.rssBytes?.let { j.put("rss_bytes", it) }
        report.kvTypeK?.let { j.put("kv_type_k", it) }
        report.kvTypeV?.let { j.put("kv_type_v", it) }
        report.nCtx?.let { j.put("n_ctx", it) }
        report.threads?.let { j.put("threads", it) }
        report.gpuLayers?.let { j.put("gpu_layers", it) }
        report.error?.let { j.put("error", it) }
        return j.toString()
    }

    /** Terminal marker the sweep harness polls for before moving to the next cell. */
    fun done(runId: String, ok: Boolean): String {
        val j = JSONObject()
        j.put("kind", "done")
        j.put("run_id", runId)
        j.put("ok", ok)
        return j.toString()
    }
}
