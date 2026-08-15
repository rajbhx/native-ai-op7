package com.engine.nativeai

import android.content.Context

/**
 * Core execution-layer selection (Track E). The agent only ever sees an
 * [ExecutionBackend]; this chooses Termux when it is READY, otherwise the
 * local short-lived process backend. Termux stays optional — the AI engine
 * works with neither installed, and the UI never pretends otherwise.
 */
class ExecutionManager(private val context: Context) {

    private val bridge = AndroidTermuxBridge(context)
    val termux = TermuxBackend(bridge)

    /** Backend used by the TerminalTool: Termux when READY, else local. */
    fun backend(): ExecutionBackend =
        if (termux.status == TermuxStatus.READY) termux
        else LocalProcessBackend(defaultWorkingDirectory = context.filesDir)

    fun status(): TermuxStatus = termux.status
    fun statusReason(): String = termux.statusReason
    suspend fun probe(): TermuxStatus = termux.probe()
}
