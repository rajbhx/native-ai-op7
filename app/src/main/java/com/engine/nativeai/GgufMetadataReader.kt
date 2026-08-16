package com.engine.nativeai

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * GGUF model header metadata (architecture, layer count, embedding dim).
 * Read WITHOUT loading the model so memory planning can use measured values
 * at pre-flight. Only confirmed values are returned; any anomaly yields null
 * and callers fall back to labeled estimates. Never fabricates numbers.
 */
data class GgufModelMeta(
    val architecture: String,
    val layers: Int,
    val embeddingDim: Int,
)

/**
 * Minimal, strict GGUF v2/v3 metadata reader (llama.cpp gguf format: magic,
 * version, tensor_count u64, kv_count u64, then key/value pairs). Malformed,
 * unsupported, or truncated files return null (unknown), never a guess.
 */
object GgufMetadataReader {

    private const val MAGIC: Int = 0x46554747 // "GGUF" as little-endian u32
    private const val MIN_VERSION = 2

    private const val TYPE_UINT8 = 0
    private const val TYPE_INT8 = 1
    private const val TYPE_UINT16 = 2
    private const val TYPE_INT16 = 3
    private const val TYPE_UINT32 = 4
    private const val TYPE_INT32 = 5
    private const val TYPE_FLOAT32 = 6
    private const val TYPE_BOOL = 7
    private const val TYPE_STRING = 8
    private const val TYPE_ARRAY = 9
    private const val TYPE_UINT64 = 10
    private const val TYPE_INT64 = 11
    private const val TYPE_FLOAT64 = 12

    private const val MAX_STRING_BYTES = 16L * 1024 * 1024
    private const val MAX_KV_COUNT = 1_000_000
    private const val MAX_ARRAY_COUNT = 1_000_000

    fun read(file: File): GgufModelMeta? {
        if (!file.isFile || file.length() < 24) return null
        return try {
            RandomAccessFile(file, "r").use { parse(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(raf: RandomAccessFile): GgufModelMeta? {
        val header = le(raf, 24)
        if (header.getInt(0) != MAGIC) return null
        if (header.getInt(4) < MIN_VERSION) return null
        val kvCount = header.getLong(16)
        if (kvCount < 0 || kvCount > MAX_KV_COUNT) return null

        var architecture: String? = null
        val blockCounts = HashMap<String, Int>()
        val embeddingDims = HashMap<String, Int>()

        repeat(kvCount.toInt()) {
            val key = readString(raf)
            val type = leU32(raf)
            when (type) {
                TYPE_STRING -> {
                    val value = readString(raf)
                    if (key == "general.architecture") architecture = value
                }
                TYPE_UINT32 -> {
                    val value = leU32(raf)
                    val prefix = key.substringBefore('.')
                    when {
                        key.endsWith(".block_count") -> blockCounts[prefix] = value
                        key.endsWith(".embedding_length") -> embeddingDims[prefix] = value
                    }
                }
                else -> skipValue(raf, type)
            }
        }

        val arch = architecture ?: return null
        val layers = blockCounts[arch] ?: return null
        val dim = embeddingDims[arch] ?: return null
        return GgufModelMeta(arch, layers, dim)
    }

    private fun readString(raf: RandomAccessFile): String {
        val len = leU64(raf)
        if (len < 0 || len > MAX_STRING_BYTES) throw IllegalArgumentException("oversized GGUF string")
        val bytes = ByteArray(len.toInt())
        raf.readFully(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    private fun skipValue(raf: RandomAccessFile, type: Int) {
        when (type) {
            TYPE_UINT8, TYPE_INT8, TYPE_BOOL -> skip(raf, 1)
            TYPE_UINT16, TYPE_INT16 -> skip(raf, 2)
            TYPE_UINT32, TYPE_INT32, TYPE_FLOAT32 -> skip(raf, 4)
            TYPE_UINT64, TYPE_INT64, TYPE_FLOAT64 -> skip(raf, 8)
            TYPE_STRING -> {
                val len = leU64(raf)
                if (len < 0 || len > MAX_STRING_BYTES) throw IllegalArgumentException("oversized GGUF string")
                skip(raf, len)
            }
            TYPE_ARRAY -> {
                val elemType = leU32(raf)
                val count = leU64(raf)
                if (count < 0 || count > MAX_ARRAY_COUNT) throw IllegalArgumentException("oversized GGUF array")
                repeat(count.toInt()) { skipValue(raf, elemType) }
            }
            else -> throw IllegalArgumentException("unknown GGUF value type $type")
        }
    }

    private fun skip(raf: RandomAccessFile, bytes: Long) {
        raf.seek(raf.filePointer + bytes)
    }

    private fun le(raf: RandomAccessFile, n: Int): ByteBuffer {
        val buf = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
        raf.readFully(buf.array())
        return buf
    }

    private fun leU32(raf: RandomAccessFile): Int = le(raf, 4).getInt(0)
    private fun leU64(raf: RandomAccessFile): Long = le(raf, 8).getLong(0)
}

/**
 * Small process-wide cache keyed by path + size + mtime so pre-flight and the
 * stats card never re-read the GGUF header on every recomposition.
 */
object GgufMetaCache {

    private data class Key(val path: String, val length: Long, val modified: Long)

    private val cache = object : LinkedHashMap<Key, GgufModelMeta>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, GgufModelMeta>?): Boolean = size > 16
    }

    @Synchronized
    fun metaFor(file: File): GgufModelMeta? {
        val key = Key(file.absolutePath, file.length(), file.lastModified())
        cache[key]?.let { return it }
        return GgufMetadataReader.read(file)?.also { cache[key] = it }
    }

    @Synchronized
    fun clear() = cache.clear()
}
