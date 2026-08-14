package com.engine.nativeai

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Dynamic model catalog (spec §4). Providers are registered at runtime; the
 * catalog supports add/remove/rename/update and persists descriptor metadata
 * (never API keys) to a JSON file in app storage.
 */
class ModelRegistry(private val catalogFile: File? = null) {

    private val providers = LinkedHashMap<String, ModelProvider>()
    private val descriptors = LinkedHashMap<String, ModelDescriptor>()

    fun register(provider: ModelProvider) {
        providers[provider.descriptor.id] = provider
        descriptors[provider.descriptor.id] = provider.descriptor
    }

    fun addDescriptor(descriptor: ModelDescriptor) {
        descriptors[descriptor.id] = descriptor
    }

    fun updateDescriptor(id: String, descriptor: ModelDescriptor): Boolean {
        if (!descriptors.containsKey(id)) return false
        descriptors[id] = descriptor
        // Local provider metadata is immutable; remote providers must be
        // re-registered when their endpoint/config changes.
        val live = providers[id]
        if (live != null && live !is LocalModelProvider) providers.remove(id)
        return true
    }

    fun remove(id: String): Boolean {
        providers.remove(id)
        return descriptors.remove(id) != null
    }

    fun get(id: String): ModelDescriptor? = descriptors[id]

    fun provider(id: String): ModelProvider? = providers[id]

    fun providerFor(descriptor: ModelDescriptor?): ModelProvider? = descriptor?.let { providers[it.id] }

    fun list(): List<ModelDescriptor> = descriptors.values.toList()

    fun saveCatalog(): Boolean {
        val file = catalogFile ?: return false
        return try {
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            descriptors.values.forEach { arr.put(it.toJson()) }
            file.writeText(JSONObject().put("models", arr).toString())
            true
        } catch (e: Exception) {
            false
        }
    }

    fun loadCatalog(): Int {
        val file = catalogFile ?: return 0
        return try {
            if (!file.exists()) return 0
            val arr = JSONObject(file.readText()).optJSONArray("models") ?: return 0
            var loaded = 0
            for (i in 0 until arr.length()) {
                val d = ModelDescriptor.fromJson(arr.getJSONObject(i))
                if (descriptors.containsKey(d.id)) {
                    descriptors[d.id] = descriptors[d.id]!!.copy(availability = d.availability)
                } else {
                    descriptors[d.id] = d
                }
                loaded++
            }
            loaded
        } catch (e: Exception) {
            0
        }
    }
}
