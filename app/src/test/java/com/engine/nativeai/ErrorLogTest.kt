package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorLogTest {

    @Test
    fun recordsInOrder() {
        val log = ErrorLog()
        log.record("agent", "first")
        log.record("generate", "second")
        assertEquals(listOf("first", "second"), log.all().map { it.message })
        assertEquals(2, log.count())
    }

    @Test
    fun boundedCapDropsOldest() {
        val log = ErrorLog(maxEntries = 3)
        repeat(5) { log.record("source", "e$it") }
        assertEquals(3, log.count())
        assertEquals(listOf("e2", "e3", "e4"), log.all().map { it.message })
    }

    @Test
    fun clearEmpties() {
        val log = ErrorLog()
        log.record("agent", "x")
        log.clear()
        assertTrue(log.all().isEmpty())
        assertEquals(0, log.count())
    }

    @Test
    fun throwableDetailCapturedAndTruncated() {
        val log = ErrorLog()
        val boom = IllegalStateException("kaboom")
        log.record("diagnostics", "probe failed", boom)
        val e = log.all().single()
        assertEquals("probe failed", e.message)
        assertTrue(e.detail.orEmpty().contains("kaboom"))
        assertTrue(e.detail.orEmpty().contains("IllegalStateException"))
    }

    @Test
    fun nullThrowableStoresNullDetail() {
        val log = ErrorLog()
        log.record("crash", "unknown", null as Throwable?)
        assertNull(log.all().single().detail)
    }
}
