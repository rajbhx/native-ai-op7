package com.engine.nativeai

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

/**
 * Single resolution point for user-facing storage (golden: the UI, importer,
 * model library, memory DB and the background service must never compute
 * paths themselves). Defaults stay inside app-private storage; an explicit
 * override (SAF tree on primary external storage, app-scoped) redirects
 * everything consistently — one knob, one truth.
 *
 * Scoped-storage honesty (Android 10+, targetSdk 34): arbitrary user folders
 * are NOT writable via raw File paths. Overrides are therefore accepted only
 * inside the app's own external directory (Android/data/<pkg>/...); a pick
 * outside that scope is rejected with an explicit message instead of a fake
 * capability.
 */
object StoragePaths {

    /** Data directory (memory DB, vectors, skills, training). */
    fun dataDir(context: Context, prefs: ModelPreferencesStore): File =
        writableDir(context, prefs.dataDirOverride) ?: context.filesDir

    /** Models directory (GGUF files + manifest + catalog). */
    fun modelsDir(context: Context, prefs: ModelPreferencesStore): File {
        val dir = writableDir(context, prefs.modelsDirOverride)
            ?: File(context.filesDir, "models")
        dir.mkdirs()
        return dir
    }

    fun catalogFile(context: Context, prefs: ModelPreferencesStore): File =
        File(modelsDir(context, prefs), "catalog.json")

    /** Absolute path for the memory DB (defaults to SQLite's app-private
     *  databases dir when no override is set). */
    fun memoryDbPath(context: Context, prefs: ModelPreferencesStore?): String =
        prefs?.let { File(dataDir(context, it), "memory.db").absolutePath } ?: "memory.db"

    /** App-scoped external storage root (Android/data/<pkg>). */
    fun externalRoot(context: Context): File? =
        context.getExternalFilesDir(null)?.parentFile

    /** Resolves a SAF tree URI to an absolute path we may actually write.
     *  Only primary external storage inside our app-scoped root is accepted;
     *  anything else returns null (caller shows the honest message). */
    fun treeToAppPath(context: Context, uri: Uri): String? = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(uri)
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2 || parts[0] != "primary") return@runCatching null
        val candidate = File(Environment.getExternalStorageDirectory(), parts[1]).absoluteFile
        val root = externalRoot(context) ?: return@runCatching null
        if (candidate.path.startsWith(root.path + File.separator)) candidate.absolutePath else null
    }.getOrNull()

    fun label(context: Context, prefs: ModelPreferencesStore, dir: File?): String {
        val internal = context.filesDir.absolutePath
        val external = externalRoot(context)?.absolutePath
        return when {
            dir == null -> "app-private internal storage (default)"
            dir.absolutePath == internal -> "app-private internal storage (default)"
            dir.absolutePath == external -> "external app storage (default external)"
            external != null && dir.absolutePath.startsWith(external + File.separator) ->
                "external app storage \u00b7 ${dir.absolutePath.removePrefix(external + File.separator)}"
            else -> dir.absolutePath
        }
    }

    private fun writableDir(context: Context, override: String?): File? {
        if (override.isNullOrBlank()) return null
        val f = File(override)
        // Never accept a path outside our own writable roots.
        val allowed = f.absolutePath == context.filesDir.absolutePath ||
            f.absolutePath == context.filesDir.absolutePath + File.separator ||
            f.absolutePath.startsWith(context.filesDir.absolutePath + File.separator) ||
            externalRoot(context)?.let { root ->
                f.absolutePath == root.absolutePath ||
                    f.absolutePath.startsWith(root.absolutePath + File.separator)
            } == true
        if (!allowed) return null
        return if (f.isDirectory || f.mkdirs()) f else null
    }
}
