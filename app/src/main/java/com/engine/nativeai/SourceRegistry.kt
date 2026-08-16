package com.engine.nativeai

/**
 * uBO-style source registry (clean-room ideas only, GPL-3.0 upstream not
 * copied): a versioned, refreshable catalog of knowledge sources. Each
 * source carries its own update cadence (updateAfterHours); staleness and
 * read-time LRU eviction keep the local index battery- and storage-friendly.
 */
class SourceRegistry(private val db: SourceStore) {

    /** Seed catalog entry (assets/sources.json, same shape as uBO assets.json). */
    data class SeedSource(
        val collection: String,
        val title: String,
        val type: SourceType,
        val contentUrl: String? = null,
        val owner: String? = null,
        val repo: String? = null,
        val updateAfterHours: Long = 24,
    )

    /** Idempotent seeding: only missing titles are added (first-run only). */
    fun seed(entries: List<SeedSource>, catalogVersion: Int = 1): Int {
        pruneLegacySeeds(catalogVersion)
        var added = 0
        entries.forEach { e ->
            if (db.sourceByTitle(e.title) == null) {
                val colId = db.upsertSourceCollection(e.collection)
                db.saveSource(
                    Source(
                        id = 0,
                        collectionId = colId,
                        title = e.title,
                        type = e.type,
                        contentUrl = e.contentUrl,
                        owner = e.owner,
                        repo = e.repo,
                        updateAfterHours = e.updateAfterHours,
                    ),
                )
                added++
            }
        }
        return added
    }

    /**
     * One-time upgrade cleanup: seed rows from an older catalog that are no
     * longer in the catalog are removed so every install converges on the
     * current seed set (FMHY + Playbook). Runs only when the stored catalog
     * version is behind the shipped one; user-added rows are never touched.
     * The legacy title list is intentionally one-release-only and is deleted
     * with the next catalog bump.
     */
    private fun pruneLegacySeeds(catalogVersion: Int) {
        val stored = db.metaGet(KEY_CATALOG_VERSION)?.toIntOrNull() ?: 0
        // The legacy list is the v1 -> v2 diff; only valid when upgrading to a
        // catalog that actually dropped those seeds.
        if (catalogVersion < MIN_PRUNE_VERSION || stored >= catalogVersion) return
        LEGACY_SEED_TITLES.forEach { title ->
            db.sourceByTitle(title)?.let { db.deleteSource(it.id) }
        }
        db.metaSet(KEY_CATALOG_VERSION, catalogVersion.toString())
    }

    private companion object {
        const val KEY_CATALOG_VERSION = "source_catalog_version"
        const val MIN_PRUNE_VERSION = 2
        val LEGACY_SEED_TITLES = setOf("Termux", "llama.cpp", "uBlock Origin", "MemPalace", "LiteRT")
    }

    fun addSource(s: Source): Long = db.saveSource(s)
    fun removeSource(id: Long) = db.deleteSource(id)
    fun sources(): List<Source> = db.sources()
    fun collections(): List<SourceCollection> = db.collections()
    fun touchRead(id: Long) = db.touchSourceRead(id)

    /** Due sources sorted most-obsolete-first (uBO gentle-updater order). */
    fun updateCandidates(now: Long = System.currentTimeMillis()): List<Source> =
        sources()
            .filter { it.stalenessMs(now) > 0 }
            .sortedByDescending { it.stalenessMs(now) }

    fun evict(keep: Int = SourceCapabilities.EVICT_KEEP): Int = db.evictSources(keep)

    fun markError(id: Long, message: String) = db.markSourceError(id, message)
}
