package com.engine.nativeai

/**
 * Pure lifecycle transitions for the engine service (Phase A4). The service
 * applies these and mirrors the result into a StateFlow; the UI only reads.
 * Every transition comes from a real event (start, ready, run started/ended,
 * error, stop) — no UI component guesses service state.
 */
class EngineServiceStateMachine {

    var state: EngineServiceState = EngineServiceState.STOPPED
        private set

    /** Service received onStartCommand and is creating the engine. */
    fun start() {
        state = EngineServiceState.STARTING
    }

    /** Engine + memory + watchdog created successfully. */
    fun ready() {
        if (state == EngineServiceState.STARTING || state == EngineServiceState.ERROR) {
            state = EngineServiceState.READY
        }
    }

    /** Mirrors an active agent/generation run. Only READY may become BUSY. */
    fun busy(busy: Boolean) {
        state = when {
            busy && state == EngineServiceState.READY -> EngineServiceState.BUSY
            !busy && state == EngineServiceState.BUSY -> EngineServiceState.READY
            else -> state
        }
    }

    /** Engine init failed; the service stays alive so START_STICKY can retry. */
    fun error() {
        state = EngineServiceState.ERROR
    }

    /** Service destroyed. */
    fun stop() {
        state = EngineServiceState.STOPPED
    }
}
