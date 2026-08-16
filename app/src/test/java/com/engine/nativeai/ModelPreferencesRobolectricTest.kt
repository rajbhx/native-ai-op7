package com.engine.nativeai

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Context-bound persistence tests (golden catalog: Robolectric). SharedPrefs
 * behavior that the in-memory store cannot cover: actual apply() round-trips,
 * including the null system-prompt removal path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class ModelPreferencesRobolectricTest {

    @Test
    fun selectionAndOverrideRoundTrip() {
        val prefs = ModelPreferences(ApplicationProvider.getApplicationContext())
        prefs.lastSelectedModelId = "nemotron-3.5-lightning-free"
        prefs.systemPromptOverride = "You are a strict OnePlus 7 systems engineer."
        prefs.terminalEnabled = true

        // Fresh instance over the same file reads back what apply() wrote.
        val fresh = ModelPreferences(ApplicationProvider.getApplicationContext())
        assertEquals("nemotron-3.5-lightning-free", fresh.lastSelectedModelId)
        assertEquals("You are a strict OnePlus 7 systems engineer.", fresh.systemPromptOverride)
        assertTrue(fresh.terminalEnabled)

        // Blank clears the override (null semantics, not an empty string).
        fresh.systemPromptOverride = null
        assertNull(fresh.systemPromptOverride)
    }

    @Test
    fun favoritesPersistAndToggle() {
        val prefs = ModelPreferences(ApplicationProvider.getApplicationContext())
        prefs.toggleFavorite("local-llama")
        assertTrue(ModelPreferences(ApplicationProvider.getApplicationContext()).isFavorite("local-llama"))
        prefs.toggleFavorite("local-llama")
        assertTrue(ModelPreferences(ApplicationProvider.getApplicationContext()).favorites().isEmpty())
    }
}
