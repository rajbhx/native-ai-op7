package com.engine.nativeai

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentExtractorTest {

    private class FakePdfBackend(
        override val available: Boolean = true,
        private val stdout: String = "PDF text page one\npage two",
        private val exitCode: Int = 0,
    ) : ExecutionBackend {
        var lastCommand: String? = null
        override suspend fun execute(request: ExecutionRequest): ExecutionResult {
            lastCommand = request.command
            return ExecutionResult(exitCode, stdout, "", 10, false, false)
        }
    }

    private fun scratch(): File = File(System.getProperty("java.io.tmpdir"), "nativedoc-${System.nanoTime()}")

    @Test
    fun extractsPdfTextWhenBackendAvailable() = runBlocking {
        val backend = FakePdfBackend()
        val extractor = TermuxDocumentTextExtractor(backend, scratch())
        val text = extractor.extractPdf("%PDF-1.7 fake".toByteArray(), "doc-1.pdf")
        assertNotNull(text)
        assertTrue(text!!.contains("PDF text page one"))
        assertTrue(backend.lastCommand!!.contains("pdftotext"))
    }

    @Test
    fun nullWhenBackendUnavailable() = runBlocking {
        val extractor = TermuxDocumentTextExtractor(FakePdfBackend(available = false), scratch())
        assertNull(extractor.extractPdf("x".toByteArray(), "d.pdf"))
    }

    @Test
    fun nullWhenPdfToolFails() = runBlocking {
        val backend = FakePdfBackend(exitCode = 1, stdout = "")
        val extractor = TermuxDocumentTextExtractor(backend, scratch())
        assertNull(extractor.extractPdf("x".toByteArray(), "d.pdf"))
    }
}
