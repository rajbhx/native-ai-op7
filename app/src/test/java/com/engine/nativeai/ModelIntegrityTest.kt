package com.engine.nativeai

import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelIntegrityTest {

    private val knownSha256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test
    fun sha256MatchesKnownVector() = runTest {
        val f = File.createTempFile("integrity", ".bin")
        try {
            f.writeBytes("abc".toByteArray())
            assertEquals(knownSha256, ModelIntegrity.sha256(f))
        } finally {
            f.delete()
        }
    }

    @Test
    fun matchesIsCaseInsensitive() = runTest {
        val f = File.createTempFile("integrity", ".bin")
        try {
            f.writeBytes("abc".toByteArray())
            assertTrue(ModelIntegrity.matches(f, knownSha256.uppercase()))
            assertFalse(ModelIntegrity.matches(f, "deadbeef"))
            assertFalse("blank expected must fail closed", ModelIntegrity.matches(f, ""))
        } finally {
            f.delete()
        }
    }

    @Test
    fun missingFileReturnsNullDigest() = runTest {
        assertNull(ModelIntegrity.sha256(File("/definitely/not/here.gguf")))
        assertFalse(ModelIntegrity.matches(File("/definitely/not/here.gguf"), knownSha256))
    }
}
