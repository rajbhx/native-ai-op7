package com.engine.nativeai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hybrid context tests (roadmap Phase 6): citations + knowledge-seeking classification. */
class SourceContextTest {

    @Test
    fun formatSourceHitsEmitsCitations() {
        val hits = listOf(
            SourceSearchHit("uBlock Origin", "src/js/scripting.js", "some content", 1.0f),
            SourceSearchHit("MemPalace", "README.md", "other content", 0.5f),
        )
        val text = formatSourceHits(hits)
        assertTrue(text.contains("[uBlock Origin/src/js/scripting.js]"))
        assertTrue(text.contains("[MemPalace/README.md]"))
        assertTrue(text.contains("some content"))
    }

    @Test
    fun formatSourceHitsTruncatesLongContent() {
        val hits = listOf(SourceSearchHit("s", "f", "x".repeat(2000), 1.0f))
        val text = formatSourceHits(hits)
        assertTrue("expected content capped, got ${text.length}", text.length < 500)
    }

    @Test
    fun knowledgeSeekingTasksConsultSources() {
        assertTrue(TaskType.RESEARCH.consultsSources())
        assertTrue(TaskType.SUMMARIZATION.consultsSources())
        assertTrue(TaskType.DOCUMENT_ANALYSIS.consultsSources())
        assertTrue(TaskType.DEBUGGING.consultsSources())
        assertTrue(TaskType.CODING.consultsSources())
        assertFalse(TaskType.CHAT.consultsSources())
        assertFalse(TaskType.REASONING.consultsSources())
        assertFalse(TaskType.VISION.consultsSources())
    }

    @Test
    fun searchOverStoreReturnsCitableHit() = runBlocking {
        val store = FakeSourceStore()
        val collectionId = store.upsertSourceCollection("AI")
        val sourceId = store.saveSource(
            Source(
                id = 0, collectionId = collectionId, title = "uBlock Origin",
                type = SourceType.GITHUB_REPO, status = SourceStatus.INDEXED,
            ),
        )
        val fileId = store.upsertSourceFile(SourceFile(id = 0, sourceId = sourceId, path = "src/main.js"))
        store.replaceSourceChunks(sourceId, fileId, listOf("blocklist matching happens in main.js"))

        val hits = SourceSearch(store).search("blocklist", 3)
        assertEquals(1, hits.size)
        val text = formatSourceHits(hits)
        assertTrue(text.contains("[uBlock Origin/src/main.js]"))
        assertTrue(text.contains("blocklist matching"))
    }
}
