package com.engine.nativeai

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Honest Termux capability state (Track E). Never fabricates availability. */
enum class TermuxStatus {
    NOT_INSTALLED,
    INSTALLED,
    SETUP_REQUIRED,
    READY,
    ERROR,
}

/**
 * Small seam so JVM tests can drive [TermuxBackend] without Android.
 * The real bridge launches Termux's public RunCommandService intent
 * (clean-room per the execution audit; no Termux code is copied).
 */
interface TermuxBridge {
    fun isInstalled(): Boolean

    /** Fire the run-command intent; false = rejected (toggle/permission). */
    fun launch(runId: String, wrappedCommand: String, workingDirectory: String?): Boolean

    /** Directory where Termux writes out.txt / err.txt / code.txt. */
    fun resultDir(runId: String): File
}

/** Real Android bridge: package probe + RunCommandService intent + shared-storage dir. */
class AndroidTermuxBridge(private val context: Context) : TermuxBridge {

    override fun isInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    override fun launch(runId: String, wrappedCommand: String, workingDirectory: String?): Boolean {
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PACKAGE, TERMUX_RUN_SERVICE)
            putExtra(EXTRA_PATH, TERMUX_BASH)
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", wrappedCommand))
            putExtra(EXTRA_WORKDIR, workingDirectory ?: TERMUX_HOME)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_SESSION_ACTION, 0)
        }
        return try {
            context.startService(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun resultDir(runId: String): File =
        File("/storage/emulated/0/Download/nativeai", runId)

    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val TERMUX_RUN_SERVICE = "com.termux.app.RunCommandService"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        const val TERMUX_HOME = "/data/data/com.termux/files/home"
    }
}

/**
 * Termux execution backend at the core execution layer (Track E). Runs one
 * short-lived command per call via Termux's RunCommandService and captures
 * stdout/stderr/exit code through a shared-storage file exchange
 * (/sdcard/Download/nativeai/<runId>/). Bounded: hard timeout, cancellation,
 * best-effort kill, cleanup. Optional dependency: the engine works without
 * Termux installed (ExecutionManager falls back to LocalProcessBackend).
 */
class TermuxBackend(
    private val bridge: TermuxBridge,
    private val pollIntervalMs: Long = 300,
    private val probeTimeoutMs: Long = 8_000,
) : ExecutionBackend {

    override val available: Boolean get() = status == TermuxStatus.READY

    private var cachedStatus: TermuxStatus? = null
    private var reason: String = ""
    private var lastProbeMs = 0L

    /** Non-blocking status: cached probe result, else cheap install check. */
    val status: TermuxStatus
        get() = cachedStatus ?: if (bridge.isInstalled()) TermuxStatus.INSTALLED else TermuxStatus.NOT_INSTALLED

    val statusReason: String get() = reason

    /** Full capability probe: runs `echo termux-ok` through Termux. */
    suspend fun probe(): TermuxStatus {
        cachedStatus = null
        reason = ""
        if (!bridge.isInstalled()) {
            cachedStatus = TermuxStatus.NOT_INSTALLED
            reason = "Termux is not installed (F-Droid or Play Store)"
            return cachedStatus!!
        }
        cachedStatus = TermuxStatus.INSTALLED
        val r = execute(ExecutionRequest(command = "echo termux-ok", timeoutMs = probeTimeoutMs))
        cachedStatus = when {
            r.exitCode == 0 && r.stdout.contains("termux-ok") -> {
                lastProbeMs = System.currentTimeMillis()
                reason = "ready"
                TermuxStatus.READY
            }
            r.exitCode == 126 -> TermuxStatus.SETUP_REQUIRED.also {
                reason = "Termux rejected the probe \u2014 enable \u201cAllow external apps\u201d in Termux settings"
            }
            r.timedOut -> TermuxStatus.SETUP_REQUIRED.also {
                reason = "Termux did not respond \u2014 enable \u201cAllow external apps\u201d and run termux-setup-storage once (grant storage if asked)"
            }
            else -> TermuxStatus.ERROR.also {
                reason = "probe failed: ${r.stderr.take(120)}"
            }
        }
        return cachedStatus!!
    }

    override suspend fun execute(request: ExecutionRequest): ExecutionResult =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val runId = "run-$started-${(Math.random() * 1000).toInt()}"
            val dir = bridge.resultDir(runId)
            try {
                dir.mkdirs()
            } catch (e: Exception) {
                return@withContext ExecutionResult(
                    126, "", "termux output dir unavailable: ${e.message}", 0,
                )
            }
            val wrapped = wrap(request, runId)
            if (!bridge.launch(runId, wrapped, request.workingDirectory)) {
                return@withContext ExecutionResult(
                    126, "",
                    "Termux rejected the command — enable \u201cAllow external apps\u201d in Termux settings",
                    System.currentTimeMillis() - started,
                )
            }
            val codeFile = File(dir, "code.txt")
            var code: String? = null
            val deadline = started + request.timeoutMs
            while (System.currentTimeMillis() < deadline) {
                if (!coroutineContext.isActive) {
                    cleanup(dir)
                    return@withContext ExecutionResult(
                        -1, "", "cancelled",
                        System.currentTimeMillis() - started, cancelled = true,
                    )
                }
                if (codeFile.exists()) {
                    code = runCatching { codeFile.readText().trim() }.getOrNull()
                    break
                }
                delay(pollIntervalMs)
            }
            if (code == null) {
                kill(runId)
                cleanup(dir)
                return@withContext ExecutionResult(
                    -1, "", "termux timed out",
                    System.currentTimeMillis() - started, timedOut = true,
                )
            }
            val stdout = runCatching { File(dir, "out.txt").readText() }.getOrNull() ?: ""
            val stderr = runCatching { File(dir, "err.txt").readText() }.getOrNull() ?: ""
            cleanup(dir)
            ExecutionResult(
                code.toIntOrNull() ?: -1, stdout, stderr,
                System.currentTimeMillis() - started,
            )
        }

    private fun wrap(request: ExecutionRequest, runId: String): String = buildString {
        if (request.environment.isNotEmpty()) {
            append(request.environment.entries.joinToString("; ") { (k, v) ->
                "export $k=${shellQuote(v)}"
            })
            append("; ")
        }
        val out = bridge.resultDir(runId).absolutePath
        append(request.command)
        append(" > ")
        append(shellQuote("$out/out.txt"))
        append(" 2> ")
        append(shellQuote("$out/err.txt"))
        append("; echo \$? > ")
        append(shellQuote("$out/code.txt"))
        // Run tag lands in argv so best-effort pkill can match it.
        append(" # nativeai-$runId")
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun kill(runId: String) {
        // Best-effort: pkill matches the bash argv tag, not the env.
        bridge.launch("kill-$runId", "pkill -f nativeai-$runId", null)
    }

    private fun cleanup(dir: File) {
        runCatching {
            File(dir, "out.txt").delete()
            File(dir, "err.txt").delete()
            File(dir, "code.txt").delete()
            dir.delete()
        }
    }
}
