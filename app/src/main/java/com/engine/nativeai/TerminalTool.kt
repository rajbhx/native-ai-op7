package com.engine.nativeai

/**
 * First-class agent tool for the execution layer. Disabled by default; the
 * model can never bypass the permission boundary or the command policy.
 */
class TerminalTool(
    private val backend: ExecutionBackend,
    private val policy: ExecutionPolicy = ExecutionPolicy(),
    @Volatile private var enabledFlag: Boolean = false,
) : AgentTool {

    override val enabled: Boolean get() = enabledFlag

    fun setEnabled(value: Boolean) {
        enabledFlag = value
    }

    override val name = "terminal"
    override val description =
        "Run one shell command on this device, return exit code + output."
    override val permission = ToolPermission.REQUIRES_APPROVAL
    override val available: Boolean get() = enabledFlag && backend.available
    override val unavailableReason: String?
        get() = when {
            !enabledFlag -> "terminal disabled"
            !backend.available -> "backend unavailable (${backendLabel ?: "?"})"
            else -> null
        }
    override val backendLabel: String?
        get() = if (backend is TermuxBackend) "termux" else "local"

    override suspend fun execute(input: String): ToolOutput {
        if (!available) {
            return ToolOutput(name, "", false, "terminal unavailable (disabled)")
        }
        val command = input.trim()
        if (!policy.allows(command)) {
            return ToolOutput(name, "", false, "execution policy denied command: '${command.take(120)}'")
        }
        val result = backend.execute(ExecutionRequest(command = command, timeoutMs = 15_000))
        val text = buildString {
            append("[${backendLabel ?: "?"}] exit=").append(result.exitCode)
            if (result.timedOut) append(" [timed out]")
            if (result.cancelled) append(" [cancelled]")
            append("\n")
            append(result.stdout)
            if (result.stderr.isNotBlank()) {
                append("\n[stderr]\n")
                append(result.stderr)
            }
        }
        return ToolOutput(name, text, result.exitCode == 0)
    }
}
