package com.engine.nativeai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Bounded conditional-fetch response (uBO ETag/Last-Modified model). */
data class FetchResponse(
    val status: Int,
    val body: ByteArray?,
    val etag: String? = null,
    val lastModified: String? = null,
    val rateLimitRemaining: Long? = null,
    val rateLimitReset: Long? = null,
) {
    val notModified: Boolean get() = status == 304
    val rateLimited: Boolean get() = status == 403 || status == 429
}

interface SourceFetcher {
    suspend fun fetch(url: String, headers: Map<String, String> = emptyMap()): FetchResponse
}

/** HttpURLConnection fetcher; works on Android and in JVM tests. */
class HttpSourceFetcher(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : SourceFetcher {
    override suspend fun fetch(url: String, headers: Map<String, String>): FetchResponse =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", "NativeAI-OP7/0.1 (android)")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                val status = conn.responseCode
                val etag = conn.getHeaderField("ETag")
                val lm = conn.getHeaderField("Last-Modified")
                val rlRemaining = conn.getHeaderField("X-RateLimit-Remaining")?.toLongOrNull()
                val rlReset = conn.getHeaderField("X-RateLimit-Reset")?.toLongOrNull()
                if (status == 304) {
                    return@withContext FetchResponse(304, null, etag, lm, rlRemaining, rlReset)
                }
                if (status !in 200..299) {
                    conn.errorStream?.close()
                    return@withContext FetchResponse(status, null, etag, lm, rlRemaining, rlReset)
                }
                val body = conn.inputStream.use { it.readBytes() }
                FetchResponse(status, body, etag, lm, rlRemaining, rlReset)
            } finally {
                conn.disconnect()
            }
        }
}
