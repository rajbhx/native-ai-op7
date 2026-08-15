package com.engine.nativeai

/**
 * Storage contract for the source knowledge base. Implemented by
 * MemoryDatabase (Android SQLite); JVM tests use a sqlite-jdbc twin so the
 * same registry/updater/search logic is verified without the device.
 */
interface SourceStore {
    fun sourceByTitle(title: String): Source?
    fun sourceById(id: Long): Source?
    fun upsertSourceCollection(name: String): Long
    fun saveSource(s: Source): Long
    fun deleteSource(id: Long)
    fun sources(): List<Source>
    fun collections(): List<SourceCollection>
    fun sourceFiles(sourceId: Long): List<SourceFile>
    fun upsertSourceFile(f: SourceFile): Long
    fun deleteSourceFile(fileId: Long)
    fun replaceSourceChunks(sourceId: Long, fileId: Long, chunks: List<String>)
    fun touchSourceRead(sourceId: Long)
    fun touchSourceWrite(
        sourceId: Long,
        revision: String?,
        etag: String?,
        lastModified: String?,
        status: SourceStatus,
        fileCount: Int,
        sizeBytes: Long,
    )
    fun markSourceError(sourceId: Long, message: String)
    fun evictSources(keep: Int): Int
    fun searchSources(query: String, limit: Int): List<SourceSearchHit>
}
