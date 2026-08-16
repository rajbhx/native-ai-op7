package com.engine.nativeai

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-model integrity manifest (models/.manifest.json). Records the measured
 * SHA-256 + size + source URL per GGUF file so pre-load verification is
 * possible and storage usage is accounted. Versioned shape; unknown fields
 * are ignored, never fabricated.
 */
object ModelManifest {

    private const val MANIFEST_NAME = ".manifest.json"
    private const val SCHEMA = 1

    data class Entry(
        val sha256: String,
        val bytes: Long,
        val url: String,
        val downloadedAt: Long,
    )

    private fun fileFor(modelsDir: File): File = File(modelsDir, MANIFEST_NAME)

    fun load(modelsDir: File): Map<String, Entry> {
        val f = fileFor(modelsDir)
        if (!f.isFile) return emptyMap()
        return try {
            val root = JSONObject(f.readText())
            if (root.optInt("schema", 0) != SCHEMA) return emptyMap()
            val out = LinkedHashMap<String, Entry>()
            val arr = root.optJSONArray("models") ?: return emptyMap()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val name = j.optString("file", "")
                if (name.isBlank()) continue
                out[name] = Entry(
                    sha256 = j.optString("sha256", ""),
                    bytes = j.optLong("bytes", 0L),
                    url = j.optString("url", ""),
                    downloadedAt = j.optLong("downloaded_at", 0L),
                )
            }
            out
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @Synchronized
    fun record(
        modelsDir: File,
        fileName: String,
        sha256: String,
        bytes: Long,
        url: String = "",
    ) {
        if (fileName.isBlank() || sha256.isBlank()) return
        val entries = load(modelsDir).toMutableMap()
        entries[fileName] = Entry(
            sha256 = sha256.lowercase(),
            bytes = bytes,
            url = url,
            downloadedAt = System.currentTimeMillis(),
        )
        val arr = JSONArray()
        entries.forEach { (name, e) ->
            arr.put(
                JSONObject()
                    .put("file", name)
                    .put("sha256", e.sha256)
                    .put("bytes", e.bytes)
                    .put("url", e.url)
                    .put("downloaded_at", e.downloadedAt),
            )
        }
        try {
            modelsDir.mkdirs()
            fileFor(modelsDir).writeText(
                JSONObject()
                    .put("schema", SCHEMA)
                    .put("models", arr)
                    .toString(2),
            )
        } catch (e: Exception) {
            // Manifest is best-effort; load verification then reports "unknown".
        }
    }

    fun entryFor(file: File): Entry? =
        file.parentFile?.let { dir -> load(dir)[file.name] }
}
