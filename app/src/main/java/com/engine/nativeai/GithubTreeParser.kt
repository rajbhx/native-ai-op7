package com.engine.nativeai

import org.json.JSONArray
import org.json.JSONObject

/** Pure parsing of GitHub API responses; JVM-testable (no Android deps). */
data class GithubTreeEntry(
    val path: String,
    val sha: String,
    val size: Long,
)

object GithubTreeParser {

    /** Latest commit sha from GET /repos/{o}/{r}/commits?per_page=1. */
    fun latestCommitSha(json: String): String? {
        val arr = JSONArray(json)
        return if (arr.length() > 0) arr.getJSONObject(0).optString("sha").takeIf { it.isNotBlank() }
        else null
    }

    /** Parse git/trees/{sha}?recursive=1; only blobs survive, bounded. */
    fun parseTree(json: String): List<GithubTreeEntry> {
        val obj = JSONObject(json)
        val tree = obj.optJSONArray("tree") ?: return emptyList()
        val out = mutableListOf<GithubTreeEntry>()
        for (i in 0 until tree.length()) {
            val e = tree.getJSONObject(i)
            if (e.optString("type") != "blob") continue
            out += GithubTreeEntry(
                path = e.optString("path"),
                sha = e.optString("sha"),
                size = e.optLong("size"),
            )
        }
        return out
    }

    /** uBO changed-blob-only policy: paths whose blob sha changed or are new. */
    fun changedPaths(tree: List<GithubTreeEntry>, existing: Map<String, String>): List<GithubTreeEntry> =
        tree.filter { existing[it.path] != it.sha }

    /** Paths tracked locally but gone upstream — must be dropped. */
    fun removedPaths(tree: List<GithubTreeEntry>, existing: Map<String, String>): List<String> =
        existing.keys.filter { path -> tree.none { it.path == path } }
}
