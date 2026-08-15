package com.engine.nativeai

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException

/**
 * sqlite-jdbc twin of MemoryDatabase's source schema. Runs the exact DDL
 * from SourceSchema so migration/search/eviction tests reflect device SQL.
 */
class JdbcSourceStore(private val conn: Connection) : SourceStore {

    companion object {
        fun open(createFts: Boolean = true): JdbcSourceStore {
            Class.forName("org.sqlite.JDBC")
            val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
            SourceSchema.DDL.forEach { conn.createStatement().use { st -> st.execute(it) } }
            if (createFts) {
                SourceSchema.FTS_DDL.forEach { conn.createStatement().use { st -> st.execute(it) } }
            }
            return JdbcSourceStore(conn)
        }
    }

    override fun sourceByTitle(title: String): Source? =
        conn.prepareStatement("SELECT * FROM sources WHERE title = ?").use { ps ->
            ps.setString(1, title); ps.executeQuery().use { if (it.next()) it.toSource() else null }
        }

    override fun sourceById(id: Long): Source? =
        conn.prepareStatement("SELECT * FROM sources WHERE id = ?").use { ps ->
            ps.setLong(1, id); ps.executeQuery().use { if (it.next()) it.toSource() else null }
        }

    override fun upsertSourceCollection(name: String): Long {
        conn.prepareStatement("INSERT OR IGNORE INTO source_collections(name, created) VALUES(?, ?)").use { ps ->
            ps.setString(1, name); ps.setLong(2, System.currentTimeMillis()); ps.executeUpdate()
        }
        return conn.prepareStatement("SELECT id FROM source_collections WHERE name = ?").use { ps ->
            ps.setString(1, name); ps.executeQuery().use { if (it.next()) it.getLong(1) else -1L }
        }
    }

