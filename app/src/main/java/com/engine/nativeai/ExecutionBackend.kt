package com.engine.nativeai

/**
 * Replaceable execution layer (master prompt: Agent -> Tool -> backend).
 * The agent never depends on a concrete backend (Termux or otherwise);
 * each backend is bounded: hard timeout, cancellation, structured result.
 */
interface ExecutionBackend {
    val available: Boolean
    suspend fun execute(request: ExecutionRequest): ExecutionResult

    /** Release managed processes/resources. Default no-op for stateless backends. */
    fun shutdown() {}
}

/** Bounded process request (Termux ExecutionCommand concept, clean-room). */
data class ExecutionRequest(
    val command: String,
    val workingDirectory: String? = null,
    val environment: Map<String, String> = emptyMap(),
    val timeoutMs: Long = 10_000,
    val stdin: String = "",
)

/** Structured process result; never parses UI text. */
data class ExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val timedOut: Boolean = false,
    val cancelled: Boolean = false,
)
