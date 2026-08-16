package com.engine.nativeai

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelManifestTest {

    private fun tmpDir(): File {
        val dir = File.createTempFile("manifest", "dir")
        dir.delete()
        dir.mkdirs()
        return dir
    }

    @Test
    fun recordThenLoadRoundTrips() {
        val dir = tmpDir()
        try {
            ModelManifest.record(dir, "model.gguf", "ABC123", 491L * 1024 * 1024, "https://example/model.gguf")
            val entry = ModelManifest.entryFor(File(dir, "model.gguf"))
            assertEquals("ABC123", entry?.sha256)
            assertEquals(491L * 1024 * 1024, entry?.bytes)
            assertEquals("https://example/model.gguf", entry?.url)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun blankShaIsNeverRecorded() {
        val dir = tmpDir()
        try {
            ModelManifest.record(dir, "model.gguf", "", 100L)
            assertNull(ModelManifest.entryFor(File(dir, "model.gguf")))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingOrCorruptManifestFailsClosed() {
        val dir = tmpDir()
        try {
            assertTrue(ModelManifest.load(dir).isEmpty())
            File(dir, ".manifest.json").writeText("{not json")
            assertTrue("corrupt manifest must fail closed, never crash", ModelManifest.load(dir).isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }
}
