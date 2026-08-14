package com.engine.nativeai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI-compatible /chat/completions backend (spec §3). Supports streaming
 * (SSE) and non-streaming calls. No dependency on a specific vendor; endpoint
 * and key come from runtime config, never from source.
 *
 * Secrets: the API key lives only in memory; it is never logged, written to
 * the catalog, or included in the APK.
 */
class OpenAICompatibleProvider(
    override val descriptor: ModelDescriptor,
    private val apiKey: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 60_000,
) : ModelProvider {

    private val baseUrl: String = descriptor.endpoint.trimEnd('/')

    override fun stream(request: ModelRequest): Flow<ModelStreamEvent> = flow {
        withContext(Dispatchers.IO) {
            val conn = openConnection(stream = true)
            try {
                writeBody(conn, request, stream = true)
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    emit(ModelStreamEvent.Error(friendlyError(code, err)))
                    return@withContext
                }
                val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                var done = false
                var tokens = 0
                var line: String?
                while (!done && currentCoroutineContext().isActive) {
                    line = reader.readLine() ?: break
                    if (line.isBlank()) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val event = parseSseEvent(data)
                    when (event) {
                        is SseContent -> {
                            if (event.delta.isNotBlank()) {
                                tokens++
                                emit(ModelStreamEvent.Token(event.delta))
                            }
                            if (event.reasoning.isNotBlank()) {
                                emit(ModelStreamEvent.Reasoning(event.reasoning))
                            }
                        }
                        is SseError -> {
                            emit(ModelStreamEvent.Error(event.message))
                            done = true
                        }
                        null -> { /* ignore unknown shapes */ }
                    }
                }
                emit(ModelStreamEvent.Done(tokens))
            } catch (e: Exception) {
                emit(ModelStreamEvent.Error(e.message ?: "stream failed"))
            } finally {
                conn.disconnect()
            }
        }
    }

    override suspend fun complete(request: ModelRequest): ModelResult =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val conn = openConnection(stream = false)
            try {
                writeBody(conn, request, stream = false)
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    throw IllegalStateException("provider ${descriptor.id} error: ${friendlyError(code, err)}")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = JSONObject(body)
                val choices = json.optJSONArray("choices") ?: JSONArray()
                val message = if (choices.length() > 0) choices.getJSONObject(0).optJSONObject("message") else null
                val text = message?.optString("content", "") ?: ""
                val usage = json.optJSONObject("usage")
                ModelResult(
                    text = text,
                    tokens = usage?.optInt("completion_tokens", 0) ?: 0,
                    durationMs = System.currentTimeMillis() - started,
                    providerId = descriptor.id,
                )
            } catch (e: Exception) {
                if (e is IllegalStateException) throw e
                throw IllegalStateException("provider ${descriptor.id} request failed: ${e.message}", e)
            } finally {
                conn.disconnect()
            }
        }

    override suspend fun health(): ProviderHealth = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            // Zen supports anonymous free usage (per-IP rate-limited); a key
            // only raises the quota. Availability is verified at request time.
            return@withContext ProviderHealth(true, 0, "anonymous (free tier, rate-limited)")
        }
        val started = System.currentTimeMillis()
        try {
            val conn = URL("$baseUrl/models").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = connectTimeoutMs
                conn.readTimeout = readTimeoutMs
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                val code = conn.responseCode
                if (code in 200..299) {
                    ProviderHealth(true, System.currentTimeMillis() - started, "reachable")
                } else {
                    ProviderHealth(false, System.currentTimeMillis() - started, "HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            ProviderHealth(false, System.currentTimeMillis() - started, e.message ?: "unreachable")
        }
    }

    private fun openConnection(stream: Boolean): HttpURLConnection {
        val conn = URL("$baseUrl/chat/completions").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", if (stream) "text/event-stream" else "application/json")
        if (apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return conn
    }

    private fun writeBody(conn: HttpURLConnection, request: ModelRequest, stream: Boolean) {
        val messages = JSONArray().apply {
            if (request.system.isNotBlank()) {
                put(JSONObject().put("role", "system").put("content", request.system))
            }
            put(JSONObject().put("role", "user").put("content", request.prompt))
        }
        val body = JSONObject().apply {
            put("model", descriptor.id)
            put("messages", messages)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature.toDouble())
            put("stream", stream)
            if (request.stopSequences.isNotEmpty()) {
                put("stop", JSONArray(request.stopSequences))
            }
        }
        conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
    }

    private sealed class SseEvent

    private class SseContent(val delta: String, val reasoning: String) : SseEvent()

    private class SseError(val message: String) : SseEvent()

    private fun parseSseEvent(data: String): SseEvent? = try {
        val json = JSONObject(data)
        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            null
        } else {
            val choice = choices.getJSONObject(0)
            if (choice.optString("finish_reason") == "error") {
                SseError(json.optString("error", "provider error"))
            } else {
                val delta = choice.optJSONObject("delta") ?: JSONObject()
                SseContent(
                    delta = delta.optString("content", ""),
                    reasoning = delta.optString("reasoning_content", ""),
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun friendlyError(code: Int, raw: String): String {
        if (code == 429) {
            return "Free tier rate limit reached (anonymous). Add a free OpenCode Zen key via Configure, " +
                "wait for the limit to reset, or use a local model."
        }
        return truncate(raw)
    }

    private fun truncate(s: String, max: Int = 400): String =
        if (s.length <= max) s else s.take(max) + "…"
}
