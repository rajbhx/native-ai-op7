package com.engine.nativeai

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Searches only the app's private storage (spec §22: no arbitrary files). */
class FileSearchTool(private val rootDir: File, private val maxDepth: Int = 3) : AgentTool {
    override val name = "file_search"
    override val description =
        "Find a file by name inside the app's private storage. Input: a filename or fragment."

    override val permission: ToolPermission
        get() = ToolPermission.READ_ONLY

    override suspend fun execute(input: String): ToolOutput = withContext(Dispatchers.IO) {
        val query = input.trim().lowercase()
        if (query.isEmpty()) {
            return@withContext ToolOutput(name, "", false, "empty query")
        }
        val hits = mutableListOf<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > maxDepth || hits.size >= 10) return
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) walk(f, depth + 1)
                else if (f.name.lowercase().contains(query)) hits.add(f)
            }
        }
        walk(rootDir, 0)
        if (hits.isEmpty()) {
            ToolOutput(name, "no files match '$query' under ${rootDir.absolutePath}", false, null)
        } else {
            ToolOutput(
                name,
                hits.joinToString("\n") { "${it.absolutePath} (${it.length()} bytes)" },
                true,
            )
        }
    }
}
