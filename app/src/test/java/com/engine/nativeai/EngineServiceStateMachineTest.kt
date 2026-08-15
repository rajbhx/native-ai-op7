package com.engine.nativeai

import org.junit.Assert.assertEquals
import org.junit.Test

class EngineServiceStateMachineTest {

    @Test
    fun lifecycleStartsStopped() {
        assertEquals(EngineServiceState.STOPPED, EngineServiceStateMachine().state)
    }

    @Test
    fun startThenReady() {
        val m = EngineServiceStateMachine()
        m.start()
        assertEquals(EngineServiceState.STARTING, m.state)
        m.ready()
        assertEquals(EngineServiceState.READY, m.state)
    }

    @Test
    fun busyOnlyFromReady() {
        val m = EngineServiceStateMachine()
        m.start()
        m.busy(true)
        assertEquals(EngineServiceState.STARTING, m.state)
        m.ready()
        m.busy(true)
        assertEquals(EngineServiceState.BUSY, m.state)
        m.busy(false)
        assertEquals(EngineServiceState.READY, m.state)
    }

    @Test
    fun errorThenRetryReady() {
        val m = EngineServiceStateMachine()
        m.start()
        m.error()
        assertEquals(EngineServiceState.ERROR, m.state)
        m.ready()
        assertEquals(EngineServiceState.READY, m.state)
    }

    @Test
    fun errorIgnoresBusy() {
        val m = EngineServiceStateMachine()
        m.start()
        m.error()
        m.busy(true)
        assertEquals(EngineServiceState.ERROR, m.state)
    }

    @Test
    fun stopEndsLifecycle() {
        val m = EngineServiceStateMachine()
        m.start()
        m.ready()
        m.stop()
        assertEquals(EngineServiceState.STOPPED, m.state)
    }
}
