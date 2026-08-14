package com.engine.nativeai

/**
 * Privacy gate for remote requests (spec §16-17). Only the minimal context
 * built for the current task may leave the device; anything that looks like a
 * secret/credential is dropped, and memory is never shipped wholesale.
 */
object MemoryPrivacyFilter {

    private val sensitiveMarkers = listOf(
        "api_key", "apikey", "api-key", "password", "passwd", "secret", "token", "bearer",
        "private key", "authorization", "credential",
    )

    fun forRemote(context: String): String {
        if (context.isBlank()) return context
        return context.lines()
            .filterNot { line ->
                val l = line.lowercase()
                sensitiveMarkers.any { l.contains(it) }
            }
            .joinToString("\n")
            .trim()
    }
}
