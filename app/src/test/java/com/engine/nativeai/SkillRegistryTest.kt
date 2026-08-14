package com.engine.nativeai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SkillRegistryTest {
    @Test
    fun registerAndLookup() {
        val reg = SkillRegistry()
        reg.register(Skill(id = "research", purpose = "find facts", tools = listOf("web_search")))
        assertNotNull(reg.get("research"))
        assertEquals("find facts", reg.get("research")?.purpose)
        assertNull(reg.get("missing"))
    }

    @Test
    fun removeWorks() {
        val reg = SkillRegistry()
        reg.register(Skill(id = "coding", purpose = "code"))
        assertEquals(true, reg.remove("coding"))
        assertEquals(false, reg.remove("coding"))
    }

    @Test
    fun jsonRoundTrip() {
        val skill = Skill(
            id = "android",
            purpose = "android guidance",
            tools = listOf("memory_search", "calculator"),
            workflow = listOf("recall", "reason", "answer"),
            constraints = "offline only",
            verificationRules = listOf("check memory"),
        )
        val back = Skill.fromJson(JSONObject(skill.toJson().toString()))
        assertEquals(skill, back)
    }
}