    override fun saveSource(s: Source): Long {
        val existing = if (s.id > 0) sourceById(s.id) else sourceByTitle(s.title)
        if (existing != null) {
            conn.prepareStatement(
                """UPDATE sources SET collection_id=?, title=?, type=?, content_url=?, owner=?,
                   repo=?, revision=?, etag=?, last_modified=?, write_time=?, read_time=?,
                   update_after_hours=?, status=?, error=?, file_count=?, last_updated=?, size_bytes=?
                   WHERE id=?""",
            ).use { ps ->
                ps.setLong(1, s.collectionId); ps.setString(2, s.title); ps.setString(3, s.type.name)
                ps.setString(4, s.contentUrl); ps.setString(5, s.owner); ps.setString(6, s.repo)
                ps.setString(7, s.revision); ps.setString(8, s.etag); ps.setString(9, s.lastModified)
                ps.setLong(10, s.writeTime); ps.setLong(11, s.readTime); ps.setLong(12, s.updateAfterHours)
                ps.setString(13, s.status.name); ps.setString(14, s.error)
                ps.setInt(15, s.fileCount); ps.setLong(16, s.lastUpdated); ps.setLong(17, s.sizeBytes)
                ps.setLong(18, existing.id); ps.executeUpdate()
            }
            return existing.id
        }
        val id = conn.prepareStatement(
            """INSERT INTO sources(collection_id, title, type, content_url, owner, repo,
               revision, etag, last_modified, write_time, read_time, update_after_hours,
               status, error, file_count, last_updated, size_bytes)
               VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        ).use { ps ->
            ps.setLong(1, s.collectionId); ps.setString(2, s.title); ps.setString(3, s.type.name)
            ps.setString(4, s.contentUrl); ps.setString(5, s.owner); ps.setString(6, s.repo)
            ps.setString(7, s.revision); ps.setString(8, s.etag); ps.setString(9, s.lastModified)
            ps.setLong(10, s.writeTime); ps.setLong(11, s.readTime); ps.setLong(12, s.updateAfterHours)
            ps.setString(13, s.status.name); ps.setString(14, s.error)
            ps.setInt(15, s.fileCount); ps.setLong(16, s.lastUpdated); ps.setLong(17, s.sizeBytes)
            ps.executeUpdate()
            lastInsertId()
        }
        return id
    }

    override fun deleteSource(id: Long) {
        conn.prepareStatement("DELETE FROM source_chunks WHERE source_id = ?").use { ps ->
            ps.setLong(1, id); ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM source_files WHERE source_id = ?").use { ps ->
            ps.setLong(1, id); ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM sources WHERE id = ?").use { ps ->
            ps.setLong(1, id); ps.executeUpdate()
        }
    }

    override fun sources(): List<Source> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM sources ORDER BY title ASC").use { rs ->
                buildList { while (rs.next()) add(rs.toSource()) }
            }
        }

    override fun collections(): List<SourceCollection> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM source_collections ORDER BY name ASC").use { rs ->
                buildList {
                    while (rs.next()) {
                        add(SourceCollection(rs.getLong("id"), rs.getString("name"), rs.getLong("created")))
                    }
                }
            }
        }

    override fun sourceFiles(sourceId: Long): List<SourceFile> =
        conn.prepareStatement("SELECT * FROM source_files WHERE source_id = ? ORDER BY path ASC").use { ps ->
            ps.setLong(1, sourceId); ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SourceFile(
                                id = rs.getLong("id"), sourceId = rs.getLong("source_id"),
                                path = rs.getString("path"), blobSha = rs.getString("blob_sha"),
                                chunked = rs.getInt("chunked") == 1, sizeBytes = rs.getLong("size_bytes"),
                            ),
                        )
                    }
                }
            }
        }

    override fun upsertSourceFile(f: SourceFile): Long {
        val existing = conn.prepareStatement(
            "SELECT id FROM source_files WHERE source_id = ? AND path = ?",
        ).use { ps ->
            ps.setLong(1, f.sourceId); ps.setString(2, f.path)
            ps.executeQuery().use { if (it.next()) it.getLong(1) else -1L }
        }
        if (existing > 0) {
            conn.prepareStatement(
                "UPDATE source_files SET blob_sha=?, chunked=?, size_bytes=? WHERE id=?",
            ).use { ps ->
                ps.setString(1, f.blobSha); ps.setInt(2, if (f.chunked) 1 else 0)
                ps.setLong(3, f.sizeBytes); ps.setLong(4, existing); ps.executeUpdate()
            }
            return existing
        }
        return conn.prepareStatement(
            "INSERT INTO source_files(source_id, path, blob_sha, chunked, size_bytes) VALUES(?,?,?,?,?)",
        ).use { ps ->
            ps.setLong(1, f.sourceId); ps.setString(2, f.path); ps.setString(3, f.blobSha)
            ps.setInt(4, if (f.chunked) 1 else 0); ps.setLong(5, f.sizeBytes); ps.executeUpdate()
            lastInsertId()
        }
    }

    override fun deleteSourceFile(fileId: Long) {
        conn.prepareStatement("DELETE FROM source_chunks WHERE source_file_id = ?").use { ps ->
            ps.setLong(1, fileId); ps.executeUpdate()
        }
        conn.prepareStatement("DELETE FROM source_files WHERE id = ?").use { ps ->
            ps.setLong(1, fileId); ps.executeUpdate()
        }
    }

    override fun replaceSourceChunks(sourceId: Long, fileId: Long, chunks: List<String>) {
        conn.prepareStatement("DELETE FROM source_chunks WHERE source_file_id = ?").use { ps ->
            ps.setLong(1, fileId); ps.executeUpdate()
        }
        conn.prepareStatement(
            "INSERT INTO source_chunks(source_id, source_file_id, chunk_index, content) VALUES(?,?,?,?)",
        ).use { ps ->
            chunks.forEachIndexed { i, content ->
                ps.setLong(1, sourceId); ps.setLong(2, fileId); ps.setInt(3, i); ps.setString(4, content)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    override fun touchSourceRead(sourceId: Long) {
        conn.prepareStatement("UPDATE sources SET read_time = ? WHERE id = ?").use { ps ->
            ps.setLong(1, System.currentTimeMillis()); ps.setLong(2, sourceId); ps.executeUpdate()
        }
    }

    override fun touchSourceWrite(
        sourceId: Long, revision: String?, etag: String?, lastModified: String?,
        status: SourceStatus, fileCount: Int, sizeBytes: Long,
    ) {
        conn.prepareStatement(
            """UPDATE sources SET revision=?, etag=?, last_modified=?, write_time=?,
               last_updated=?, status=?, file_count=?, size_bytes=? WHERE id=?""",
        ).use { ps ->
            ps.setString(1, revision); ps.setString(2, etag); ps.setString(3, lastModified)
            ps.setLong(4, System.currentTimeMillis()); ps.setLong(5, System.currentTimeMillis())
            ps.setString(6, status.name); ps.setInt(7, fileCount); ps.setLong(8, sizeBytes)
            ps.setLong(9, sourceId); ps.executeUpdate()
        }
    }

    override fun markSourceError(sourceId: Long, message: String) {
        conn.prepareStatement(
            "UPDATE sources SET status=?, error=?, last_updated=? WHERE id=?",
        ).use { ps ->
            ps.setString(1, SourceStatus.ERROR.name); ps.setString(2, message.take(500))
            ps.setLong(3, System.currentTimeMillis()); ps.setLong(4, sourceId); ps.executeUpdate()
        }
    }

    override fun evictSources(keep: Int): Int {
        val total = conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM sources").use { if (it.next()) it.getLong(1) else 0L }
        }
        val excess = total - keep
        if (excess <= 0) return 0
        val doomed = conn.createStatement().use { st ->
            st.executeQuery("SELECT id FROM sources ORDER BY read_time ASC LIMIT $excess").use { rs ->
                buildList { while (rs.next()) add(rs.getLong(1)) }
            }
        }
        doomed.forEach { deleteSource(it) }
        return doomed.size
    }

    override fun searchSources(query: String, limit: Int): List<SourceSearchHit> {
        val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return try {
            val match = terms.joinToString(" OR ") { "\"" + it.replace("\"", "").replace("*", "") + "\"" }
            conn.prepareStatement(SourceSchema.FTS_SEARCH_SQL).use { ps ->
                ps.setString(1, match); ps.setInt(2, limit); ps.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                SourceSearchHit(
                                    sourceTitle = rs.getString("title"),
                                    filePath = rs.getString("path"),
                                    content = rs.getString("content"),
                                    score = rs.getFloat("bm25_rank"),
                                ),
                            )
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            likeSearch(terms, limit)
        }
    }

    private fun likeSearch(terms: List<String>, limit: Int): List<SourceSearchHit> {
        val hits = mutableListOf<SourceSearchHit>()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT * FROM source_chunks ORDER BY id DESC LIMIT 5000").use { rs ->
                while (rs.next()) {
                    val chunk = rs.toSourceChunk()
                    val n = terms.count { chunk.content.contains(it, ignoreCase = true) }
                    if (n > 0) {
                        hits += SourceSearchHit(
                            sourceTitle = sourceById(chunk.sourceId)?.title ?: "?",
                            filePath = sourceFileTitle(chunk.sourceFileId),
                            content = chunk.content,
                            score = -n.toFloat(),
                        )
                    }
                }
            }
        }
        return hits.sortedBy { it.score }.take(limit)
    }

    private fun sourceFileTitle(fileId: Long): String =
        conn.prepareStatement("SELECT path FROM source_files WHERE id = ?").use { ps ->
            ps.setLong(1, fileId); ps.executeQuery().use { if (it.next()) it.getString(1) else "?" }
        }

    private fun lastInsertId(): Long =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT last_insert_rowid()").use { if (it.next()) it.getLong(1) else -1L }
        }

    private fun ResultSet.toSource(): Source = Source(
        id = getLong("id"),
        collectionId = getLong("collection_id"),
        title = getString("title"),
        type = SourceType.valueOf(getString("type")),
        contentUrl = getString("content_url"),
        owner = getString("owner"),
        repo = getString("repo"),
        revision = getString("revision"),
        etag = getString("etag"),
        lastModified = getString("last_modified"),
        writeTime = getLong("write_time"),
        readTime = getLong("read_time"),
        updateAfterHours = getLong("update_after_hours"),
        status = SourceStatus.valueOf(getString("status")),
        error = getString("error"),
        fileCount = getInt("file_count"),
        lastUpdated = getLong("last_updated"),
        sizeBytes = getLong("size_bytes"),
    )

    private fun ResultSet.toSourceChunk(): SourceChunk = SourceChunk(
        id = getLong("id"),
        sourceId = getLong("source_id"),
        sourceFileId = getLong("source_file_id"),
        chunkIndex = getInt("chunk_index"),
        content = getString("content"),
    )
}
