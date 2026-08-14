package com.engine.nativeai

/** In-memory skill registry (master prompt §11). */
class SkillRegistry {
    private val skills = LinkedHashMap<String, Skill>()

    fun register(skill: Skill) {
        skills[skill.id] = skill
    }

    fun get(id: String): Skill? = skills[id]

    fun remove(id: String): Boolean = skills.remove(id) != null

    fun list(): List<Skill> = skills.values.toList()

    fun descriptions(): String =
        skills.values.joinToString("\n") { "- ${it.id}: ${it.purpose}" }
}
