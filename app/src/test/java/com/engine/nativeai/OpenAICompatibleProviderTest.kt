package com.engine.nativeai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket

/**
 * Regression: remote stream failures must surface as ModelStreamEvent.Error.
 * Previously the provider emitted from a bare catch block inside the flow
 * builder, which kotlinx.coroutines 1.8 rejects with
 * FlowExceptionTransparencyViolatedException, killing the whole stream.
 */
class OpenAICompatibleProviderTest {

    private fun descriptor(endpoint: String) = ModelDescriptor(
        id = "test-model",
        displayName = "Test Model",
        provider = "test",
        endpoint = endpoint,
        modelType = "chat",
        kind = ModelKind.REMOTE,
        costTier = ModelCostTier.FREE,
        availability = ModelAvailability.AVAILABLE,
        contextLength = 4096,
    )

    @Test
    fun unreachableEndpointEmitsErrorInsteadOfThrowing() = runBlocking {
        val provider = OpenAICompatibleProvider(descriptor("http://127.0.0.1:1/v1"), apiKey = "")
        val events = provider.stream(
            ModelRequest(prompt = "hello", maxTokens = 8),
        ).toList()
        assertTrue("expected an Error event, got $events", events.any { it is ModelStreamEvent.Error })
    }

    /** Minimal single-request HTTP/1.1 SSE stub — no JDK module deps. */
    private class SseServer(private val body: String) {
        private val server = ServerSocket(0)
        @Volatile private var accepted = false

        fun start(): SseServer {
            Thread {
                runCatching {
                    server.accept().use { socket: Socket ->
                        accepted = true
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        var contentLength = 0
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) break
                            val lower = line.lowercase()
                            if (lower.startsWith("content-length:")) {
                                contentLength = line.substringAfter(':').trim().toInt()
                            }
                        }
                        if (contentLength > 0) {
                            val buf = CharArray(contentLength)
                            var read = 0
                            while (read < contentLength) {
                                val n = reader.read(buf, read, contentLength - read)
                                if (n < 0) break
                                read += n
                            }
                        } else {
                            // chunked body: hex-size lines until a zero chunk
                            while (true) {
                                val sizeLine = reader.readLine() ?: break
                                val size = sizeLine.trim().toIntOrNull(16) ?: break
                                if (size == 0) break
                                val buf = CharArray(size)
                                var read = 0
                                while (read < size) {
                                    val n = reader.read(buf, read, size - read)
                                    if (n < 0) break
                                    read += n
                                }
                                reader.readLine()
                            }
                        }
                        val out = socket.getOutputStream()
                        out.write(
                            ("HTTP/1.1 200 OK\r\n" +
                                "Content-Type: text/event-stream\r\n" +
                                "Content-Length: ${body.toByteArray().size}\r\n" +
                                "Connection: close\r\n\r\n").toByteArray()
                        )
                        out.write(body.toByteArray())
                        out.flush()
                    }
                }
            }.start()
            return this
        }

        fun port() = server.localPort
    }

    @Test
    fun sseStreamEmitsTokensThenDone() = runBlocking {
        val sse = SseServer(
            "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n" +
                "data: {\"choices\":[{\"delta\":{\"content\":\" there\"}}]}\n\n" +
                "data: [DONE]\n\n",
        ).start()
        val base = "http://127.0.0.1:${sse.port()}"
        val provider = OpenAICompatibleProvider(descriptor("$base/v1"), apiKey = "")
        val events = provider.stream(
            ModelRequest(prompt = "hi", maxTokens = 8),
        ).toList()
        val tokens = events.filterIsInstance<ModelStreamEvent.Token>().joinToString("") { it.text }
        assertEquals("Hi there", tokens)
        assertTrue(events.any { it is ModelStreamEvent.Done })
    }
}
