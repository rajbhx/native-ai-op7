package com.engine.nativeai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceUpdaterTest {

    private val now = 1_800_000_000_000L
    private val hour = 3_600_000L

    private class FakeFetcher : SourceFetcher {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val responses = mutableMapOf<String, FetchResponse>()
        override suspend fun fetch(url: String, headers: Map<String, String>): FetchResponse {
            requests += url to headers
            return responses[url] ?: FetchResponse(404, null)
        }
    }

    @Test
    fun webSource304SendsConditionalHeadersAndSkipsRechunk() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "Docs", SourceType.WEB_PAGE, contentUrl = "https://example.com/doc",
                etag = "\"v1\"", writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        val fetcher = FakeFetcher()
        fetcher.responses["https://example.com/doc"] = FetchResponse(304, null, "\"v1\"", null)
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher)

        val report = updater.updateOnce(now)

        assertEquals(1, report.updated)
        assertTrue(fetcher.requests.first().second.containsKey("If-None-Match"))
        assertTrue(store.chunks.isEmpty())
        assertEquals(SourceStatus.INDEXED, store.sourceById(1)!!.status)
    }

    @Test
    fun githubFetchesOnlyChangedBlobs() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "Repo", SourceType.GITHUB_REPO, owner = "o", repo = "r",
                revision = "old", writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        store.upsertSourceFile(SourceFile(0, 1, "a.txt", blobSha = "shaA", chunked = true, sizeBytes = 10))
        val fetcher = FakeFetcher()
        fetcher.responses["https://api.github.com/repos/o/r/commits?per_page=1"] =
            FetchResponse(200, """[{"sha":"newsha"}]""".toByteArray())
        fetcher.responses["https://api.github.com/repos/o/r/git/trees/newsha?recursive=1"] =
            FetchResponse(
                200,
                ("""{ "tree": [
                      {"path":"a.txt","sha":"shaA","size":10,"type":"blob"},
                      {"path":"b.txt","sha":"shaB","size":5,"type":"blob"}
                    ] }""").toByteArray(),
            )
        fetcher.responses["https://raw.githubusercontent.com/o/r/newsha/b.txt"] =
            FetchResponse(200, "hello world".toByteArray())
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher)

        val report = updater.updateOnce(now)

        assertEquals(1, report.updated)
        assertEquals("newsha", store.sourceById(1)!!.revision)
        // a.txt unchanged => never re-fetched; b.txt changed => fetched once
        assertTrue(fetcher.requests.none { it.first.contains("/newsha/a.txt") })
        assertTrue(fetcher.requests.any { it.first.contains("/newsha/b.txt") })
        assertEquals(2, store.sourceFiles(1).size)
        assertTrue(store.chunks.values.flatten().any { it.content.contains("hello") })
    }

    @Test
    fun githubRateLimit403MarksSourceErrorWithClearMessage() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "Repo", SourceType.GITHUB_REPO, owner = "o", repo = "r",
                writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        val fetcher = FakeFetcher()
        fetcher.responses["https://api.github.com/repos/o/r/commits?per_page=1"] =
            FetchResponse(403, null, rateLimitRemaining = 0L, rateLimitReset = 1_800_360_000L)
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher)

        val report = updater.updateOnce(now)

        assertEquals(1, report.failed)
        assertEquals(SourceStatus.ERROR, store.sourceById(1)!!.status)
        assertTrue(store.sourceById(1)!!.error!!.contains("rate limit"))
    }

    @Test
    fun github429IsRateLimited() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "Repo", SourceType.GITHUB_REPO, owner = "o", repo = "r",
                writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        val fetcher = FakeFetcher()
        fetcher.responses["https://api.github.com/repos/o/r/commits?per_page=1"] =
            FetchResponse(429, null)
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher)

        assertEquals(1, updater.updateOnce(now).failed)
        assertTrue(store.sourceById(1)!!.error!!.contains("rate limit"))
    }

    @Test
    fun nonRateLimit403IsNotMisreported() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "Repo", SourceType.GITHUB_REPO, owner = "o", repo = "r",
                writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        val fetcher = FakeFetcher()
        fetcher.responses["https://api.github.com/repos/o/r/commits?per_page=1"] =
            FetchResponse(403, null, rateLimitRemaining = 50L)
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher)

        updater.updateOnce(now)
        // Remaining > 0 => not the anonymous cap; error must not claim rate limit.
        assertTrue(store.sourceById(1)!!.error!!.contains("HTTP 403"))
    }

    @Test
    fun stoppedUpdateBreaksLoopEarly() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(Source(0, 1, "a", SourceType.RAW_TEXT, writeTime = now - 48 * hour, updateAfterHours = 24))
        store.saveSource(Source(0, 1, "b", SourceType.RAW_TEXT, writeTime = now - 48 * hour, updateAfterHours = 24))
        val fetcher = FakeFetcher() // no responses => every fetch 404 => failures
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher)
        updater.updateStop()
        val report = updater.updateOnce(now)
        assertTrue(report.stopped)
    }

    @Test
    fun failureMarksSourceError() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "Broken", SourceType.WEB_PAGE, contentUrl = "https://example.com/x",
                writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        val fetcher = FakeFetcher() // 404
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher)
        val report = updater.updateOnce(now)
        assertEquals(1, report.failed)
        assertEquals(SourceStatus.ERROR, store.sourceById(1)!!.status)
        assertNull(store.sourceById(1)!!.revision)
    }

    @Test
    fun documentSourceIndexesExtractedText() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "PDF", SourceType.DOCUMENT,
                contentUrl = "https://example.com/doc.pdf",
                writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        val fetcher = FakeFetcher()
        fetcher.responses["https://example.com/doc.pdf"] =
            FetchResponse(200, "%PDF-1.7 fake".toByteArray())
        val extractor = object : DocumentTextExtractor {
            override val available = true
            override suspend fun extractPdf(bytes: ByteArray, fileName: String): String =
                "extracted pdf body with keywords"
        }
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher, extractor)

        val report = updater.updateOnce(now)

        assertEquals(1, report.updated)
        assertEquals(SourceStatus.INDEXED, store.sourceById(1)!!.status)
        assertTrue(store.chunks.values.flatten().any { it.content.contains("extracted pdf") })
    }

    @Test
    fun documentWithoutExtractorStaysMetadataOnlyWithError() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "PDF", SourceType.DOCUMENT,
                contentUrl = "https://example.com/doc.pdf",
                writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        val fetcher = FakeFetcher()
        fetcher.responses["https://example.com/doc.pdf"] =
            FetchResponse(200, "%PDF-1.7 fake".toByteArray())
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher, textExtractor = null)

        val report = updater.updateOnce(now)

        assertEquals(1, report.failed)
        assertEquals(SourceStatus.ERROR, store.sourceById(1)!!.status)
        assertTrue(store.sourceById(1)!!.error!!.contains("Termux"))
        assertTrue(store.chunks.values.flatten().isEmpty())
    }

    @Test
    fun unchangedDocumentSkipsReExtraction() = runBlocking {
        val store = FakeSourceStore()
        store.saveSource(
            Source(0, 1, "PDF", SourceType.DOCUMENT,
                contentUrl = "https://example.com/doc.pdf",
                writeTime = now - 48 * hour, updateAfterHours = 24),
        )
        store.upsertSourceFile(SourceFile(0, 1, "root", blobSha = "doc-1-13", chunked = true, sizeBytes = 13))
        store.replaceSourceChunks(1, 1, listOf("old"))
        val fetcher = FakeFetcher()
        fetcher.responses["https://example.com/doc.pdf"] =
            FetchResponse(200, "%PDF-1.7 fake".toByteArray()) // 13 bytes
        var extractCalls = 0
        val extractor = object : DocumentTextExtractor {
            override val available = true
            override suspend fun extractPdf(bytes: ByteArray, fileName: String): String {
                extractCalls++
                return "new text"
            }
        }
        val updater = SourceUpdater(SourceRegistry(store), store, fetcher, extractor)

        updater.updateOnce(now)

        assertEquals(0, extractCalls)
        assertTrue(store.chunks.values.flatten().all { it.content == "old" })
    }
}
