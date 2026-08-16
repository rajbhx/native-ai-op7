package com.engine.nativeai

import android.content.Context
import android.content.SharedPreferences

/**
 * Persisted model-selection state (spec: selection persistence). Backed by
 * SharedPreferences; abstracted behind an interface so JVM tests can use an
 * in-memory store.
 */
interface ModelPreferencesStore {
    var lastSelectedModelId: String?
    var routingMode: RoutingMode
    var privacyMode: PrivacyMode
    var zenBaseUrl: String
    var terminalEnabled: Boolean
    var terminalAllowlist: Set<String>
    var toolAlwaysAllow: Set<String>
    var firstRunDismissed: Boolean
    var systemPromptOverride: String?
    /** Absolute app-writable directory for the memory DB / vectors / skills.
     *  null = app-private internal storage (filesDir). */
    var dataDirOverride: String?
    /** Absolute app-writable directory for GGUF models. null = filesDir/models. */
    var modelsDirOverride: String?
    fun favorites(): Set<String>
    fun isFavorite(id: String): Boolean
    fun toggleFavorite(id: String): Set<String>
}

/** In-memory store for JVM tests (no Android dependency). */
class InMemoryModelPreferences : ModelPreferencesStore {
    override var lastSelectedModelId: String? = null
    override var routingMode: RoutingMode = RoutingMode.HYBRID
    override var privacyMode: PrivacyMode = PrivacyMode.HYBRID
    override var zenBaseUrl: String = ModelCatalog.ZEN_BASE_URL
    override var terminalEnabled: Boolean = false
    override var terminalAllowlist: Set<String> = emptySet()
    override var toolAlwaysAllow: Set<String> = emptySet()
    override var firstRunDismissed: Boolean = false
    override var systemPromptOverride: String? = null
    override var dataDirOverride: String? = null
    override var modelsDirOverride: String? = null
    private val favs = mutableSetOf<String>()
    override fun favorites(): Set<String> = favs.toSet()
    override fun isFavorite(id: String): Boolean = id in favs
    override fun toggleFavorite(id: String): Set<String> {
        if (!favs.add(id)) favs.remove(id)
        return favs.toSet()
    }
}

class ModelPreferences(context: Context) : ModelPreferencesStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("model_catalog", Context.MODE_PRIVATE)

    override var lastSelectedModelId: String?
        get() = prefs.getString("last_selected", null)
        set(value) {
            prefs.edit().putString("last_selected", value).apply()
        }

    override var routingMode: RoutingMode
        get() = runCatching {
            RoutingMode.valueOf(prefs.getString("routing_mode", RoutingMode.HYBRID.name)!!)
        }.getOrDefault(RoutingMode.HYBRID)
        set(value) {
            prefs.edit().putString("routing_mode", value.name).apply()
        }

    override var privacyMode: PrivacyMode
        get() = runCatching {
            PrivacyMode.valueOf(prefs.getString("privacy_mode", PrivacyMode.HYBRID.name)!!)
        }.getOrDefault(PrivacyMode.HYBRID)
        set(value) {
            prefs.edit().putString("privacy_mode", value.name).apply()
        }

    override var zenBaseUrl: String
        get() = prefs.getString("zen_base_url", ModelCatalog.ZEN_BASE_URL)
            ?: ModelCatalog.ZEN_BASE_URL
        set(value) {
            prefs.edit().putString("zen_base_url", value).apply()
        }

    override var terminalEnabled: Boolean
        get() = prefs.getBoolean("terminal_enabled", false)
        set(value) {
            prefs.edit().putBoolean("terminal_enabled", value).apply()
        }

    override var terminalAllowlist: Set<String>
        get() = prefs.getStringSet("terminal_allowlist", emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("terminal_allowlist", value).apply()
        }

    override var toolAlwaysAllow: Set<String>
        get() = prefs.getStringSet("tool_always_allow", emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet("tool_always_allow", value).apply()
        }

    override var firstRunDismissed: Boolean
        get() = prefs.getBoolean("first_run_dismissed", false)
        set(value) {
            prefs.edit().putBoolean("first_run_dismissed", value).apply()
        }

    override var systemPromptOverride: String?
        get() = prefs.getString("system_prompt_override", null)
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove("system_prompt_override").apply()
            } else {
                prefs.edit().putString("system_prompt_override", value).apply()
            }
        }

    override var dataDirOverride: String?
        get() = prefs.getString("data_dir_override", null)
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove("data_dir_override").apply()
            } else {
                prefs.edit().putString("data_dir_override", value).apply()
            }
        }

    override var modelsDirOverride: String?
        get() = prefs.getString("models_dir_override", null)
        set(value) {
            if (value.isNullOrBlank()) {
                prefs.edit().remove("models_dir_override").apply()
            } else {
                prefs.edit().putString("models_dir_override", value).apply()
            }
        }

    override fun favorites(): Set<String> =
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()

    override fun isFavorite(id: String): Boolean = id in favorites()

    override fun toggleFavorite(id: String): Set<String> {
        val current = favorites().toMutableSet()
        if (!current.add(id)) current.remove(id)
        prefs.edit().putStringSet("favorites", current).apply()
        return current
    }
}
