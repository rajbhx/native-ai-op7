package com.engine.nativeai

import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceStoreJdbcTest {

    @Test
    fun migrationV3ToV4RetainsExistingData() {
        Class.forName("org.sqlite.JDBC")
        val conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { it.execute(
            "CREATE TABLE experiences (id INTEGER PRIMARY KEY AUTOINCREMENT, problem_summary TEXT NOT NULL, " +
                "approach_summary TEXT, tool_used TEXT, result_summary TEXT, success INTEGER NOT NULL DEFAULT 0, " +
                "confidence REAL NOT NULL DEFAULT 0.5, utility_score REAL NOT NULL DEFAULT 0.5, " +
                "verified INTEGER NOT NULL DEFAULT 0, timestamp INTEGER NOT NULL)",
        ) }
        conn.createStatement().use { it.execute(
            "CREATE VIRTUAL TABLE experiences_fts USING fts5(problem_summary, content='experiences', content_rowid='id')",
        ) }
        conn.prepareStatement(
            "INSERT INTO experiences(problem_summary, success, timestamp) VALUES(?, 1, ?)",
        ).use { ps -> ps.setString(1, "old memory survives"); ps.setLong(2, 123L); ps.executeUpdate() }

        // Run the v4 migration exactly like onUpgrade does.
        SourceSchema.DDL.forEach { conn.createStatement().use { st -> st.execute(it) } }
        SourceSchema.FTS_DDL.forEach { conn.createStatement().use { st -> st.execute(it) } }

        val kept = conn.createStatement().use {
            it.executeQuery("SELECT problem_summary FROM experiences").use { rs ->
                if (rs.next()) rs.getString(1) else null
            }
        }
        assertEquals("old memory survives", kept)
        val srcTables = conn.createStatement().use {
            it.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name LIKE 'source_%'").use { rs ->
                buildList { while (rs.next()) add(rs.getString(1)) }
            }
        }
        assertTrue("source tables created: $srcTables", srcTables.contains("sources"))
        assertTrue(srcTables.contains("source_chunks"))
        conn.close()
    }

    @Test
    fun evictionDropsLeastRecentlyReadSources() {
        val store = JdbcSourceStore.open(createFts = false)
        store.upsertSourceCollection("General")
        repeat(25) { i ->
            store.saveSource(
                Source(0, 1, "s$i", SourceType.RAW_TEXT, writeTime = 0, readTime = i * 1000L),
            )
        }
        val removed = store.evictSources(20)
        assertEquals(5, removed)
        assertEquals(20, store.sources().size)
        // Least-recently-read (read_time 0..4) are the ones removed.
        val titles = store.sources().map { it.title }.toSet()
        assertFalse(titles.contains("s0"))
        assertTrue(titles.contains("s24"))
    }

    @Test
    fun bm25SearchRanksRelevantChunks() {
        val store = JdbcSourceStore.open(createFts = true)
        store.upsertSourceCollection("AI")
        store.saveSource(
            Source(0, 1, "llama.cpp", SourceType.GITHUB_REPO, owner = "ggerganov", repo = "llama.cpp"),
        )
        val src = store.sources().first()
        val fileId = store.upsertSourceFile(SourceFile(0, src.id, "README.md", "abc", sizeBytes = 1))
        store.replaceSourceChunks(
            src.id, fileId,
            listOf(
                "The GGUF format stores quantized model weights for CPU inference.",
                "KV cache and context management keep generation bounded.",
            ),
        )
        val hits = store.searchSources("gguf", 5)
        assertTrue(hits.isNotEmpty())
        assertEquals("llama.cpp", hits[0].sourceTitle)
        assertTrue(hits[0].content.contains("GGUF"))
    }

    @Test
    fun likeFallbackSearchesWithoutFts() {
        val store = JdbcSourceStore.open(createFts = false)
        store.upsertSourceCollection("AI")
        store.saveSource(Source(0, 1, "lite rt", SourceType.GITHUB_REPO))
        val src = store.sources().first()
        val fileId = store.upsertSourceFile(SourceFile(0, src.id, "guide.md", "x", sizeBytes = 1))
        store.replaceSourceChunks(src.id, fileId, listOf("NNAPI delegates run on device"))
        val hits = store.searchSources("nnapi", 5)
        assertTrue(hits.isNotEmpty())
        assertEquals("lite rt", hits[0].sourceTitle)
    }

    @Test
    fun rawTextSourceIndexesAndSearches() {
        val store = JdbcSourceStore.open(createFts = true)
        val id = store.saveSource(Source(0, 1, "notes", SourceType.RAW_TEXT))
        val fileId = store.upsertSourceFile(SourceFile(0, id, "root.txt", "raw", sizeBytes = 4))
        store.replaceSourceChunks(id, fileId, SourceChunker.chunk("OnePlus 7 Snapdragon 855 memory budget"))
        val hits = store.searchSources("snapdragon", 5)
        assertTrue(hits.isNotEmpty())
        assertEquals("notes", hits[0].sourceTitle)
    }
    @Test
    fun metaKeyValueRoundTrip() {
        val store = JdbcSourceStore.open(createFts = false)
        assertEquals(null, store.metaGet("source_catalog_version"))
        store.metaSet("source_catalog_version", "2")
        store.metaSet("source_catalog_version", "3")
        assertEquals("3", store.metaGet("source_catalog_version"))
        assertEquals(null, store.metaGet("missing"))
    }

}
