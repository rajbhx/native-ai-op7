package com.engine.nativeai

import java.io.File

/**
 * Loads persisted skills on start, seeds defaults, and keeps registry +
 * storage in sync (master prompt §11).
 */
class SkillManager(private val registry: SkillRegistry = SkillRegistry()) {

    private var storage: SkillStorage? = null

    fun init(storageDir: File?) {
        storage = storageDir?.let { SkillStorage(File(it, "skills")) }
        storage?.let { store ->
            store.loadAll().forEach { registry.register(it) }
        }
        if (registry.list().isEmpty()) {
            DefaultSkills.seeds().forEach { register(it) }
        }
    }

    fun register(skill: Skill) {
        registry.register(skill)
        storage?.save(skill)
    }

    fun list(): List<Skill> = registry.list()

    fun get(id: String): Skill? = registry.get(id)

    fun descriptions(): String = registry.descriptions()

    /** Delete a user skill (registry + storage). Built-ins are read-only:
     *  they re-seed when the list is empty and are never removed. */
    fun delete(id: String): Boolean {
        val skill = registry.get(id) ?: return false
        if (skill.builtin) return false
        registry.remove(id)
        return storage?.delete(id) ?: true
    }
}
