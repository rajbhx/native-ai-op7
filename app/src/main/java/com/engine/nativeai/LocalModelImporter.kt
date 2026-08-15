package com.engine.nativeai

import android.content.ContentResolver
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class ImportResult {
    data class Success(val file: File) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * SAF GGUF importer (multi-model library). Copies a user-picked document
 * into `filesDir/models` with progress, a free-space check with margin, and
 * a GGUF magic guard; partial copies are never promoted to the load path.
 * Mirrors ModelDownloader's temp-file-rename pattern.
 */
class LocalModelImporter(private val modelsDir: File) {

    suspend fun import(
        resolver: ContentResolver,
        uri: Uri,
        onProgress: (downloaded: Long, total: Long?) -> Unit,
    ): ImportResult = withContext(Dispatchers.IO) {
        val displayName = queryDisplayName(resolver, uri) ?: "imported.gguf"
        val safeName = displayName
            .substringAfterLast('/')
            .takeLast(120)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "imported.gguf" }
        val destName = if (safeName.endsWith(".gguf", ignoreCase = true)) {
            safeName
        } else {
            "$safeName.gguf"
        }
        val dest = File(modelsDir, destName)
        val tmp = File(modelsDir, destName + ".tmp")
        try {
            val stat = StatFs(modelsDir.absolutePath)
            val freeBytes = stat.availableBytes
            val total = querySize(resolver, uri)
            val needBytes = (total ?: 0L) + (64L * 1024 * 1024)
            if (total != null && freeBytes < needBytes) {
                return@withContext ImportResult.Error(
                    "Not enough space: need ${needBytes / (1024 * 1024)} MB, free ${freeBytes / (1024 * 1024)} MB",
                )
            }
            modelsDir.mkdirs()
            var copied = 0L
            val input = resolver.openInputStream(uri)
            if (input == null) {
                return@withContext ImportResult.Error("Could not open selected file")
            }
            input.use { ins ->
                FileOutputStream(tmp).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var n: Int
                    while (ins.read(buf).also { n = it } != -1) {
                        out.write(buf, 0, n)
                        copied += n
                        onProgress(copied, total)
                    }
                }
            }
            val magic = tmp.inputStream().use { tins ->
                val head = ByteArray(4)
                if (tins.read(head) != 4) null else head
            }
            if (magic == null || String(magic) != "GGUF") {
                tmp.delete()
                return@withContext ImportResult.Error("Selected file is not a GGUF model")
            }
            if (dest.exists() && !dest.delete()) {
                tmp.delete()
                return@withContext ImportResult.Error("Could not replace existing file")
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                return@withContext ImportResult.Error("Could not move file into place")
            }
            ImportResult.Success(dest)
        } catch (e: Exception) {
            tmp.delete()
            ImportResult.Error(e.message ?: "import failed")
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? =
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                val idx = c.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !c.isNull(idx)) c.getLong(idx) else null
            }
}
