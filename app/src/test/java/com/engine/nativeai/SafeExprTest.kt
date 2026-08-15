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
    fun scientificNotation() {
        assertEquals(1_000_000_000.0, SafeExpr.evaluate("1e9"), 1e-9)
    }

    @Test
    fun scientificNotationDecimal() {
        assertEquals(250_000_000.0, SafeExpr.evaluate("2.5e8"), 1e-9)
    }

    @Test
    fun scientificNotationSignedExponent() {
        assertEquals(0.25, SafeExpr.evaluate("2.5e-1"), 1e-9)
    }

    @Test
    fun thousandsSeparators() {
        assertEquals(1_000_000.0, SafeExpr.evaluate("1,000,000"), 1e-9)
    }

    @Test
    fun mixedNumberFormats() {
        assertEquals(123_456.0, SafeExpr.evaluate("1,234.56e2"), 1e-9)
    }

    @Test
    fun malformedExponentFailsGracefully() {
        assertThrows(NumberFormatException::class.java) { SafeExpr.evaluate("1e") }
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
