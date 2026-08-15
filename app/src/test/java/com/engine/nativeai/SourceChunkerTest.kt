package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceChunkerTest {

    @Test
    fun emptyTextYieldsNoChunks() {
        assertTrue(SourceChunker.chunk("").isEmpty())
    }

    @Test
    fun shortTextStaysSingleChunk() {
        val chunks = SourceChunker.chunk("hello world")
        assertEquals(1, chunks.size)
        assertEquals("hello world", chunks[0])
    }

    @Test
    fun longTextSplitsWithinBounds() {
        val text = (1..200).joinToString(" ") { "word$it" }
        val chunks = SourceChunker.chunk(text)
        assertTrue("expected multiple chunks", chunks.size > 1)
        chunks.forEach { assertTrue(it.length <= 512) }
    }

    @Test
    fun chunksCarryOverlapContext() {
        val text = (1..300).joinToString(" ") { "token$it" }
        val chunks = SourceChunker.chunk(text, chunkChars = 128, overlapChars = 32)
        assertTrue(chunks.size >= 2)
        // Consecutive chunks should share at least some boundary words.
        for (i in 1 until chunks.size) {
            val a = chunks[i - 1].split(" ").toSet()
            val b = chunks[i].split(" ").toSet()
            assertTrue("chunks $i should overlap", a.any { it in b })
        }
    }

    @Test
    fun binarySniffDetectsNulBytes() {
        assertTrue(SourceChunker.isLikelyBinary(byteArrayOf(1, 2, 3, 0, 5)))
        assertFalse(SourceChunker.isLikelyBinary("plain text".toByteArray()))
    }

    @Test
    fun shouldIngestRespectsSizeAndTypeCaps() {
        val big = ByteArray(SourceCapabilities.MAX_FILE_BYTES.toInt() + 1)
        assertFalse(SourceChunker.shouldIngest(big, "a.txt"))
        assertFalse(SourceChunker.shouldIngest(byteArrayOf(0, 1, 2), "a.txt"))
        assertTrue(SourceChunker.shouldIngest("val x = 1".toByteArray(), "Main.kt"))
        assertTrue(SourceChunker.shouldIngest("# readme".toByteArray(), "README"))
        assertFalse(SourceChunker.shouldIngest("data".toByteArray(), "image.png"))
    }
}
