package com.engine.nativeai

import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app GGUF downloader (PocketPal-style model onboarding, adapted for OP7).
 * Downloads to a temp file, verifies the GGUF magic, then renames into place.
 * Follows redirects (HF CDN), checks free space, supports cancellation, and
 * never writes partial models to the load path.
 *
 * Resume: a cancelled or network-interrupted attempt keeps the `.tmp`
 * partial; the next download() call sends `Range: bytes=N-` and appends.
 * SHA-256 still covers the full file (existing bytes are replayed through
 * the digest from disk before appending), so the manifest checksum stays
 * valid for resumed downloads. Servers that ignore Range restart cleanly.
 */
sealed class DownloadResult {
    /** sha256 is the streaming digest computed while writing (measured). */
    data class Success(val bytes: Long, val sha256: String) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

class ModelDownloader(private val dest: File) {

    private fun tmpFile(): File = File(dest.parentFile, dest.name + ".tmp")

    /** Partial bytes on disk from a previous attempt (0 = no resume yet). */
    val existingBytes: Long
        get() = tmpFile().length().takeIf { it > 0 } ?: 0L

    suspend fun download(
        url: String,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
        cancelled: AtomicBoolean,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val tmp = tmpFile()
        cancelled.set(false)
        var existing = existingBytes
        var downloaded = 0L
        var total: Long? = null
        var conn: HttpURLConnection? = null
        try {
            var finished = false
            while (!finished) {
                val c = URL(url).openConnection() as HttpURLConnection
                conn?.disconnect()
                conn = c
                c.requestMethod = "GET"
                c.connectTimeout = 20_000
                c.readTimeout = 30_000
                c.instanceFollowRedirects = true
                if (existing > 0L) c.setRequestProperty("Range", "bytes=$existing-")
                val code = c.responseCode
                when {
                    // Stale/ignored partial: the server cannot resume, restart clean.
                    code == 416 || (code == 200 && existing > 0L) -> {
                        tmp.delete()
                        existing = 0L
                        continue
                    }
                    code !in 200..299 -> {
                        tmp.delete()
                        return@withContext DownloadResult.Error("HTTP $code from $url")
                    }
                    code == 206 -> {
                        // Content-Length on a 206 is the remaining bytes.
                        total = c.contentLengthLong.takeIf { it >= 0L }?.let { existing + it }
                    }
                    else -> total = c.contentLengthLong.takeIf { it > 0L }
                }
                if (total != null && existing >= total) {
                    // Partial is already complete/stale — restart rather than append.
                    tmp.delete()
                    existing = 0L
                    continue
                }
                val stat = StatFs(dest.parentFile?.absolutePath ?: "/")
                val freeBytes = stat.availableBytes
                val needBytes = (total ?: 0L) - existing + (64L * 1024 * 1024)
                if (total != null && freeBytes < needBytes) {
                    tmp.delete()
                    return@withContext DownloadResult.Error(
                        "Not enough space: need ${needBytes / (1024 * 1024)} MB, free ${freeBytes / (1024 * 1024)} MB",
                    )
                }

                dest.parentFile?.mkdirs()
                val digest = MessageDigest.getInstance("SHA-256")
                // Replay existing bytes through the digest so a resumed
                // download still yields a checksum over the full file.
                if (existing > 0L) {
                    tmp.inputStream().use { ins ->
                        val buf = ByteArray(256 * 1024)
                        var n: Int
                        while (ins.read(buf).also { n = it } != -1) digest.update(buf, 0, n)
                    }
                }
                downloaded = existing
                FileOutputStream(tmp, /* append */ true).use { out ->
                    val buf = ByteArray(256 * 1024)
                    val input = c.inputStream.buffered()
                    var lastUpdate = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        if (cancelled.get()) {
                            // Keep the partial so the next attempt can resume.
                            return@withContext DownloadResult.Error("cancelled")
                        }
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        downloaded += n
                        if (downloaded - lastUpdate >= 256 * 1024 || downloaded == (total ?: 0L)) {
                            lastUpdate = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }
                finished = true

                val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

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
                return@withContext DownloadResult.Success(downloaded, sha256)
            }
            // Unreachable: every path inside the loop returns or finishes.
            DownloadResult.Error("download failed")
        } catch (e: Exception) {
            // Cancellation and network interruptions keep the partial for
            // resume; other failures drop it so a corrupt tmp never lingers.
            val keepPartial = cancelled.get() || e is IOException
            if (!keepPartial) tmp.delete()
            DownloadResult.Error(e.message ?: "download failed")
        } finally {
            conn?.disconnect()
        }
    }
}
