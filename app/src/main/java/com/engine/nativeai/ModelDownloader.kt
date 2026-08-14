package com.engine.nativeai

import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app GGUF downloader (PocketPal-style model onboarding, adapted for OP7).
 * Downloads to a temp file, verifies the GGUF magic, then renames into place.
 * Follows redirects (HF CDN), checks free space, supports cancellation, and
 * never writes partial models to the load path.
 */
sealed class DownloadResult {
    data class Success(val bytes: Long) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

class ModelDownloader(private val dest: File) {

    /** Total bytes when the server reports Content-Length, else null. */
    suspend fun download(
        url: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
        cancelled: AtomicBoolean,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        cancelled.set(false)
        var downloaded = 0L
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = 20_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                if (code !in 200..299) {
                    return@withContext DownloadResult.Error("HTTP $code from $url")
                }
                val total = conn.contentLengthLong.takeIf { it > 0 }
                val stat = StatFs(dest.parentFile?.absolutePath ?: "/")
                val freeBytes = stat.availableBytes
                val needBytes = (total ?: 0L) + (64L * 1024 * 1024)
                if (total != null && freeBytes < needBytes) {
                    return@withContext DownloadResult.Error(
                        "Not enough space: need ${needBytes / (1024 * 1024)} MB, free ${freeBytes / (1024 * 1024)} MB",
                    )
                }

                dest.parentFile?.mkdirs()
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(256 * 1024)
                    val input = conn.inputStream.buffered()
                    var lastUpdate = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        if (cancelled.get()) return@withContext DownloadResult.Error("cancelled")
                        out.write(buf, 0, n)
                        downloaded += n
                        if (downloaded - lastUpdate >= 256 * 1024 || downloaded == (total ?: 0L)) {
                            lastUpdate = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }

                // GGUF magic guard: never promote a truncated/garbage file.
                val magic = tmp.inputStream().use { ins ->
                    val head = ByteArray(4)
                    if (ins.read(head) != 4) null else head
                }
                if (magic == null || String(magic) != "GGUF") {
                    tmp.delete()
                    return@withContext DownloadResult.Error("Downloaded file is not a GGUF model")
                }

                if (dest.exists() && !dest.delete()) {
                    tmp.delete()
                    return@withContext DownloadResult.Error("Could not replace existing model")
                }
                if (!tmp.renameTo(dest)) {
                    tmp.delete()
                    return@withContext DownloadResult.Error("Could not move model into place")
                }
                DownloadResult.Success(downloaded)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            tmp.delete()
            DownloadResult.Error(e.message ?: "download failed")
        }
    }
}
