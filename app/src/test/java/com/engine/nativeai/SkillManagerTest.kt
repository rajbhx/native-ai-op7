package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Skill lifecycle tests (Phase 10). Pure JVM: SkillManager persists to a
 * File dir, so no Android Context is required.
 */
class SkillManagerTest {

    private fun manager(dir: File): SkillManager = SkillManager().apply { init(dir) }

    @Test
    fun builtinSeedsCannotBeDeleted() {
        val dir = Files.createTempDirectory("skills-test").toFile()
        val mgr = manager(dir)
        assertTrue(mgr.list().isNotEmpty())
        val seeded = mgr.list().first()
        assertTrue(seeded.builtin)
        assertFalse(mgr.delete(seeded.id))
        assertTrue(mgr.list().any { it.id == seeded.id })
    }

    @Test
    fun customSkillRoundTripsThroughStorageAndDeletes() {
        val dir = Files.createTempDirectory("skills-test").toFile()
        val custom = Skill("my-skill", "custom purpose", workflow = listOf("step 1", "step 2"))
        manager(dir).register(custom)

        // Fresh manager over the same dir reloads the persisted skill.
        val reloaded = manager(dir).get("my-skill")
        assertEquals("custom purpose", reloaded?.purpose)
        assertEquals(listOf("step 1", "step 2"), reloaded?.workflow)
        assertFalse(reloaded!!.builtin)

        assertTrue(manager(dir).delete("my-skill"))
        assertNull(manager(dir).get("my-skill"))
    }

    @Test
    fun registerOverwritesExistingSkill() {
        val dir = Files.createTempDirectory("skills-test").toFile()
        val mgr = manager(dir)
        mgr.register(Skill("a", "v1"))
        mgr.register(Skill("a", "v2"))
        assertEquals("v2", mgr.get("a")?.purpose)
    }
}
