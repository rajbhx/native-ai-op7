package com.engine.nativeai

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase 2 — Fast Local Memory (raw SQLite + FTS5, no Room).
 *
 * Stores structured experience summaries (never raw chain-of-thought) so the
 * memory stays bounded and privacy-safe (gold-standard spec §8-10).
 * Hybrid retrieval: FTS5/BM25 candidates -> utility + recency ranking -> Top-K.
 */
class MemoryDatabase(context: Context) :
    SQLiteOpenHelper(context, "memory.db", null, SCHEMA_VERSION),
    SourceStore {

    /** False when the platform SQLite lacks the FTS5 module (seen on OP7/OxygenOS
     *  Android 10: "no such module: fts5"); retrieval falls back to LIKE matching. */
    private var ftsAvailable = true

    /** Re-verify FTS5 on EVERY open, not just schema creation. ftsAvailable must
     *  not survive process restarts as true on OEM SQLite without fts5 — onCreate
     *  only runs when the DB file is first created (A20 regression: A27). */
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        ftsAvailable = probeFts5(db)
    }

    private fun probeFts5(db: SQLiteDatabase): Boolean = try {
        // Cheap probe; only meaningful once experiences_fts exists (onConfigure
        // runs before onCreate on first open, where onCreate still sets the flag).
        db.rawQuery("SELECT rowid FROM experiences_fts LIMIT 0", null).use { }
        true
    } catch (e: SQLiteException) {
        false
    }

    companion object {
        private const val SCHEMA_VERSION = SourceSchema.SCHEMA_VERSION
        private const val DEFAULT_TOP_K = 3  // spec: Top K = 3 default
        private const val DAY_MS = 86_400_000L
        private const val RECENCY_WINDOW_DAYS = 30.0
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE experiences (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                problem_summary TEXT NOT NULL,
                approach_summary TEXT,
                tool_used TEXT,
                result_summary TEXT,
                success INTEGER NOT NULL DEFAULT 0,
                confidence REAL NOT NULL DEFAULT 0.5,
                utility_score REAL NOT NULL DEFAULT 0.5,
                verified INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL
            )""",
        )
        // External-content FTS5 index; triggers keep it in sync. Some OEM
        // SQLite builds (OP7/OxygenOS Android 10) ship without the fts5
        // module, so degrade gracefully instead of failing schema creation.
        try {
            db.execSQL(
                """CREATE VIRTUAL TABLE experiences_fts USING fts5(
                    problem_summary, approach_summary, tool_used, result_summary,
                    content='experiences', content_rowid='id'
                )""",
            )
            db.execSQL(
                """CREATE TRIGGER experiences_ai AFTER INSERT ON experiences BEGIN
                    INSERT INTO experiences_fts(rowid, problem_summary, approach_summary,
                        tool_used, result_summary)
                    VALUES (new.id, new.problem_summary, new.approach_summary,
                        new.tool_used, new.result_summary);
                END""",
            )
            db.execSQL(
                """CREATE TRIGGER experiences_ad AFTER DELETE ON experiences BEGIN
                    INSERT INTO experiences_fts(experiences_fts, rowid, problem_summary,
                        approach_summary, tool_used, result_summary)
                    VALUES ('delete', old.id, old.problem_summary, old.approach_summary,
                        old.tool_used, old.result_summary);
                END""",
            )
            db.execSQL(
                """CREATE TRIGGER experiences_au AFTER UPDATE ON experiences BEGIN
                    INSERT INTO experiences_fts(experiences_fts, rowid, problem_summary,
                        approach_summary, tool_used, result_summary)
                    VALUES ('delete', old.id, old.problem_summary, old.approach_summary,
                        old.tool_used, old.result_summary);
                    INSERT INTO experiences_fts(rowid, problem_summary, approach_summary,
                        tool_used, result_summary)
                    VALUES (new.id, new.problem_summary, new.approach_summary,
                        new.tool_used, new.result_summary);
                END""",
            )
            ftsAvailable = true
        } catch (e: SQLiteException) {
            ftsAvailable = false
        }
        db.execSQL(
            """CREATE TABLE semantic_facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject TEXT NOT NULL,
                predicate TEXT NOT NULL,
                object TEXT NOT NULL,
                confidence REAL NOT NULL DEFAULT 0.5,
                last_verified INTEGER NOT NULL DEFAULT 0,
                created INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE tool_results (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                tool_name TEXT NOT NULL,
                input TEXT NOT NULL DEFAULT '',
                output_summary TEXT,
                error_summary TEXT,
                ok INTEGER NOT NULL DEFAULT 0,
                timestamp INTEGER NOT NULL
            )""",
        )
        db.execSQL(
            """CREATE TABLE memory_scores (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                memory_type TEXT NOT NULL,
                memory_id INTEGER NOT NULL,
                utility REAL NOT NULL DEFAULT 0.5,
                last_used INTEGER NOT NULL DEFAULT 0,
                UNIQUE(memory_type, memory_id)
            )""",
        )
        db.execSQL(
            """CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                session_meta TEXT
            )""",
        )
        db.execSQL(
            """CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created INTEGER NOT NULL
            )""",
        )
        createSourceSchema(db)
    }

    /** Schema v4 additions (roadmap Phase 4/5); DDL shared with JVM tests. */
    private fun createSourceSchema(db: SQLiteDatabase) {
        SourceSchema.DDL.forEach { db.execSQL(it) }
        try {
            SourceSchema.FTS_DDL.forEach { db.execSQL(it) }
        } catch (e: SQLiteException) {
            // OEM SQLite without the fts5 module (OP7/OxygenOS 10): source
            // search falls back to LIKE like experiences do (A27 regression).
            ftsAvailable = false
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Real migrations only — never drop user data. v3->v4 keeps every
        // existing table and adds the source knowledge base.
        if (oldVersion < 4) createSourceSchema(db)
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    @Synchronized
    fun storeExperience(
        problemSummary: String,
        approachSummary: String,
        toolUsed: String,
        resultSummary: String,
        success: Boolean,
        confidence: Float = 0.5f,
    ): Long {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val id = db.insertOrThrow(
            "experiences", null,
            ContentValues().apply {
                put("problem_summary", problemSummary)
                put("approach_summary", approachSummary)
                put("tool_used", toolUsed)
                put("result_summary", resultSummary)
                put("success", if (success) 1 else 0)
                put("confidence", confidence)
                put("utility_score", 0.5f)
                put("verified", if (success) 1 else 0)
                put("timestamp", now)
            },
        )
        db.insertOrThrow(
            "memory_scores", null,
            ContentValues().apply {
                put("memory_type", "experience")
                put("memory_id", id)
                put("utility", 0.5f)
                put("last_used", now)
            },
        )
        return id
    }

    @Synchronized
    fun storeFact(subject: String, predicate: String, `object`: String, confidence: Float = 0.5f): Long {
        val now = System.currentTimeMillis()
        val db = writableDatabase
        val id = db.insertOrThrow(
            "semantic_facts", null,
            ContentValues().apply {
                put("subject", subject)
                put("predicate", predicate)
                put("object", `object`)
                put("confidence", confidence)
                put("last_verified", now)
                put("created", now)
            },
        )
        db.insertOrThrow(
            "memory_scores", null,
            ContentValues().apply {
                put("memory_type", "fact")
                put("memory_id", id)
                put("utility", 0.5f)
                put("last_used", now)
            },
        )
        return id
    }

    @Synchronized
    fun startSession(meta: String = ""): Long =
        writableDatabase.insertOrThrow(
            "sessions", null,
            ContentValues().apply {
                put("started_at", System.currentTimeMillis())
                put("session_meta", meta)
            },
        )

    @Synchronized
    fun recordMessage(sessionId: Long, role: String, content: String): Long =
        writableDatabase.insertOrThrow(
            "messages", null,
            ContentValues().apply {
                put("session_id", sessionId)
                put("role", role)
                put("content", content.take(4000))
                put("created", System.currentTimeMillis())
            },
        )

    @Synchronized
    fun recentMessages(sessionId: Long, limit: Int = 50): List<Message> =
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE session_id = ? ORDER BY created DESC LIMIT ?",
            arrayOf(sessionId.toString(), limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toMessage()) } }

    @Synchronized
    fun searchMessages(query: String, limit: Int = 20): List<Message> {
        if (query.isBlank()) return emptyList()
        val like = "%${query.trim()}%"
        return readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE content LIKE ? ORDER BY created DESC LIMIT ?",
            arrayOf(like, limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toMessage()) } }
    }

    @Synchronized
    fun endSession(id: Long) {
        writableDatabase.execSQL(
            "UPDATE sessions SET ended_at = ? WHERE id = ?",
            arrayOf<Any>(System.currentTimeMillis(), id),
        )
    }

    fun recentSessions(limit: Int = 10): List<SessionInfo> =
        readableDatabase.rawQuery(
            "SELECT * FROM sessions ORDER BY started_at DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        SessionInfo(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            startedAt = c.getLong(c.getColumnIndexOrThrow("started_at")),
                            endedAt = if (c.isNull(c.getColumnIndexOrThrow("ended_at"))) {
                                null
                            } else {
                                c.getLong(c.getColumnIndexOrThrow("ended_at"))
                            },
                            meta = c.getString(c.getColumnIndexOrThrow("session_meta")),
                        ),
                    )
                }
            }
        }

    /** Verified, successful experiences = training candidates (phase 4 spec). */
    fun verifiedExperiences(limit: Int = 500): List<Experience> =
        readableDatabase.rawQuery(
            "SELECT * FROM experiences WHERE success = 1 AND verified = 1 " +
                "ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toExperience()) } }

    @Synchronized
    fun storeToolResult(
        toolName: String,
        input: String,
        outputSummary: String,
        errorSummary: String?,
        ok: Boolean,
    ): Long =
        writableDatabase.insertOrThrow(
            "tool_results", null,
            ContentValues().apply {
                put("tool_name", toolName)
                put("input", input)
                put("output_summary", outputSummary)
                put("error_summary", errorSummary)
                put("ok", if (ok) 1 else 0)
                put("timestamp", System.currentTimeMillis())
            },
        )

    @Synchronized
    fun markFactVerified(factId: Long) {
        writableDatabase.execSQL(
            "UPDATE semantic_facts SET last_verified = ? WHERE id = ?",
            arrayOf<Any>(System.currentTimeMillis(), factId),
        )
    }

    @Synchronized
    fun markExperienceUsed(experienceId: Long) {
        writableDatabase.execSQL(
            "UPDATE memory_scores SET last_used = ? WHERE memory_type = 'experience' AND memory_id = ?",
            arrayOf<Any>(System.currentTimeMillis(), experienceId),
        )
    }

    // ------------------------------------------------------------------
    // Retrieval (hybrid: FTS5 candidates -> utility/recency -> top-K)
    // ------------------------------------------------------------------

    fun searchSimilarExperiences(query: String, topK: Int = DEFAULT_TOP_K): List<Experience> {
        if (query.isBlank()) return emptyList()
        val now = System.currentTimeMillis()
        // Spec §2.B: score = BM25 relevance * exp(-lambda * ageDays),
        // lambda = 0.05 -> half-life ~13.8 days. Utility/success are small tiebreaks.
        return ftsCandidates(query, limit = 20)
            .map { (rank, e) ->
                val ageDays = (now - e.timestamp).toDouble() / DAY_MS
                val decay = Math.exp(-Op7SystemProfile.DECAY_LAMBDA * ageDays)
                val relevance = -rank // bm25() returns negative, lower = better
                val score = relevance * decay + e.utility * 0.1 + (if (e.success) 0.05 else 0.0)
                e to score
            }
            .sortedByDescending { it.second }
            .take(topK)
            .map { it.first }
    }

    fun queryFacts(subjectPrefix: String? = null): List<Fact> {
        val db = readableDatabase
        val cursor = if (subjectPrefix.isNullOrBlank()) {
            db.rawQuery("SELECT * FROM semantic_facts ORDER BY confidence DESC, last_verified DESC", null)
        } else {
            db.rawQuery(
                "SELECT * FROM semantic_facts WHERE subject LIKE ? ORDER BY confidence DESC",
                arrayOf("$subjectPrefix%"),
            )
        }
        return cursor.use { c -> buildList { while (c.moveToNext()) add(c.toFact()) } }
    }

    fun recentToolResults(limit: Int = 10): List<ToolResult> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM tool_results ORDER BY timestamp DESC LIMIT ?",
            arrayOf(limit.toString()),
        )
        return cursor.use { c -> buildList { while (c.moveToNext()) add(c.toToolResult()) } }
    }

    /** Prompt-ready context block for the agent (phase 3). */
    fun searchContext(query: String, topK: Int = DEFAULT_TOP_K): String {
        val sb = StringBuilder()
        val experiences = searchSimilarExperiences(query, topK)
        if (experiences.isNotEmpty()) {
            sb.append("Relevant past experiences:\n")
            experiences.forEachIndexed { i, e ->
                sb.append(
                    "${i + 1}. [${if (e.success) "ok" else "fail"}] problem: ${e.problemSummary} | " +
                        "approach: ${e.approachSummary} | tool: ${e.toolUsed} | result: ${e.resultSummary}\n",
                )
            }
        }
        val facts = queryFacts().take(3)
        if (facts.isNotEmpty()) {
            sb.append("Known facts:\n")
            facts.forEach { sb.append("- ${it.subject} ${it.predicate} ${it.`object`}\n") }
        }
        return sb.toString().trimEnd()
    }

    // ------------------------------------------------------------------
    // Source Knowledge Base (schema v4, roadmap Phase 4-6)
    // ------------------------------------------------------------------

    @Synchronized
    override fun upsertSourceCollection(name: String): Long {
        val db = writableDatabase
        db.insertWithOnConflict(
            "source_collections", null,
            ContentValues().apply {
                put("name", name)
                put("created", System.currentTimeMillis())
            },
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        return db.rawQuery(
            "SELECT id FROM source_collections WHERE name = ?", arrayOf(name),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
    }

    override fun collections(): List<SourceCollection> =
        readableDatabase.rawQuery(
            "SELECT * FROM source_collections ORDER BY name ASC", null,
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        SourceCollection(
                            id = c.getLong(c.getColumnIndexOrThrow("id")),
                            name = c.getString(c.getColumnIndexOrThrow("name")),
                            created = c.getLong(c.getColumnIndexOrThrow("created")),
                        ),
                    )
                }
            }
        }

    override fun sources(): List<Source> =
        readableDatabase.rawQuery(
            "SELECT * FROM sources ORDER BY title ASC", null,
        ).use { c -> buildList { while (c.moveToNext()) add(c.toSource()) } }

    override fun sourceById(id: Long): Source? =
        readableDatabase.rawQuery(
            "SELECT * FROM sources WHERE id = ?", arrayOf(id.toString()),
        ).use { c -> if (c.moveToFirst()) c.toSource() else null }

    override fun sourceByTitle(title: String): Source? =
        readableDatabase.rawQuery(
            "SELECT * FROM sources WHERE title = ?", arrayOf(title),
        ).use { c -> if (c.moveToFirst()) c.toSource() else null }

    @Synchronized
    override fun saveSource(s: Source): Long {
        val db = writableDatabase
        val existing = if (s.id > 0) sourceById(s.id) else sourceByTitle(s.title)
        return if (existing != null) {
            db.update("sources", s.toValues(), "id = ?", arrayOf(existing.id.toString()))
            existing.id
        } else {
            val id = db.insertOrThrow("sources", null, s.toValues())
            touchSourceRead(id)
            id
        }
    }

    @Synchronized
    override fun deleteSource(id: Long) {
        writableDatabase.execSQL("DELETE FROM source_chunks WHERE source_id = ?", arrayOf(id.toString()))
        writableDatabase.execSQL("DELETE FROM source_files WHERE source_id = ?", arrayOf(id.toString()))
        writableDatabase.execSQL("DELETE FROM sources WHERE id = ?", arrayOf(id.toString()))
    }

    override fun sourceFiles(sourceId: Long): List<SourceFile> =
        readableDatabase.rawQuery(
            "SELECT * FROM source_files WHERE source_id = ? ORDER BY path ASC",
            arrayOf(sourceId.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(c.toSourceFile()) } }

    @Synchronized
    override fun upsertSourceFile(f: SourceFile): Long {
        val db = writableDatabase
        val existing = db.rawQuery(
            "SELECT id FROM source_files WHERE source_id = ? AND path = ?",
            arrayOf(f.sourceId.toString(), f.path),
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        return if (existing > 0) {
            db.update("source_files", f.toValues(), "id = ?", arrayOf(existing.toString()))
            existing
        } else {
            db.insertOrThrow("source_files", null, f.toValues())
        }
    }

    @Synchronized
    override fun deleteSourceFile(fileId: Long) {
        writableDatabase.execSQL("DELETE FROM source_chunks WHERE source_file_id = ?", arrayOf(fileId.toString()))
        writableDatabase.execSQL("DELETE FROM source_files WHERE id = ?", arrayOf(fileId.toString()))
    }

    /** Replaces a file's chunks (changed-blob-only re-chunking, uBO model). */
    @Synchronized
    override fun replaceSourceChunks(sourceId: Long, fileId: Long, chunks: List<String>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("DELETE FROM source_chunks WHERE source_file_id = ?", arrayOf(fileId.toString()))
            chunks.forEachIndexed { i, content ->
                db.insertOrThrow(
                    "source_chunks", null,
                    ContentValues().apply {
                        put("source_id", sourceId)
                        put("source_file_id", fileId)
                        put("chunk_index", i)
                        put("content", content)
                    },
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    override fun touchSourceRead(sourceId: Long) {
        writableDatabase.execSQL(
            "UPDATE sources SET read_time = ? WHERE id = ?",
            arrayOf<Any>(System.currentTimeMillis(), sourceId),
        )
    }

    @Synchronized
    override fun touchSourceWrite(
        sourceId: Long,
        revision: String?,
        etag: String?,
        lastModified: String?,
        status: SourceStatus,
        fileCount: Int,
        sizeBytes: Long,
    ) {
        writableDatabase.execSQL(
            """UPDATE sources SET revision = ?, etag = ?, last_modified = ?,
               write_time = ?, last_updated = ?, status = ?, file_count = ?,
               size_bytes = ? WHERE id = ?""",
            arrayOf<Any?>(
                revision, etag, lastModified,
                System.currentTimeMillis(), System.currentTimeMillis(),
                status.name, fileCount, sizeBytes, sourceId,
            ),
        )
    }

    @Synchronized
    override fun markSourceError(sourceId: Long, message: String) {
        writableDatabase.execSQL(
            """UPDATE sources SET status = ?, error = ?, last_updated = ? WHERE id = ?""",
            arrayOf<Any>(SourceStatus.ERROR.name, message.take(500), System.currentTimeMillis(), sourceId),
        )
    }

    /** uBO-style read-time LRU eviction: drop least-recently-read sources. */
    @Synchronized
    override fun evictSources(keep: Int): Int {
        val db = writableDatabase
        val total = db.rawQuery("SELECT COUNT(*) FROM sources", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }
        val excess = total - keep
        if (excess <= 0) return 0
        val doomed = db.rawQuery(
            "SELECT id FROM sources ORDER BY read_time ASC LIMIT ?",
            arrayOf(excess.toString()),
        ).use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        doomed.forEach { deleteSource(it) }
        return doomed.size
    }

    override fun searchSources(query: String, limit: Int): List<SourceSearchHit> {
        val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return if (ftsAvailable) ftsSourceSearch(terms, limit) else likeSourceSearch(terms, limit)
    }

    /** BM25 over source_chunks_fts when the platform SQLite has fts5. */
    private fun ftsSourceSearch(terms: List<String>, limit: Int): List<SourceSearchHit> {
        val match = terms.joinToString(" OR ") { "\"" + it.replace("\"", "").replace("*", "") + "\"" }
        if (match.isBlank()) return emptyList()
        val cursor = readableDatabase.rawQuery(
            SourceSchema.FTS_SEARCH_SQL,
            arrayOf(match, limit.toString()),
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        SourceSearchHit(
                            sourceTitle = c.getString(c.getColumnIndexOrThrow("title")),
                            filePath = c.getString(c.getColumnIndexOrThrow("path")),
                            content = c.getString(c.getColumnIndexOrThrow("content")),
                            score = c.getFloat(c.getColumnIndexOrThrow("bm25_rank")),
                        ),
                    )
                }
            }
        }
    }

    /** LIKE fallback (OP7/OxygenOS 10 without fts5): term-hit ranking. */
    private fun likeSourceSearch(terms: List<String>, limit: Int): List<SourceSearchHit> {
        val hits = mutableListOf<SourceSearchHit>()
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM source_chunks ORDER BY id DESC LIMIT 5000", null,
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val chunk = c.toSourceChunk()
                val hitsHere = terms.count { chunk.content.contains(it, ignoreCase = true) }
                if (hitsHere > 0) {
                    hits += SourceSearchHit(
                        sourceTitle = sourceById(chunk.sourceId)?.title ?: "?",
                        filePath = sourceFileTitle(chunk.sourceFileId),
                        content = chunk.content,
                        score = -hitsHere.toFloat(),
                    )
                }
            }
        }
        return hits.sortedBy { it.score }.take(limit)
    }

    private fun sourceFileTitle(fileId: Long): String =
        readableDatabase.rawQuery(
            "SELECT path FROM source_files WHERE id = ?", arrayOf(fileId.toString()),
        ).use { c -> if (c.moveToFirst()) c.getString(0) else "?" }

    private fun Source.toValues(): ContentValues = ContentValues().apply {
        put("collection_id", collectionId)
        put("title", title)
        put("type", type.name)
        put("content_url", contentUrl)
        put("owner", owner)
        put("repo", repo)
        put("revision", revision)
        put("etag", etag)
        put("last_modified", lastModified)
        put("write_time", writeTime)
        put("read_time", readTime)
        put("update_after_hours", updateAfterHours)
        put("status", status.name)
        put("error", error)
        put("file_count", fileCount)
        put("last_updated", lastUpdated)
        put("size_bytes", sizeBytes)
    }

    private fun SourceFile.toValues(): ContentValues = ContentValues().apply {
        put("source_id", sourceId)
        put("path", path)
        put("blob_sha", blobSha)
        put("chunked", if (chunked) 1 else 0)
        put("size_bytes", sizeBytes)
    }

    private fun Cursor.toSource(): Source = Source(
        id = getLong(getColumnIndexOrThrow("id")),
        collectionId = getLong(getColumnIndexOrThrow("collection_id")),
        title = getString(getColumnIndexOrThrow("title")),
        type = SourceType.valueOf(getString(getColumnIndexOrThrow("type"))),
        contentUrl = getStringOrNull("content_url"),
        owner = getStringOrNull("owner"),
        repo = getStringOrNull("repo"),
        revision = getStringOrNull("revision"),
        etag = getStringOrNull("etag"),
        lastModified = getStringOrNull("last_modified"),
        writeTime = getLong(getColumnIndexOrThrow("write_time")),
        readTime = getLong(getColumnIndexOrThrow("read_time")),
        updateAfterHours = getLong(getColumnIndexOrThrow("update_after_hours")),
        status = SourceStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
        error = getStringOrNull("error"),
        fileCount = getInt(getColumnIndexOrThrow("file_count")),
        lastUpdated = getLong(getColumnIndexOrThrow("last_updated")),
        sizeBytes = getLong(getColumnIndexOrThrow("size_bytes")),
    )

    private fun Cursor.toSourceFile(): SourceFile = SourceFile(
        id = getLong(getColumnIndexOrThrow("id")),
        sourceId = getLong(getColumnIndexOrThrow("source_id")),
        path = getString(getColumnIndexOrThrow("path")),
        blobSha = getStringOrNull("blob_sha"),
        chunked = getInt(getColumnIndexOrThrow("chunked")) == 1,
        sizeBytes = getLong(getColumnIndexOrThrow("size_bytes")),
    )

    private fun Cursor.toSourceChunk(): SourceChunk = SourceChunk(
        id = getLong(getColumnIndexOrThrow("id")),
        sourceId = getLong(getColumnIndexOrThrow("source_id")),
        sourceFileId = getLong(getColumnIndexOrThrow("source_file_id")),
        chunkIndex = getInt(getColumnIndexOrThrow("chunk_index")),
        content = getString(getColumnIndexOrThrow("content")),
    )

    private fun Cursor.getStringOrNull(col: String): String? =
        if (isNull(getColumnIndexOrThrow(col))) null else getString(getColumnIndexOrThrow(col))

    // ------------------------------------------------------------------
    // Maintenance (async — spec §10)
    // ------------------------------------------------------------------


    suspend fun decayUnusedMemories() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            """UPDATE memory_scores
               SET utility = utility * exp(-0.05 * ((? - last_used) / 86400000.0))
               WHERE last_used > 0 AND (? - last_used) > 0""",
            arrayOf<Any>(now, now),
        )
        syncExperienceUtility()
    }

    suspend fun deleteLowUtilityMemories(threshold: Float = 0.1f) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 30 * DAY_MS
        writableDatabase.execSQL(
            "DELETE FROM experiences WHERE utility_score < ? AND timestamp < ?",
            arrayOf<Any>(threshold, cutoff),
        )
        writableDatabase.execSQL(
            """DELETE FROM semantic_facts WHERE id IN (
                SELECT f.id FROM semantic_facts f
                JOIN memory_scores s ON s.memory_type = 'fact' AND s.memory_id = f.id
                WHERE s.utility < ? AND f.created < ?)""",
            arrayOf<Any>(threshold, cutoff),
        )
    }

    suspend fun verifyStaleFacts(maxAgeDays: Long = 90): List<Fact> = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - maxAgeDays * DAY_MS
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM semantic_facts WHERE last_verified < ? ORDER BY last_verified ASC",
            arrayOf(cutoff.toString()),
        )
        cursor.use { c -> buildList { while (c.moveToNext()) add(c.toFact()) } }
    }

    suspend fun vacuumDatabase() = withContext(Dispatchers.IO) {
        writableDatabase.execSQL("VACUUM")
    }

    fun counts(): String {
        val db = readableDatabase
        fun count(table: String): Long =
            db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
                if (c.moveToFirst()) c.getLong(0) else 0L
            }
        return "experiences=${count("experiences")} facts=${count("semantic_facts")} " +
            "tool_results=${count("tool_results")} sessions=${count("sessions")}"
    }

    // ------------------------------------------------------------------

    private data class FtsCandidate(val rank: Float, val experience: Experience)

    private fun ftsCandidates(query: String, limit: Int): List<FtsCandidate> {
        val terms = query.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        return if (ftsAvailable) fts5Candidates(terms, limit) else likeCandidates(terms, limit)
    }

    /** BM25-ranked candidates when FTS5 is available (spec §2.B). */
    private fun fts5Candidates(terms: List<String>, limit: Int): List<FtsCandidate> {
        val match = terms
            .joinToString(" OR ") { "\"" + it.replace("\"", "").replace("*", "") + "\"" }
        if (match.isBlank()) return emptyList()
        val cursor = readableDatabase.rawQuery(
            """SELECT bm25(experiences_fts) AS bm25_rank,
                      e.id, e.problem_summary, e.approach_summary, e.tool_used,
                      e.result_summary, e.success, e.confidence, e.utility_score, e.timestamp
               FROM experiences_fts
               JOIN experiences e ON e.id = experiences_fts.rowid
               WHERE experiences_fts MATCH ?
               ORDER BY bm25(experiences_fts) LIMIT ?""",
            arrayOf(match, limit.toString()),
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        FtsCandidate(
                            rank = c.getFloat(c.getColumnIndexOrThrow("bm25_rank")),
                            experience = c.toExperience(),
                        ),
                    )
                }
            }
        }
    }

    /** LIKE-based fallback when the platform SQLite has no FTS5 module.
     *  Approximate ranking: more matched query terms (across any text column)
     *  ranks higher; newest wins ties. Bounded scan keeps it cheap. */
    private fun likeCandidates(terms: List<String>, limit: Int): List<FtsCandidate> {
        val cursor = readableDatabase.rawQuery(
            "SELECT * FROM experiences ORDER BY timestamp DESC LIMIT 2000", null,
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val e = c.toExperience()
                    val hits = terms.count { term ->
                        e.problemSummary.contains(term, ignoreCase = true) ||
                            e.approachSummary.contains(term, ignoreCase = true) ||
                            e.resultSummary.contains(term, ignoreCase = true)
                    }
                    if (hits > 0) add(FtsCandidate(-hits.toFloat(), e))
                }
            }.sortedWith(compareBy({ it.rank }, { -it.experience.timestamp }))
        }.take(limit)
    }

    private fun syncExperienceUtility() {
        writableDatabase.execSQL(
            """UPDATE experiences SET utility_score = COALESCE((
                SELECT utility FROM memory_scores
                WHERE memory_type = 'experience' AND memory_id = experiences.id), utility_score)""",
        )
    }

    private fun Cursor.toExperience() = Experience(
        id = getLong(getColumnIndexOrThrow("id")),
        problemSummary = getString(getColumnIndexOrThrow("problem_summary")),
        approachSummary = getString(getColumnIndexOrThrow("approach_summary")),
        toolUsed = getString(getColumnIndexOrThrow("tool_used")),
        resultSummary = getString(getColumnIndexOrThrow("result_summary")),
        success = getInt(getColumnIndexOrThrow("success")) == 1,
        confidence = getFloat(getColumnIndexOrThrow("confidence")),
        utility = getFloat(getColumnIndexOrThrow("utility_score")),
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
    )

    private fun Cursor.toFact() = Fact(
        id = getLong(getColumnIndexOrThrow("id")),
        subject = getString(getColumnIndexOrThrow("subject")),
        predicate = getString(getColumnIndexOrThrow("predicate")),
        `object` = getString(getColumnIndexOrThrow("object")),
        confidence = getFloat(getColumnIndexOrThrow("confidence")),
        lastVerified = getLong(getColumnIndexOrThrow("last_verified")),
    )

    private fun Cursor.toMessage() = Message(
        id = getLong(getColumnIndexOrThrow("id")),
        sessionId = getLong(getColumnIndexOrThrow("session_id")),
        role = getString(getColumnIndexOrThrow("role")),
        content = getString(getColumnIndexOrThrow("content")),
        created = getLong(getColumnIndexOrThrow("created")),
    )

    private fun Cursor.toToolResult() = ToolResult(
        id = getLong(getColumnIndexOrThrow("id")),
        toolName = getString(getColumnIndexOrThrow("tool_name")),
        input = getString(getColumnIndexOrThrow("input")),
        outputSummary = getString(getColumnIndexOrThrow("output_summary")),
        errorSummary = getStringOrNull(getColumnIndexOrThrow("error_summary")),
        ok = getInt(getColumnIndexOrThrow("ok")) == 1,
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
    )

    private fun Cursor.getStringOrNull(col: Int): String? =
        if (isNull(col)) null else getString(col)
}
