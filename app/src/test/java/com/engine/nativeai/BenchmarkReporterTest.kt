package com.engine.nativeai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkReporterTest {

    private fun sample() = BenchmarkReporter.Report(
        runId = "run-test-1",
        providerId = LocalModelProvider.LOCAL_MODEL_ID,
        category = "reasoning",
        tokens = 128,
        durationMs = 64000,
        tokensPerSec = 2.0,
        firstTokenMs = 1500,
        rssBytes = 764_000_000,
        kvTypeK = "q8_0",
        kvTypeV = "q8_0",
        nCtx = 1024,
        threads = 4,
        gpuLayers = 0,
        ok = true,
    )

    @Test
    fun line_builds_single_line_json_with_all_fields() {
        val line = BenchmarkReporter.line(sample())
        assertTrue("line must not contain a newline", !line.contains("\n"))
        val j = JSONObject(line)
        assertEquals("bench", j.getString("kind"))
        assertEquals("run-test-1", j.getString("run_id"))
        assertEquals("local-llama", j.getString("provider_id"))
        assertEquals("reasoning", j.getString("category"))
        assertEquals(128, j.getInt("tokens"))
        assertEquals(64000L, j.getLong("duration_ms"))
        assertEquals(2.0, j.getDouble("tokens_per_sec"), 1e-9)
        assertEquals(1500L, j.getLong("first_token_ms"))
        assertEquals(764_000_000L, j.getLong("rss_bytes"))
        assertEquals("q8_0", j.getString("kv_type_k"))
        assertEquals("q8_0", j.getString("kv_type_v"))
        assertEquals(1024, j.getInt("n_ctx"))
        assertEquals(4, j.getInt("threads"))
        assertEquals(0, j.getInt("gpu_layers"))
        assertTrue(j.getBoolean("ok"))
        assertFalse("error must be omitted when null", j.has("error"))
    }

    @Test
    fun line_omits_null_metrics_and_keeps_error() {
        val failed = sample().copy(
            tokensPerSec = null,
            firstTokenMs = null,
            ok = false,
            error = "model not found",
        )
        val j = JSONObject(BenchmarkReporter.line(failed))
        assertFalse(j.has("tokens_per_sec"))
        assertFalse(j.has("first_token_ms"))
        assertFalse(j.getBoolean("ok"))
        assertEquals("model not found", j.getString("error"))
    }

    @Test
    fun done_marks_ok_and_carries_run_id() {
        val j = JSONObject(BenchmarkReporter.done("run-test-1", ok = true))
        assertEquals("done", j.getString("kind"))
        assertEquals("run-test-1", j.getString("run_id"))
        assertTrue(j.getBoolean("ok"))
    }
}
