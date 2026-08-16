package com.engine.nativeai

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Model file integrity (golden-standard model manager): streaming SHA-256 so
 * a 491 MB GGUF is never loaded into RAM to verify. Off the Main thread.
 * A mismatch means "delete and re-download" — a corrupt model is never loaded.
 */
object ModelIntegrity {

    /** Streaming SHA-256 hex digest, or null on any read failure. */
    suspend fun sha256(file: File): String? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(256 * 1024).use { ins ->
                val buf = ByteArray(256 * 1024)
                var n: Int
                while (ins.read(buf).also { n = it } != -1) {
                    digest.update(buf, 0, n)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun matches(file: File, expected: String): Boolean {
        if (expected.isBlank() || !file.isFile) return false
        val actual = sha256(file) ?: return false
        return actual.equals(expected.trim().lowercase(), ignoreCase = true)
    }
}
