package com.engine.nativeai

import androidx.lifecycle.SavedStateHandle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** Installs a test Main dispatcher so ViewModel coroutines work in JVM tests.
 *  Note: the Main test dispatcher has its own scheduler, separate from
 *  runTest's — tests that launch VM coroutines must advance
 *  `mainDispatcherRule.testDispatcher` explicitly (e.g. advanceUntilIdle). */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}

/**
 * ViewModel state-machine tests (golden UX P0: single authority). JVM-only:
 * covers every path that does not require an Android Context; Context-bound
 * paths (attach/runAgent) are exercised on device via CI smoke tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun newVm() = EngineViewModel()

    /** Exposes the protected ViewModel lifecycle hook for tests. */
    private class TestEngineViewModel(savedState: SavedStateHandle = SavedStateHandle()) :
        EngineViewModel(savedState) {
        fun invokeOnCleared() = onCleared()
    }

    @Test
    fun initialStateIsEmptyAndReady() {
        val vm = newVm()
        assertEquals("", vm.prompt.value)
        assertEquals("", vm.output.value)
        assertEquals("", vm.answer.value)
        assertEquals(EngineUiState.READY, vm.engineState.value)
        assertFalse(vm.running.value)
        assertFalse(vm.loaded.value)
        assertNull(vm.lastRoute.value)
        assertNull(vm.pendingApproval.value)
    }

    @Test
    fun promptSurvivesProcessDeathViaSavedState() {
        val saved = SavedStateHandle()
        val vm = EngineViewModel(saved)
        vm.setPrompt("remember me")
        assertEquals("remember me", saved["prompt"])
        // Process death: a fresh VM over the same bundle restores the prompt.
        val reborn = EngineViewModel(saved)
        assertEquals("remember me", reborn.prompt.value)
        reborn.clearRun()
        assertEquals("", reborn.prompt.value)
        assertEquals("", saved["prompt"])
    }

    @Test
    fun settersReflectInStateFlows() {
        val vm = newVm()
        vm.setPrompt("hello")
        vm.setOutput("trace line")
        vm.setAnswer("final answer")
        vm.setStatus("idle")
        vm.setRunning(true)
        vm.setEngineState(EngineUiState.THINKING)
        assertEquals("hello", vm.prompt.value)
        assertEquals("trace line", vm.output.value)
        assertEquals("final answer", vm.answer.value)
        assertEquals("idle", vm.status.value)
        assertTrue(vm.running.value)
        assertEquals(EngineUiState.THINKING, vm.engineState.value)
    }

    @Test
    fun clearRunResetsPromptAnswerAndOutput() {
        val vm = newVm()
        vm.setPrompt("p")
        vm.setAnswer("a")
        vm.setOutput("o")
        vm.clearRun()
        assertEquals("", vm.prompt.value)
        assertEquals("", vm.answer.value)
        assertEquals("", vm.output.value)
        assertFalse(vm.running.value)
    }

    @Test
    fun clearRunResetsFullRunState() {
        val vm = newVm()
        vm.setPrompt("p")
        vm.setAnswer("a")
        vm.setOutput("o")
        vm.setStatus("agent done")
        vm.setEngineState(EngineUiState.COMPLETED)
        vm.clearRun()
        assertEquals("", vm.prompt.value)
        assertEquals("", vm.answer.value)
        assertEquals("", vm.output.value)
        assertEquals("", vm.status.value)
        assertEquals(EngineUiState.READY, vm.engineState.value)
        assertNull(vm.lastRoute.value)
        assertEquals(0L, vm.elapsedMs.value)
    }

    @Test
    fun sendQuickWithBlankPromptIsNoop() = runTest {
        val vm = newVm()
        vm.sendQuick(RoutingMode.HYBRID, null, 2048, 4, File("/tmp/models"))
        assertFalse(vm.running.value)
        assertEquals(EngineUiState.READY, vm.engineState.value)
    }

    @Test
    fun sendQuickWithoutAttachIsNoop() = runTest {
        val vm = newVm()
        vm.setPrompt("hello")
        vm.sendQuick(RoutingMode.HYBRID, null, 2048, 4, File("/tmp/models"))
        assertFalse(vm.running.value)
        assertEquals(EngineUiState.READY, vm.engineState.value)
    }

    @Test
    fun stopWithoutRunDoesNotCrash() = runTest {
        val vm = newVm()
        vm.stop()
        vm.stop()
        assertFalse(vm.running.value)
    }

    @Test
    fun resolveApprovalWithoutPendingDoesNotCrash() = runTest {
        val vm = newVm()
        vm.resolveApproval(ApprovalDecision.ALLOW_ONCE)
        vm.resolveApproval(ApprovalDecision.DENY)
        assertNull(vm.pendingApproval.value)
    }

    @Test
    fun onClearedCompletesPendingApprovalWithDeny() = runTest {
        val vm = TestEngineViewModel()
        vm.invokeOnCleared()
        // No crash, gate (if any) resolved DENY — run state stays safe.
        assertNull(vm.pendingApproval.value)
    }

    @Test
    fun notifyEmitsUiEventToSubscribers() = runTest {
        val vm = newVm()
        val received = mutableListOf<String>()
        val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.events.collect { received += it.text }
        }
        vm.notify("model loaded")
        vm.notify("download finished")
        assertEquals(listOf("model loaded", "download finished"), received)
        collector.cancel()
    }
}
