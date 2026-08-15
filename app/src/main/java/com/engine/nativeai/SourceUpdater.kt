package com.engine.nativeai

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * uBO-style serial source updater (clean-room): one run at a time, most
 * obsolete source first, conditional fetches (ETag/Last-Modified/revision),
 * changed-blob-only re-chunking, hard per-run byte budget, interruptible.
 */
class SourceUpdater(
    private val registry: SourceRegistry,
    private val db: SourceStore,
    private val fetcher: SourceFetcher = HttpSourceFetcher(),
) {
    data class UpdateReport(
        val skippedBusy: Boolean = false,
        var updated: Int = 0,
        var failed: Int = 0,
        var stopped: Boolean = false,
    )

    private val running = AtomicBoolean(false)
    @Volatile private var stopRequested = false

    val isRunning: Boolean get() = running.get()

    /** Request the running update loop to stop after the current source. */
    fun updateStop() {
        stopRequested = true
    }

    /** Refresh every due source, most obsolete first (uBO gentle updater). */
    suspend fun updateOnce(now: Long = System.currentTimeMillis()): UpdateReport {
        if (!running.compareAndSet(false, true)) return UpdateReport(skippedBusy = true)
        val report = UpdateReport()
        try {
            val candidates = registry.updateCandidates(now)
            var budgetBytes = MAX_BUDGET_BYTES
            for (s in candidates) {
                if (stopRequested) { report.stopped = true; break }
                val spent = updateSourceInternal(s, budgetBytes)
                if (spent >= 0) {
                    report.updated++
                    budgetBytes -= spent
                    if (budgetBytes <= 0) break
                } else {
                    report.failed++
                }
            }
        } finally {
            running.set(false)
            stopRequested = false
        }
        return report
    }

    /** Refresh one source regardless of staleness (add / manual refresh). */
    suspend fun updateSource(id: Long): UpdateReport {
        if (!running.compareAndSet(false, true)) return UpdateReport(skippedBusy = true)
        val report = UpdateReport()
        try {
            val s = db.sourceById(id)
            if (s == null) report.failed++
            else if (updateSourceInternal(s, MAX_BUDGET_BYTES) >= 0) report.updated++
            else report.failed++
        } finally {
            running.set(false)
            stopRequested = false
        }
        return report
    }

    /** Returns bytes consumed (>=0 ok, -1 failed). */
    private suspend fun updateSourceInternal(s: Source, budgetBytes: Long): Long {
        return try {
            val spent = when (s.type) {
                SourceType.GITHUB_REPO -> updateGithubRepo(s, budgetBytes)
                SourceType.WEB_PAGE -> updateHttpSource(s, budgetBytes)
                // RAW_TEXT is indexed directly on add (no remote refresh).
                SourceType.RAW_TEXT -> 0L
                SourceType.LOCAL_FILE -> updateLocalFile(s, budgetBytes)
                SourceType.DOCUMENT -> 0L // metadata-only for now (ADR-007: text extraction deferred)
            }
            registry.touchRead(s.id)
            spent
        } catch (e: Exception) {
            registry.markError(s.id, e.message ?: "update failed")
            -1L
        }
    }

    private suspend fun updateGithubRepo(s: Source, budgetBytes: Long): Long {
        val owner = s.owner ?: throw IllegalStateException("github source missing owner")
        val repo = s.repo ?: throw IllegalStateException("github source missing repo")
        val apiBase = "https://api.github.com/repos/$owner/$repo"
        val commitsResp = fetcher.fetch("$apiBase/commits?per_page=1")
        requireNotRateLimited(commitsResp, "commits")
        val commits = commitsResp.body?.toString(Charsets.UTF_8)
            ?: throw IllegalStateException("no commits response")
        val sha = GithubTreeParser.latestCommitSha(commits)
            ?: throw IllegalStateException("no commit sha in response")
        if (sha == s.revision) {
            // Up-to-date: bump write_time so it stops being due (uBO 304 path).
            db.touchSourceWrite(s.id, sha, null, null, SourceStatus.INDEXED, s.fileCount, s.sizeBytes)
            return 0L
        }
        val treeResp = fetcher.fetch("$apiBase/git/trees/$sha?recursive=1")
        requireNotRateLimited(treeResp, "tree")
        val treeBody = treeResp.body?.toString(Charsets.UTF_8)
            ?: throw IllegalStateException("no tree response")
        val entries = GithubTreeParser.parseTree(treeBody)
            .filter { it.size <= SourceCapabilities.MAX_FILE_BYTES }
            .take(SourceCapabilities.MAX_FILES_PER_SOURCE)
        val existing = db.sourceFiles(s.id).associate { it.path to (it.blobSha ?: "") }
        val changed = GithubTreeParser.changedPaths(entries, existing)
        var bytes = 0L
        for (e in changed) {
            if (stopRequested) break
            if (bytes + e.size > budgetBytes) break
            val raw = fetcher.fetch("https://raw.githubusercontent.com/$owner/$repo/$sha/${e.path}")
            val body = raw.body ?: continue
            if (!SourceChunker.shouldIngest(body, e.path)) continue
            val chunks = SourceChunker.chunk(String(body, Charsets.UTF_8))
            val fileId = db.upsertSourceFile(
                SourceFile(id = 0, sourceId = s.id, path = e.path, blobSha = e.sha, sizeBytes = body.size.toLong()),
            )
            db.replaceSourceChunks(s.id, fileId, chunks)
            bytes += body.size
        }
        // Drop files removed upstream (uBO keeps index consistent with source).
        GithubTreeParser.removedPaths(entries, existing).forEach { path ->
            db.sourceFiles(s.id).firstOrNull { it.path == path }?.let { db.deleteSourceFile(it.id) }
        }
        val fileCount = db.sourceFiles(s.id).size
        val sizeBytes = db.sourceFiles(s.id).sumOf { it.sizeBytes }
        db.touchSourceWrite(s.id, sha, null, null, SourceStatus.INDEXED, fileCount, sizeBytes)
        return bytes
    }

    private suspend fun updateHttpSource(s: Source, budgetBytes: Long): Long {
        val url = s.contentUrl ?: throw IllegalStateException("http source missing url")
        val headers = buildMap {
            s.etag?.let { put("If-None-Match", it) }
            s.lastModified?.let { put("If-Modified-Since", it) }
        }
        val resp = fetcher.fetch(url, headers)
        if (resp.notModified) {
            db.touchSourceWrite(s.id, null, resp.etag ?: s.etag, resp.lastModified ?: s.lastModified,
                SourceStatus.INDEXED, s.fileCount, s.sizeBytes)
            return 0L
        }
        if (resp.status !in 200..299) throw IllegalStateException("HTTP ${resp.status} for $url")
        val body = resp.body ?: throw IllegalStateException("empty body")
        if (!SourceChunker.shouldIngest(body, "root.txt")) throw IllegalStateException("content not text")
        val text = String(body, Charsets.UTF_8)
        val chunks = SourceChunker.chunk(text)
        val fileId = db.upsertSourceFile(
            SourceFile(id = 0, sourceId = s.id, path = "root", blobSha = resp.etag, sizeBytes = body.size.toLong()),
        )
        db.replaceSourceChunks(s.id, fileId, chunks)
        db.touchSourceWrite(s.id, null, resp.etag, resp.lastModified, SourceStatus.INDEXED, 1, body.size.toLong())
        return body.size.toLong()
    }

    private suspend fun updateLocalFile(s: Source, budgetBytes: Long): Long {
        val path = s.contentUrl ?: throw IllegalStateException("local source missing path")
        val f = File(path)
        if (!f.exists()) throw IllegalStateException("file not found: $path")
        if (f.length() > SourceCapabilities.MAX_FILE_BYTES) throw IllegalStateException("file too large")
        val bytes = f.readBytes()
        if (!SourceChunker.shouldIngest(bytes, f.name)) throw IllegalStateException("not a text file")
        val marker = "${f.lastModified()}-${f.length()}"
        val existing = db.sourceFiles(s.id).firstOrNull()
        if (existing?.blobSha == marker) {
            db.touchSourceWrite(s.id, marker, null, null, SourceStatus.INDEXED, 1, f.length())
            return 0L
        }
        val chunks = SourceChunker.chunk(String(bytes, Charsets.UTF_8))
        val fileId = db.upsertSourceFile(
            SourceFile(id = 0, sourceId = s.id, path = f.name, blobSha = marker, sizeBytes = f.length()),
        )
        db.replaceSourceChunks(s.id, fileId, chunks)
        db.touchSourceWrite(s.id, marker, null, null, SourceStatus.INDEXED, 1, f.length())
        return f.length()
    }

    private fun requireNotRateLimited(resp: FetchResponse, what: String) {
        if (resp.rateLimited && (resp.rateLimitRemaining == null || resp.rateLimitRemaining == 0L)) {
            throw IllegalStateException(GITHUB_RATE_LIMIT_MESSAGE)
        }
        if (resp.status !in 200..299) throw IllegalStateException("HTTP ${resp.status} for $what")
    }

    private companion object {
        /** Per-run cap; protects battery on the 855 while keeping repos fresh. */
        const val MAX_BUDGET_BYTES = 16L * 1024 * 1024

        /** Anonymous GitHub cap: 60 req/hr (no token path this phase). */
        const val GITHUB_RATE_LIMIT_MESSAGE =
            "GitHub API rate limit reached (60/hr anonymous) \u2014 retry later"
    }
}
