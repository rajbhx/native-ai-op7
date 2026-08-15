package com.engine.nativeai

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local short-lived process execution. No daemons, no persistent sessions;
 * every call spawns one process with a hard timeout and explicit teardown
 * on timeout/cancel. Safe default cwd = app-private directory.
 */
class LocalProcessBackend(
    private val shell: String = defaultShell(),
    private val defaultWorkingDirectory: File? = null,
) : ExecutionBackend {

    override val available: Boolean = true

    companion object {
        fun defaultShell(): String =
            if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
    }

    override suspend fun execute(request: ExecutionRequest): ExecutionResult =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val process = try {
                val pb = ProcessBuilder(shell, "-c", request.command)
                request.workingDirectory?.let { pb.directory(File(it)) }
                    ?: defaultWorkingDirectory?.let { pb.directory(it) }
                request.environment.forEach { (k, v) -> pb.environment()[k] = v }
                pb.start()
            } catch (e: Exception) {
                return@withContext ExecutionResult(
                    127, "", e.message ?: "process start failed", 0,
                )
            }
            try {
                val out = StringBuilder()
                val err = StringBuilder()
                val drainOut = Thread {
                    out.append(process.inputStream.readBytes().toString(Charsets.UTF_8))
                }.apply { isDaemon = true }
                val drainErr = Thread {
                    err.append(process.errorStream.readBytes().toString(Charsets.UTF_8))
                }.apply { isDaemon = true }
                drainOut.start()
                drainErr.start()
                if (request.stdin.isNotBlank()) {
                    process.outputStream.use { it.write(request.stdin.toByteArray()) }
                } else {
                    process.outputStream.close()
                }
                val finished = process.waitFor(request.timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroy()
                    process.waitFor(2, TimeUnit.SECONDS)
                    process.destroyForcibly()
                    return@withContext ExecutionResult(
                        -1, out.toString(), err.toString(),
                        System.currentTimeMillis() - started, timedOut = true,
                    )
                }
                drainOut.join(1000)
                drainErr.join(1000)
                ExecutionResult(
                    exitCode = process.exitValue(),
                    stdout = out.toString(),
                    stderr = err.toString(),
                    durationMs = System.currentTimeMillis() - started,
                )
            } catch (e: CancellationException) {
                process.destroyForcibly()
                throw e
            } finally {
                process.destroy()
            }
        }
}
