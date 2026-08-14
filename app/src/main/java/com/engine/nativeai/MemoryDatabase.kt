package com.engine.nativeai

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
    SQLiteOpenHelper(context, "memory.db", null, SCHEMA_VERSION) {

    companion object {
        private const val SCHEMA_VERSION = 1
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
        // External-content FTS5 index; triggers keep it in sync.
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
                input_hash TEXT NOT NULL,
                output_summary TEXT,
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Dev-only reset until real migrations arrive (phase 8 hardening).
        db.execSQL("DROP TABLE IF EXISTS experiences_fts")
        db.execSQL("DROP TABLE IF EXISTS experiences")
        db.execSQL("DROP TABLE IF EXISTS semantic_facts")
        db.execSQL("DROP TABLE IF EXISTS tool_results")
        db.execSQL("DROP TABLE IF EXISTS memory_scores")
        db.execSQL("DROP TABLE IF EXISTS sessions")
        onCreate(db)
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
    fun storeToolResult(toolName: String, inputHash: String, outputSummary: String, ok: Boolean): Long =
        writableDatabase.insertOrThrow(
            "tool_results", null,
            ContentValues().apply {
                put("tool_name", toolName)
                put("input_hash", inputHash)
                put("output_summary", outputSummary)
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
        return ftsCandidates(query, limit = 20)
            .map { e ->
                val ageDays = (now - e.timestamp).toDouble() / DAY_MS
                val recency = 1.0 - minOf(ageDays, RECENCY_WINDOW_DAYS) / RECENCY_WINDOW_DAYS
                val score = e.utility * 0.5 + recency * 0.3 + (if (e.success) 0.2 else 0.0)
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
    // Maintenance (async — spec §10)
    // ------------------------------------------------------------------

    suspend fun decayUnusedMemories() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        writableDatabase.execSQL(
            """UPDATE memory_scores
               SET utility = utility * exp(-0.02 * ((? - last_used) / 86400000.0))
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

    private fun ftsCandidates(query: String, limit: Int): List<Experience> {
        val match = query.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .joinToString(" OR ") { "\"" + it.replace("\"", "").replace("*", "") + "\"" }
        if (match.isBlank()) return emptyList()
        val cursor = readableDatabase.rawQuery(
            """SELECT e.id, e.problem_summary, e.approach_summary, e.tool_used,
                      e.result_summary, e.success, e.confidence, e.utility_score, e.timestamp
               FROM experiences_fts
               JOIN experiences e ON e.id = experiences_fts.rowid
               WHERE experiences_fts MATCH ?
               ORDER BY bm25(experiences_fts) LIMIT ?""",
            arrayOf(match, limit.toString()),
        )
        return cursor.use { c -> buildList { while (c.moveToNext()) add(c.toExperience()) } }
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

    private fun Cursor.toToolResult() = ToolResult(
        id = getLong(getColumnIndexOrThrow("id")),
        toolName = getString(getColumnIndexOrThrow("tool_name")),
        inputHash = getString(getColumnIndexOrThrow("input_hash")),
        outputSummary = getString(getColumnIndexOrThrow("output_summary")),
        ok = getInt(getColumnIndexOrThrow("ok")) == 1,
        timestamp = getLong(getColumnIndexOrThrow("timestamp")),
    )
}
