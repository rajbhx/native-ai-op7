package com.engine.nativeai

import android.content.Context

/**
 * Core execution-layer selection (Track E). The agent only ever sees an
 * [ExecutionBackend]; this chooses Termux when it is READY, otherwise the
 * local short-lived process backend. Termux stays optional — the AI engine
 * works with neither installed, and the UI never pretends otherwise.
 *
 * Shared process-wide (Phase A4): the engine service and the UI must manage
 * the same backend so service shutdown can terminate managed exec processes.
 */
class ExecutionManager private constructor(private val context: Context) {

    private val bridge = AndroidTermuxBridge(context)
    val termux = TermuxBackend(bridge)
    private var local: LocalProcessBackend? = null

    /** Backend used by the TerminalTool: Termux when READY, else local. */
    fun backend(): ExecutionBackend =
        if (termux.status == TermuxStatus.READY) termux
        else local ?: LocalProcessBackend(defaultWorkingDirectory = context.filesDir).also { local = it }

    fun status(): TermuxStatus = termux.status
    fun statusReason(): String = termux.statusReason
    suspend fun probe(): TermuxStatus = termux.probe()

    /** Kill managed processes and drop cached backends (service shutdown). */
    fun shutdown() {
        termux.shutdown()
        local?.shutdown()
        local = null
    }

    companion object {
        @Volatile private var instance: ExecutionManager? = null

        fun shared(context: Context): ExecutionManager =
            instance ?: synchronized(this) {
                instance ?: ExecutionManager(context.applicationContext).also { instance = it }
            }
    }
}
