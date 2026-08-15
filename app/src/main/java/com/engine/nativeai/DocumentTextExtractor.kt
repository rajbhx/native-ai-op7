package com.engine.nativeai

import java.io.File

/**
 * PDF/document text extraction seam (roadmap Phase 5). The LLM core stays
 * dependency-free: extraction is delegated to the optional Termux execution
 * layer (`pdftotext` from the poppler package). When Termux/pdftotext is not
 * available the extractor reports unavailable and DOCUMENT sources remain
 * honest metadata-only entries — never a fabricated capability.
 */
interface DocumentTextExtractor {
    val available: Boolean

    /** Returns extracted text, or null when extraction is unavailable/failed. */
    suspend fun extractPdf(bytes: ByteArray, fileName: String): String?
}

/**
 * Termux-backed extractor. The PDF is staged in the app's own external
 * files dir (no storage permission needed to write it); Termux reads it
 * with its own storage access and the existing bounded ExecutionBackend
 * exchange returns stdout. Bounded: hard timeout, cancellation.
 */
class TermuxDocumentTextExtractor(
    private val backend: ExecutionBackend,
    private val scratchDir: File,
    private val timeoutMs: Long = 30_000,
) : DocumentTextExtractor {

    override val available: Boolean get() = backend.available

    override suspend fun extractPdf(bytes: ByteArray, fileName: String): String? {
        if (!backend.available || bytes.isEmpty()) return null
        runCatching { scratchDir.mkdirs() }
        val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val pdf = File(scratchDir, safeName)
        try {
            pdf.writeBytes(bytes)
            val res = backend.execute(
                ExecutionRequest(
                    command = "pdftotext -q '${pdf.absolutePath}' - 2>/dev/null",
                    timeoutMs = timeoutMs,
                ),
            )
            return if (res.exitCode == 0 && res.stdout.isNotBlank()) res.stdout else null
        } catch (_: Exception) {
            return null
        } finally {
            runCatching { pdf.delete() }
        }
    }
}
