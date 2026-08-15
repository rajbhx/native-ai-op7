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

    companion object {
        fun parse(text: String): List<SourceRegistry.SeedSource> {
            val root = JSONObject(text)
            val arr = root.optJSONArray("sources") ?: return emptyList()
            val out = mutableListOf<SourceRegistry.SeedSource>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val type = runCatching { SourceType.valueOf(o.optString("type")) }.getOrNull()
                    ?: continue
                val rawTitle = o.optString("title")
                if (rawTitle.isBlank()) continue
                val title = rawTitle
                out += SourceRegistry.SeedSource(
                    collection = o.optString("collection").ifBlank { "General" },
                    title = title,
                    type = type,
                    contentUrl = o.optString("contentUrl").takeIf { it.isNotBlank() },
                    owner = o.optString("owner").takeIf { it.isNotBlank() },
                    repo = o.optString("repo").takeIf { it.isNotBlank() },
                    updateAfterHours = o.optLong("updateAfterHours", 24).coerceIn(1, 24 * 7),
                )
            }
            return out
        }
    }
}
