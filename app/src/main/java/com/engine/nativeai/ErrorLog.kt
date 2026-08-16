package com.engine.nativeai

/** One structured error entry (core error log). */
data class ErrorEntry(
    val id: Long,
    val atMs: Long,
    val source: String,
    val message: String,
    val detail: String? = null,
)

/**
 * Core, bounded, thread-safe error log backing the ERRORS tab. Handled
 * errors and uncaught crashes are recorded here so every failure is
 * visible in-app instead of silently lost. Never throws; never grows
 * unbounded. Persisting to disk is deliberately out of scope — this is a
 * live debugging surface, not a substitute for crash reporting.
 */
class ErrorLog(private val maxEntries: Int = 100) {

    private val entries = ArrayDeque<ErrorEntry>()
    private var nextId = 1L

    @Synchronized
    fun record(source: String, message: String, detail: String? = null): ErrorEntry {
        val entry = ErrorEntry(nextId++, System.currentTimeMillis(), source, message, detail?.take(2000))
        entries.addLast(entry)
        while (entries.size > maxEntries) entries.removeFirst()
        return entry
    }

    @Synchronized
    fun record(source: String, message: String, throwable: Throwable?): ErrorEntry =
        record(source, message, throwable?.stackTraceToString()?.take(2000))

    @Synchronized
    fun all(): List<ErrorEntry> = entries.toList()

    @Synchronized
    fun count(): Int = entries.size

    @Synchronized
    fun clear() {
        entries.clear()
    }
}

/**
 * Process-wide core error log. The UI, engine paths and the crash hook all
 * write here; the ERRORS tab reads it. The crash hook only records and then
 * delegates to the previous handler — behavior is unchanged, failures become
 * visible.
 */
object CoreErrors {
    val log = ErrorLog()

    fun installCrashHook() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            log.record(
                "crash",
                throwable.message ?: throwable.javaClass.simpleName,
                throwable,
            )
            previous?.uncaughtException(thread, throwable)
        }
    }
}
