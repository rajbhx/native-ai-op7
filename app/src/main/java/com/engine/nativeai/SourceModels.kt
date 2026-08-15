package com.engine.nativeai

/** Source Knowledge Base models (roadmap Phase 4-6). */
enum class SourceType { RAW_TEXT, WEB_PAGE, LOCAL_FILE, GITHUB_REPO, DOCUMENT }

enum class SourceStatus { NEW, INDEXED, ERROR, STALE }

data class SourceCollection(
    val id: Long,
    val name: String,
    val created: Long,
)

data class Source(
    val id: Long,
    val collectionId: Long,
    val title: String,
    val type: SourceType,
    val contentUrl: String? = null,
    val owner: String? = null,
    val repo: String? = null,
    val revision: String? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    val writeTime: Long = 0,
    val readTime: Long = 0,
    val updateAfterHours: Long = 24,
    val status: SourceStatus = SourceStatus.NEW,
    val error: String? = null,
    val fileCount: Int = 0,
    val lastUpdated: Long = 0,
    val sizeBytes: Long = 0,
) {
    /** When a source should be refreshed next (uBO update_after_hours model). */
    fun dueAt(now: Long): Long = writeTime + updateAfterHours * 3_600_000L

    fun stalenessMs(now: Long): Long = (now - dueAt(now)).coerceAtLeast(0)
}

data class SourceFile(
    val id: Long,
    val sourceId: Long,
    val path: String,
    val blobSha: String? = null,
    val chunked: Boolean = false,
    val sizeBytes: Long = 0,
)

data class SourceChunk(
    val id: Long,
    val sourceId: Long,
    val sourceFileId: Long,
    val chunkIndex: Int,
    val content: String,
)

data class SourceSearchHit(
    val sourceTitle: String,
    val filePath: String,
    val content: String,
    val score: Float,
)

/** Stable capabilities; never fabricated (mirrors model metadata rules). */
object SourceCapabilities {
    const val MAX_FILES_PER_SOURCE = 500
    const val MAX_FILE_BYTES = 1_048_576L // 1 MB per file
    const val CHUNK_CHARS = 512
    const val CHUNK_OVERLAP_CHARS = 64
    const val EVICT_KEEP = 20 // uBO-style read-time LRU cap per catalog
}
