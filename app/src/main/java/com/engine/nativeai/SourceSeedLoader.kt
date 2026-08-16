package com.engine.nativeai

import android.content.Context
import org.json.JSONObject

/** Loads the default source catalog from assets (uBO assets.json pattern). */
class SourceSeedLoader(private val context: Context) {

    fun load(): List<SourceRegistry.SeedSource> = try {
        val text = context.assets.open("sources.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        parse(text)
    } catch (e: Exception) {
        emptyList() // missing/broken seed asset must never crash the app
    }

    /** Catalog version read from the same asset (drives one-time seed pruning). */
    fun catalogVersion(): Int = try {
        val text = context.assets.open("sources.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        parseVersion(text)
    } catch (e: Exception) {
        1 // unreadable asset: behave like the pre-versioning catalog
    }

    companion object {
        /** Missing/blank version defaults to 1 (the first shipped catalog). */
        fun parseVersion(text: String): Int = try {
            JSONObject(text).optInt("version", 1).coerceAtLeast(1)
        } catch (e: Exception) {
            1
        }

        /** Malformed/blank input must never throw: empty catalog, honest state. */
        fun parse(text: String): List<SourceRegistry.SeedSource> {
            return try {
                val root = JSONObject(text)
                val arr = root.optJSONArray("sources") ?: return emptyList()
                val out = mutableListOf<SourceRegistry.SeedSource>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrNull()
                        ?: continue
                    val rawTitle = o.optString("title")
                    if (rawTitle.isBlank()) continue
                    out += SourceRegistry.SeedSource(
                        collection = o.optString("collection").ifBlank { "General" },
                        title = rawTitle,
                        type = type,
                        contentUrl = o.optString("contentUrl").takeIf { it.isNotBlank() },
                        owner = o.optString("owner").takeIf { it.isNotBlank() },
                        repo = o.optString("repo").takeIf { it.isNotBlank() },
                        updateAfterHours = o.optLong("updateAfterHours", 24).coerceIn(1, 24 * 7),
                    )
                }
                out
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
