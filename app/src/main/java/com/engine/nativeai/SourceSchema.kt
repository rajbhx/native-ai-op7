package com.engine.nativeai

/**
 * Schema v4 — Source Knowledge Base tables (roadmap Phase 4/5).
 *
 * Pure DDL strings (no Android types) so the exact same statements run on
 * the device via SQLiteDatabase and in JVM tests via sqlite-jdbc. The
 * source system follows the uBO-style registry model (clean-room ideas):
 * versioned sources, per-source refresh cadence, ETag/revision tracking,
 * changed-blob-only re-chunking, read-time LRU eviction.
 */
object SourceSchema {
    const val SCHEMA_VERSION = 4

    /** Non-FTS tables; safe on any SQLite build (incl. OP7/OxygenOS 10). */
    val DDL: List<String> = listOf(
        // uBO-style "collections" = user groupings (Android / AI / Browsers...)
        """
        CREATE TABLE IF NOT EXISTS source_collections (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE,
            created INTEGER NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS sources (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            collection_id INTEGER NOT NULL,
            title TEXT NOT NULL,
            type TEXT NOT NULL,
            content_url TEXT,
            owner TEXT,
            repo TEXT,
            revision TEXT,
            etag TEXT,
            last_modified TEXT,
            write_time INTEGER NOT NULL DEFAULT 0,
            read_time INTEGER NOT NULL DEFAULT 0,
            update_after_hours INTEGER NOT NULL DEFAULT 24,
            status TEXT NOT NULL DEFAULT 'NEW',
            error TEXT,
            file_count INTEGER NOT NULL DEFAULT 0,
            last_updated INTEGER NOT NULL DEFAULT 0,
            size_bytes INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS source_files (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id INTEGER NOT NULL,
            path TEXT NOT NULL,
            blob_sha TEXT,
            chunked INTEGER NOT NULL DEFAULT 0,
            size_bytes INTEGER NOT NULL DEFAULT 0
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS source_chunks (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_id INTEGER NOT NULL,
            source_file_id INTEGER NOT NULL,
            chunk_index INTEGER NOT NULL,
            content TEXT NOT NULL
        )
        """.trimIndent(),
    )

    /** BM25 search over source_chunks_fts; shared with JVM tests (sqlite-jdbc). */
    const val FTS_SEARCH_SQL: String = """
        SELECT bm25(source_chunks_fts) AS bm25_rank,
               s.title, f.path, c.content
        FROM source_chunks_fts
        JOIN source_chunks c ON c.id = source_chunks_fts.rowid
        JOIN source_files f ON f.id = c.source_file_id
        JOIN sources s ON s.id = c.source_id
        WHERE source_chunks_fts MATCH ?
        ORDER BY bm25(source_chunks_fts) LIMIT ?
    """.trimIndent()

    /** FTS5 layer over source_chunks; probed at open time like experiences_fts. */
    val FTS_DDL: List<String> = listOf(
        """
        CREATE VIRTUAL TABLE source_chunks_fts USING fts5(
            content, content='source_chunks', content_rowid='id'
        )
        """.trimIndent(),
        """
        CREATE TRIGGER source_chunks_ai AFTER INSERT ON source_chunks BEGIN
            INSERT INTO source_chunks_fts(rowid, content)
            VALUES (new.id, new.content);
        END
        """.trimIndent(),
        """
        CREATE TRIGGER source_chunks_ad AFTER DELETE ON source_chunks BEGIN
            INSERT INTO source_chunks_fts(source_chunks_fts, rowid, content)
            VALUES ('delete', old.id, old.content);
        END
        """.trimIndent(),
        """
        CREATE TRIGGER source_chunks_au AFTER UPDATE ON source_chunks BEGIN
            INSERT INTO source_chunks_fts(source_chunks_fts, rowid, content)
            VALUES ('delete', old.id, old.content);
            INSERT INTO source_chunks_fts(rowid, content)
            VALUES (new.id, new.content);
        END
        """.trimIndent(),
    )
}
