package com.engine.nativeai

/** In-memory SourceStore twin for registry/updater JVM tests. */
class FakeSourceStore : SourceStore {
    val sources = mutableListOf<Source>()
    val collections = mutableListOf<SourceCollection>()
    val files = mutableMapOf<Long, MutableList<SourceFile>>()
    val chunks = mutableMapOf<Long, MutableList<SourceChunk>>()
    private var nextSourceId = 1L
    private var nextFileId = 1L
    private var nextChunkId = 1L
    private var nowMs = System.currentTimeMillis()

    override fun sourceByTitle(title: String): Source? = sources.firstOrNull { it.title == title }
    override fun sourceById(id: Long): Source? = sources.firstOrNull { it.id == id }

    override fun upsertSourceCollection(name: String): Long {
        collections.firstOrNull { it.name == name }?.let { return it.id }
        val id = (collections.size + 1).toLong()
        collections += SourceCollection(id, name, 0)
        return id
    }

    override fun saveSource(s: Source): Long {
        val existing = if (s.id > 0) sourceById(s.id) else sourceByTitle(s.title)
        if (existing != null) {
            sources.replaceAll { if (it.id == existing.id) s.copy(id = existing.id) else it }
            return existing.id
        }
        val id = nextSourceId++
        sources += s.copy(id = id)
        return id
    }

    val meta = mutableMapOf<String, String>()

    override fun metaGet(key: String): String? = meta[key]
    override fun metaSet(key: String, value: String) {
        meta[key] = value
    }

    override fun deleteSource(id: Long) {
        sources.removeAll { it.id == id }
        files.remove(id)
    }

    override fun sources(): List<Source> = sources.toList()
    override fun collections(): List<SourceCollection> = collections.toList()

    override fun sourceFiles(sourceId: Long): List<SourceFile> = files[sourceId]?.toList() ?: emptyList()

    override fun chunkById(id: Long): SourceChunk? = chunks.values.flatten().firstOrNull { it.id == id }

    override fun chunksForFile(fileId: Long): List<SourceChunk> =
        (chunks[fileId] ?: emptyList()).sortedBy { it.chunkIndex }

    override fun upsertSourceFile(f: SourceFile): Long {
        val list = files.getOrPut(f.sourceId) { mutableListOf() }
        val existing = list.firstOrNull { it.path == f.path }
        if (existing != null) {
            list.replaceAll { if (it.id == existing.id) f.copy(id = existing.id) else it }
            return existing.id
        }
        val id = nextFileId++
        list += f.copy(id = id)
        return id
    }

    override fun deleteSourceFile(fileId: Long) {
        files.values.forEach { it.removeAll { f -> f.id == fileId } }
        chunks.remove(fileId)
    }

    override fun replaceSourceChunks(sourceId: Long, fileId: Long, chunks: List<String>) {
        this.chunks[fileId] = chunks.mapIndexed { i, c ->
            SourceChunk(nextChunkId++, sourceId, fileId, i, c)
        }.toMutableList()
    }

    override fun touchSourceRead(sourceId: Long) {
        val s = sourceById(sourceId) ?: return
        sources.replaceAll { if (it.id == sourceId) it.copy(readTime = nowMs) else it }
    }

    override fun touchSourceWrite(
        sourceId: Long, revision: String?, etag: String?, lastModified: String?,
        status: SourceStatus, fileCount: Int, sizeBytes: Long,
    ) {
        val s = sourceById(sourceId) ?: return
        sources.replaceAll {
            if (it.id == sourceId) {
                it.copy(
                    revision = revision, etag = etag, lastModified = lastModified,
                    writeTime = nowMs, lastUpdated = nowMs, status = status,
                    fileCount = fileCount, sizeBytes = sizeBytes,
                )
            } else it
        }
    }

    override fun markSourceError(sourceId: Long, message: String) {
        val s = sourceById(sourceId) ?: return
        sources.replaceAll {
            if (it.id == sourceId) it.copy(status = SourceStatus.ERROR, error = message, lastUpdated = nowMs)
            else it
        }
    }

    override fun evictSources(keep: Int): Int {
        val excess = sources.size - keep
        if (excess <= 0) return 0
        val doomed = sources.sortedBy { it.readTime }.take(excess).map { it.id }
        doomed.forEach { deleteSource(it) }
        return doomed.size
    }

    override fun searchSources(query: String, limit: Int): List<SourceSearchHit> {
        val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        val hits = mutableListOf<SourceSearchHit>()
        chunks.values.flatten().forEach { chunk ->
            val n = terms.count { chunk.content.contains(it, ignoreCase = true) }
            if (n > 0) {
                hits += SourceSearchHit(
                    sourceTitle = sourceById(chunk.sourceId)?.title ?: "?",
                    filePath = sourceFiles(chunk.sourceId).firstOrNull { it.id == chunk.sourceFileId }?.path ?: "?",
                    content = chunk.content,
                    score = -n.toFloat(),
                    chunkId = chunk.id,
                )
            }
        }
        return hits.sortedBy { it.score }.take(limit)
    }
}
