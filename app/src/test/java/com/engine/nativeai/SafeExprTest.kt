package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SafeExprTest {
    @Test
    fun addition() {
        assertEquals(7.0, SafeExpr.evaluate("2 + 5"), 1e-9)
    }

    @Test
    fun precedence() {
        assertEquals(14.0, SafeExpr.evaluate("2 + 3 * 4"), 1e-9)
    }

    @Test
    fun parentheses() {
        assertEquals(20.0, SafeExpr.evaluate("(2 + 3) * 4"), 1e-9)
    }

    @Test
    fun power() {
        assertEquals(8.0, SafeExpr.evaluate("2 ^ 3"), 1e-9)
    }

    @Test
    fun unaryMinus() {
        assertEquals(-5.0, SafeExpr.evaluate("-5"), 1e-9)
    }

    @Test
    fun decimals() {
        assertEquals(2.5, SafeExpr.evaluate("5 / 2"), 1e-9)
    }

    @Test
    fun divisionByZeroThrows() {
        assertThrows(IllegalArgumentException::class.java) { SafeExpr.evaluate("1 / 0") }
    }

    @Test
    fun unsupportedCharacterThrows() {
        assertThrows(IllegalArgumentException::class.java) { SafeExpr.evaluate("2 + x") }
    }

    @Test
    fun emptyThrows() {
        assertThrows(IllegalArgumentException::class.java) { SafeExpr.evaluate("   ") }
    }
}
