package com.engine.nativeai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifierTest {
    @Test
    fun toolVerifiedWithOutput() {
        val r = Verifier.verifyTool("42", true, "calculator")
        assertTrue(r.passed)
        assertTrue(r.evidence.isNotBlank())
    }

    @Test
    fun toolNotVerifiedWhenFailed() {
        assertFalse(Verifier.verifyTool("", false, "web_search").passed)
        assertFalse(Verifier.verifyTool("output", false, "web_search").passed)
    }

    @Test
    fun blankOutputNotVerified() {
        assertFalse(Verifier.verifyTool("   ", true, "tool").passed)
    }

    @Test
    fun memoryClaimVerifiedAgainstContext() {
        assertTrue(Verifier.verifyMemoryClaim("Known facts:\n- sky is blue", "sky is blue").passed)
        assertFalse(Verifier.verifyMemoryClaim("Known facts:\n- sky is blue", "grass is green").passed)
        assertFalse(Verifier.verifyMemoryClaim("", "sky is blue").passed)
    }
}
