package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPreferencesTest {

    @Test
    fun defaultsAreSane() {
        val p = InMemoryModelPreferences()
        assertNull(p.lastSelectedModelId)
        assertEquals(RoutingMode.HYBRID, p.routingMode)
        assertEquals(PrivacyMode.HYBRID, p.privacyMode)
        assertEquals(ModelCatalog.ZEN_BASE_URL, p.zenBaseUrl)
        assertTrue(p.favorites().isEmpty())
    }

    @Test
    fun lastSelectedAndModePersist() {
        val p = InMemoryModelPreferences()
        p.lastSelectedModelId = "big-pickle"
        p.routingMode = RoutingMode.FREE_ONLY
        p.privacyMode = PrivacyMode.LOCAL_ONLY
        p.zenBaseUrl = "https://example.com/zen/v1"
        assertEquals("big-pickle", p.lastSelectedModelId)
        assertEquals(RoutingMode.FREE_ONLY, p.routingMode)
        assertEquals(PrivacyMode.LOCAL_ONLY, p.privacyMode)
        assertEquals("https://example.com/zen/v1", p.zenBaseUrl)
    }

    @Test
    fun favoritesToggleRoundTrip() {
        val p = InMemoryModelPreferences()
        p.toggleFavorite("big-pickle")
        p.toggleFavorite("deepseek-v4-flash-free")
        assertTrue(p.isFavorite("big-pickle"))
        assertTrue(p.isFavorite("deepseek-v4-flash-free"))
        p.toggleFavorite("big-pickle")
        assertFalse(p.isFavorite("big-pickle"))
        assertEquals(setOf("deepseek-v4-flash-free"), p.favorites())
    }
}
