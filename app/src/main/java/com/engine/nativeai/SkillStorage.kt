package com.engine.nativeai

import org.json.JSONObject
import java.io.File

/** Persists skills as JSON files under app-private storage (never in source). */
class SkillStorage(private val rootDir: File) {

    private fun fileFor(id: String): File = File(rootDir, "$id.json")

    fun save(skill: Skill) {
        rootDir.mkdirs()
        fileFor(skill.id).writeText(skill.toJson().toString())
    }

    fun delete(id: String): Boolean = fileFor(id).delete()

    fun loadAll(): List<Skill> {
        if (!rootDir.exists()) return emptyList()
        return rootDir.listFiles { f -> f.extension == "json" }
            .orEmpty()
            .mapNotNull { f ->
                try {
                    Skill.fromJson(JSONObject(f.readText()))
                } catch (e: Exception) {
                    null
                }
            }
    }
}
