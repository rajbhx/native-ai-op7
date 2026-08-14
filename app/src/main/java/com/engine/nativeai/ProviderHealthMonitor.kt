package com.engine.nativeai

import java.util.concurrent.ConcurrentHashMap

/**
 * Per-provider failure tracking (spec §13). Providers with repeated failures
 * are marked unhealthy and temporarily avoided; successes clear the state.
 */
class ProviderHealthMonitor(
    private val failureThreshold: Int = 2,
    private val cooldownMs: Long = 60_000,
) {
    private data class Status(
        var consecutiveFailures: Int = 0,
        var lastFailureAt: Long = 0,
        var lastError: String = "",
    )

    private val statuses = ConcurrentHashMap<String, Status>()

    fun reportSuccess(providerId: String) {
        statuses.remove(providerId)
    }

    fun reportFailure(providerId: String, error: String = "") {
        val s = statuses.getOrPut(providerId) { Status() }
        s.consecutiveFailures++
        s.lastFailureAt = System.currentTimeMillis()
        s.lastError = error.take(200)
    }

    fun isHealthy(providerId: String): Boolean {
        val s = statuses[providerId] ?: return true
        if (s.consecutiveFailures < failureThreshold) return true
        return System.currentTimeMillis() - s.lastFailureAt > cooldownMs
    }

    fun lastError(providerId: String): String = statuses[providerId]?.lastError ?: ""
}
