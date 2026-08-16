package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class GgufMetadataReaderTest {

    private val tmp = File.createTempFile("gguf-test-", ".gguf").apply { deleteOnExit() }

    @Test
    fun readsQwen1bMetadata() {
        tmp.writeBytes(gguf(listOf(
            "general.architecture" to kvString("qwen2"),
            "qwen2.block_count" to kvU32(24),
            "qwen2.embedding_length" to kvU32(1024),
        )))
        val meta = GgufMetadataReader.read(tmp)
        assertEquals("qwen2", meta?.architecture)
        assertEquals(24, meta?.layers)
        assertEquals(1024, meta?.embeddingDim)
    }

    @Test
    fun keyOrderDoesNotMatter() {
        tmp.writeBytes(gguf(listOf(
            "qwen2.block_count" to kvU32(24),
            "general.architecture" to kvString("qwen2"),
            "qwen2.embedding_length" to kvU32(1024),
        )))
        val meta = GgufMetadataReader.read(tmp)
        assertEquals(24, meta?.layers)
        assertEquals(1024, meta?.embeddingDim)
    }

    @Test
    fun unrelatedValuesAreSkipped() {
        tmp.writeBytes(gguf(listOf(
            "general.name" to kvString("Qwen2.5-1B-Instruct"),
            "qwen2.vocab_size" to kvU32(151936),
            "qwen2.attention.head_count" to kvU32(16),
            "general.architecture" to kvString("qwen2"),
            "qwen2.block_count" to kvU32(24),
            "qwen2.embedding_length" to kvU32(1024),
        )))
        assertEquals(24, GgufMetadataReader.read(tmp)?.layers)
    }

    @Test
    fun stringArrayValueIsSkipped() {
        tmp.writeBytes(gguf(listOf(
            "tokenizer.ggml.tokens" to kvStringArray("hello", "world"),
            "general.architecture" to kvString("qwen2"),
            "qwen2.block_count" to kvU32(24),
            "qwen2.embedding_length" to kvU32(1024),
        )))
        assertEquals(24, GgufMetadataReader.read(tmp)?.layers)
    }

    @Test
    fun otherArchitectureKeysAreIgnored() {
        tmp.writeBytes(gguf(listOf(
            "general.architecture" to kvString("qwen2"),
            "llama.block_count" to kvU32(32),
            "qwen2.block_count" to kvU32(24),
            "qwen2.embedding_length" to kvU32(1024),
        )))
        val meta = GgufMetadataReader.read(tmp)
        assertEquals(24, meta?.layers)
        assertEquals(1024, meta?.embeddingDim)
    }

    @Test
    fun missingArchitectureReturnsNull() {
        tmp.writeBytes(gguf(listOf("qwen2.block_count" to kvU32(24))))
        assertNull(GgufMetadataReader.read(tmp))
    }

    @Test
    fun missingFieldReturnsNull() {
        tmp.writeBytes(gguf(listOf(
            "general.architecture" to kvString("qwen2"),
            "qwen2.block_count" to kvU32(24),
        )))
        assertNull(GgufMetadataReader.read(tmp))
    }

    @Test
    fun corruptMagicReturnsNull() {
        tmp.writeBytes(ByteArray(48) { 0x42 })
        assertNull(GgufMetadataReader.read(tmp))
    }

    @Test
    fun unsupportedVersionReturnsNull() {
        tmp.writeBytes(gguf(emptyList(), version = 1))
        assertNull(GgufMetadataReader.read(tmp))
    }

    @Test
    fun truncatedFileReturnsNull() {
        tmp.writeBytes(gguf(listOf("general.architecture" to kvString("qwen2"))).copyOf(16))
        assertNull(GgufMetadataReader.read(tmp))
    }

    @Test
    fun missingFileReturnsNull() {
        assertNull(GgufMetadataReader.read(File(tmp.absolutePath + ".does-not-exist")))
    }

    @Test
    fun cacheReturnsSameMetaForUnchangedFile() {
        tmp.writeBytes(gguf(listOf(
            "general.architecture" to kvString("qwen2"),
            "qwen2.block_count" to kvU32(24),
            "qwen2.embedding_length" to kvU32(1024),
        )))
        try {
            assertSame(GgufMetaCache.metaFor(tmp), GgufMetaCache.metaFor(tmp))
        } finally {
            GgufMetaCache.clear()
        }
    }

    private fun gguf(kvs: List<Pair<String, ByteArray>>, version: Int = 3): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        out.write(u32(version))
        out.write(u64(0)) // tensor count
        out.write(u64(kvs.size.toLong()))
        kvs.forEach { (k, v) ->
            out.write(str(k))
            out.write(v)
        }
        return out.toByteArray()
    }

    private fun kvString(v: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32(8))
        out.write(str(v))
        return out.toByteArray()
    }

    private fun kvU32(v: Int): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32(4))
        out.write(u32(v))
        return out.toByteArray()
    }

    private fun kvStringArray(vararg values: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32(9)) // array
        out.write(u32(8)) // string element type
        out.write(u64(values.size.toLong()))
        values.forEach { out.write(str(it)) }
        return out.toByteArray()
    }

    private fun str(s: String): ByteArray {
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        return u64(bytes.size.toLong()) + bytes
    }

    private fun u32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun u64(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
}
