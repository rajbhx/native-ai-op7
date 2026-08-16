package com.engine.nativeai

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Which model/provider handled the last route (for the remote/local badge). */
data class RouteInfo(val modelId: String, val provider: String, val isRemote: Boolean)

/** One transient message: text + optional action (snackbar with Retry/Undo). */
data class UiEvent(
    val text: String,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

/**
 * Single authoritative owner of the run state (golden UX: one-way data
 * flow, configuration-change survival). The UI renders StateFlows and calls
 * methods — it never holds engine state itself. Deps are attached once per
 * ViewModel lifetime; the run coroutine lives in the ViewModel scope so a
 * rotation no longer cancels an in-flight agent run.
 */
open class EngineViewModel(
    /** Saved state (bundle) so a process-death recreation restores the
     *  prompt instead of wiping it — golden UX: state survives the process,
     *  not just rotation. Selection already survives via prefs. */
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {

    // Attached once by EngineScreen (same instance across rotations).
    private var engine: NativeEngine? = null
    private var appContext: Context? = null
    private var localLibrary: LocalModelLibrary? = null
    private var registry: ModelRegistry? = null
    private var prefs: ModelPreferencesStore? = null
    private var toolbox: Toolbox? = null
    /** Owned here so accumulated metrics survive configuration changes. */
    val runtimeMetrics = RuntimeMetrics()

    private val vmScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Shared per-provider health/latency so every run starts from measurements. */
    private val healthMonitor = ProviderHealthMonitor()

    // ---- Run state (survives configuration change) ----
    private val _prompt = MutableStateFlow(savedState.get<String>(KEY_PROMPT) ?: "")
    val prompt: StateFlow<String> = _prompt.asStateFlow()
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output.asStateFlow()
    private val _answer = MutableStateFlow("")
    val answer: StateFlow<String> = _answer.asStateFlow()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _engineState = MutableStateFlow(EngineUiState.READY)
    val engineState: StateFlow<EngineUiState> = _engineState.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private var loadedPath: String? = null
    private var runJob: Job? = null
    /** Open conversation thread (P3): quick sends and agent runs share one
     *  session so follow-ups get real prior-turn context; Clear starts a new
     *  conversation. */
    private var activeSessionId: Long? = null

    // ---- Live run extras ----
    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()
    private val _lastRoute = MutableStateFlow<RouteInfo?>(null)
    val lastRoute: StateFlow<RouteInfo?> = _lastRoute.asStateFlow()
    private val _lastSources = MutableStateFlow<List<String>>(emptyList())
    val lastSources: StateFlow<List<String>> = _lastSources.asStateFlow()

    // ---- Transient feedback + approval channel ----
    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()
    private val _pendingApproval = MutableStateFlow<ToolApprovalRequest?>(null)
    val pendingApproval: StateFlow<ToolApprovalRequest?> = _pendingApproval.asStateFlow()
    private var approvalGate: CompletableDeferred<ApprovalDecision>? = null

    fun attach(
        engine: NativeEngine,
        context: Context,
        localLibrary: LocalModelLibrary,
        registry: ModelRegistry,
        prefs: ModelPreferencesStore,
        toolbox: Toolbox,
    ) {
        this.engine = engine
        this.appContext = context.applicationContext
        this.localLibrary = localLibrary
        this.registry = registry
        this.prefs = prefs
        this.toolbox = toolbox
    }

    fun setPrompt(v: String) {
        _prompt.value = v
        savedState[KEY_PROMPT] = v
    }
    fun setStatus(v: String) = _status.run { value = v }
    fun setOutput(v: String) = _output.run { value = v }
    fun setAnswer(v: String) = _answer.run { value = v }
    fun setRunning(v: Boolean) = _running.run { value = v }
    fun setEngineState(v: EngineUiState) = _engineState.run { value = v }

    fun notify(text: String) {
        _events.tryEmit(UiEvent(text))
    }

    fun clearRun() {
        // Clear = new conversation: close the thread so the next send starts
        // a fresh session instead of pretending to continue the old one.
        activeSessionId?.let { id ->
            runCatching { toolbox?.memory?.endSession(id) }
        }
        activeSessionId = null
        _prompt.value = ""
        savedState[KEY_PROMPT] = ""
        _answer.value = ""
        _output.value = ""
        _engineState.value = EngineUiState.READY
        _status.value = ""
        _lastRoute.value = null
        _lastSources.value = emptyList()
        _elapsedMs.value = 0L
    }

    override fun onCleared() {
        approvalGate?.complete(ApprovalDecision.DENY)
        approvalGate = null
        vmScope.cancel()
    }

    companion object {
        private const val KEY_PROMPT = "prompt"
    }

    // ---- Local GGUF pre-flight + load (moved from the composable) ----
    suspend fun ensureLocalLoaded(
        file: File?,
        contextSize: Int,
        threads: Int,
        modelsDir: File,
    ): Boolean {
        val target = file
        val ctx = appContext ?: return false
        if (target == null) {
            _engineState.value = EngineUiState.ERROR
            _status.value = "Model not found:\n${modelsDir.absolutePath}\nPick or download a GGUF, then retry."
            return false
        }
        if (_loaded.value && loadedPath == target.absolutePath) return true
        val meta = GgufMetaCache.metaFor(target)
        val plan = MemoryPlanner.plan(
            modelBytes = target.length(),
            nCtx = contextSize,
            availRamBytes = availableRamBytes(ctx),
            layers = meta?.layers,
            hiddenDim = meta?.embeddingDim,
        )
        if (!plan.withinBudget) {
            _engineState.value = EngineUiState.ERROR
            _status.value = "MODEL MAY EXCEED AVAILABLE MEMORY \u2014 estimated " +
                "${plan.totalMb.toInt()} MB vs cap ${plan.availableCapMb.toInt()} MB; " +
                "drop context to ${plan.maxSafeNctx} or use a smaller GGUF"
            return false
        }
        _engineState.value = EngineUiState.LOADING
        _status.value = "loading model (threads=$threads)\u2026"
        val eng = engine ?: return false
        return try {
            // Model load is blocking native work — never on the Main thread.
            val ok = withContext(Dispatchers.Default) {
                // Integrity gate: refuse to load a model whose manifest
                // checksum does not match (corrupt file — re-download).
                ModelManifest.entryFor(target)?.let { entry ->
                    if (!ModelIntegrity.matches(target, entry.sha256)) {
                        _engineState.value = EngineUiState.ERROR
                        _status.value = "checksum mismatch for ${target.name} \u2014 delete and re-download"
                        CoreErrors.log.record("local", "checksum mismatch: ${target.name}", null as Throwable?)
                        return@withContext false
                    }
                }
                if (_loaded.value) eng.close()
                val t0 = System.currentTimeMillis()
                eng.init(
                    EngineConfig(
                        modelPath = target.absolutePath,
                        threads = threads,
                        contextSize = contextSize,
                        nativeLibDir = ctx.applicationInfo.nativeLibraryDir,
                    ),
                )
                runtimeMetrics.recordModelLoad(System.currentTimeMillis() - t0)
                true
            }
            if (ok) {
                _loaded.value = true
                loadedPath = target.absolutePath
                // READY, not COMPLETED: loading a model is not a task result.
                _engineState.value = EngineUiState.READY
                _status.value = "Model loaded: ${target.name} (threads=$threads)"
            }
            ok
        } catch (e: Exception) {
            loadedPath = null
            _loaded.value = false
            _engineState.value = EngineUiState.ERROR
            _status.value = "init failed: ${e.message}"
            CoreErrors.log.record("local", "local model init failed: ${e.message}", e)
            false
        }
    }

    // ---- Quick completion (Send / IME Send) ----
    fun sendQuick(mode: RoutingMode, preferredId: String?, contextSize: Int, threads: Int, modelsDir: File) {
        val p = prefs ?: return
        if (_prompt.value.isBlank() || _running.value) return
        // Set the flag synchronously so a rapid double-send can never start
        // two overlapping runs; the finally block always resets it.
        _running.value = true
        runJob = vmScope.launch(Dispatchers.Default) {
            // Hoisted so the failure handler can report the routed provider
            // and the conversation exchange survives errors/stops (P3).
            var routedId: String? = null
            val sb = StringBuilder()
            val memory = toolbox?.memory
            var sessionId: Long? = null
            try {
                val ctx = appContext ?: return@launch
                val reg = registry ?: return@launch
                val effectiveMode = if (p.privacyMode == PrivacyMode.LOCAL_ONLY) {
                    RoutingMode.OFFLINE_ONLY
                } else {
                    mode
                }
                // Multi-turn continuity: reuse the open thread, else start one.
                sessionId = activeSessionId ?: runCatching {
                    memory?.startSession("quick: ${_prompt.value.take(60)}")
                        ?.also { activeSessionId = it }
                }.getOrNull()
                if (sessionId != null) {
                    runCatching { memory?.recordMessage(sessionId, "user", _prompt.value) }
                }
                val descriptor = ModelRouter(effectiveMode, healthMonitor).route(
                    AgentTask(
                        prompt = _prompt.value,
                        taskType = TaskType.CHAT,
                        contextLength = contextSize,
                        networkAvailable = hasNetwork(ctx),
                    ),
                    reg,
                    preferredId = preferredId,
                )
                if (descriptor == null) {
                    _status.value = "no model available for quick completion"
                    _engineState.value = EngineUiState.ERROR
                    return@launch
                }
                routedId = descriptor.id
                if (descriptor.kind == ModelKind.LOCAL &&
                    !ensureLocalLoaded(localLibrary?.resolve(descriptor.id), contextSize, threads, modelsDir)
                ) {
                    return@launch
                }
                val provider = reg.providerFor(descriptor)
                if (provider == null) {
                    _status.value = "provider not ready: ${descriptor.id}"
                    _engineState.value = EngineUiState.ERROR
                    return@launch
                }
                _running.value = true
                _output.value = ""
                _answer.value = ""
                _engineState.value = EngineUiState.THINKING
                val fallbackNote = if (preferredId != null && preferredId != descriptor.id) {
                    val why = healthMonitor.lastError(preferredId).ifEmpty { "not available in this mode" }
                    " (fallback from $preferredId: $why)"
                } else ""
                _status.value = "generating via ${descriptor.id}$fallbackNote\u2026"
                _lastRoute.value = RouteInfo(descriptor.id, descriptor.provider, descriptor.kind == ModelKind.REMOTE)
                val startedMs = System.currentTimeMillis()
                provider.stream(
                    ModelRequest(
                        prompt = _prompt.value,
                        system = p.systemPromptOverride ?: "",
                        maxTokens = 64,
                    ),
                ).collect { ev ->
                    when (ev) {
                        is ModelStreamEvent.Token -> {
                            sb.append(ev.text)
                            _answer.value = sb.toString()
                        }
                        is ModelStreamEvent.Reasoning -> Unit
                        is ModelStreamEvent.Done -> Unit
                        is ModelStreamEvent.Error -> throw IllegalStateException(ev.message)
                    }
                }
                healthMonitor.reportSuccess(descriptor.id)
                healthMonitor.reportLatency(descriptor.id, System.currentTimeMillis() - startedMs)
                if (sessionId != null) {
                    runCatching { memory?.recordMessage(sessionId, "agent", sb.toString().take(4000)) }
                }
                _status.value = "generation complete (${sb.length} chars)"
                _engineState.value = EngineUiState.COMPLETED
            } catch (e: CancellationException) {
                if (sessionId != null) {
                    runCatching { memory?.recordMessage(sessionId, "agent", sb.toString().ifBlank { "[stopped] no output" }) }
                }
                _status.value = "generation stopped"
                _engineState.value = EngineUiState.READY
            } catch (e: Exception) {
                val friendly = friendlyGenerateError(e)
                if (sessionId != null) {
                    runCatching { memory?.recordMessage(sessionId, "agent", "[error] $friendly") }
                }
                healthMonitor.reportFailure(routedId ?: "unknown", e.message ?: "generate failed")
                CoreErrors.log.record("generate", "generate failed: ${e.message}", e)
                _status.value = "generate failed: $friendly"
                _engineState.value = EngineUiState.ERROR
            } finally {
                _running.value = false
                _elapsedMs.value = 0L
                runJob = null
            }
        }
    }

    // ---- Agent (full tool loop, moved from the composable) ----
    fun runAgent(
        context: Context,
        registry: ModelRegistry,
        prefs: ModelPreferencesStore,
        toolbox: Toolbox,
        prompt: String,
        mode: RoutingMode,
        modeLabel: String,
        preferredId: String?,
    ) {
        val effectiveMode = if (prefs.privacyMode == PrivacyMode.LOCAL_ONLY) {
            RoutingMode.OFFLINE_ONLY
        } else {
            mode
        }
        if (_running.value) return
        _running.value = true
        _output.value = ""
        _answer.value = ""
        _lastSources.value = emptyList()
        _status.value = "agent running\u2026"
        _engineState.value = EngineUiState.THINKING
        _elapsedMs.value = 0L
        val elapsedJob = vmScope.launch {
            while (_running.value) {
                delay(1000)
                _elapsedMs.value += 1000
            }
        }
        // The whole agent loop (router, tools, model stream) runs off Main;
        // UI updates go through thread-safe StateFlows.
        runJob = vmScope.launch(Dispatchers.Default) {
            try {
                val memory = toolbox.memory
                val tools = toolbox.tools
                val agent = ThinkingAgent(
                    router = ModelRouter(mode = effectiveMode, healthMonitor = healthMonitor),
                    registry = registry,
                    memory = memory,
                    tools = tools,
                    networkAvailable = hasNetwork(context),
                    preferredId = preferredId,
                    systemPromptOverride = prefs.systemPromptOverride,
                    sourceSearch = toolbox.sourceSearch,
                    skills = toolbox.skillManager.list(),
                    onApproval = ::approvalCallback,
                )
                // Multi-turn continuity (P3): the open thread's tail becomes
                // prior-conversation context (never the private memory DB —
                // only the user/agent exchange, oldest first). Follow-ups
                // reuse the same session so the agent sees what it just said.
                val priorConversation = try {
                    val sid = activeSessionId ?: memory.recentSessions(1).firstOrNull()?.id
                    if (sid != null) {
                        memory.conversationTail(sid, 8)
                            .joinToString("\n") {
                                if (it.role == "user") "YOU: ${it.content}" else "AGENT: ${it.content}"
                            }.take(2000)
                    } else ""
                } catch (e: Exception) {
                    ""
                }
                val sessionId = activeSessionId ?: try {
                    val id = memory.startSession("agent: ${prompt.take(60)}")
                    activeSessionId = id
                    id
                } catch (e: Exception) {
                    null // memory failure must never crash the agent
                }
                if (sessionId != null) {
                    try {
                        memory.recordMessage(sessionId, "user", prompt)
                    } catch (e: Exception) {
                        // best-effort persistence; never crashes the run
                    }
                }
                try {
                    val steps = StringBuilder()
                    val answer = StringBuilder()
                    var done = false
                    var tokenCount = 0
                    var routedCount = 0
                    var toolName = ""
                    var toolStartedMs = 0L
                    val runStarted = System.currentTimeMillis()
                    val stampFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                    fun log(s: String) {
                        steps.appendLine(java.time.LocalTime.now().format(stampFmt) + " " + s)
                    }
                    agent.run(
                        prompt,
                        GenerationConfig(maxTokens = 256),
                        priorConversation = priorConversation,
                    ).collect { ev ->
                        when (ev) {
                            is AgentEvent.Token -> {
                                tokenCount++
                                runtimeMetrics.recordFirstToken(System.currentTimeMillis() - runStarted)
                                answer.append(ev.text)
                                _answer.value = answer.toString()
                            }
                            is AgentEvent.Stage -> {
                                log("[${ev.state}]")
                                _output.value = steps.toString()
                                _engineState.value = when (ev.state) {
                                    AgentState.VERIFY -> EngineUiState.VERIFYING
                                    AgentState.EXECUTE, AgentState.OBSERVE -> EngineUiState.TOOL
                                    else -> EngineUiState.THINKING
                                }
                            }
                            is AgentEvent.Routed -> {
                                routedCount++
                                if (routedCount > 1) runtimeMetrics.recordRetry()
                                val remote = ev.provider != "local"
                                _lastRoute.value = RouteInfo(ev.modelId, ev.provider, remote)
                                log("MODEL ${ev.modelId}")
                                log("PROVIDER ${ev.provider}")
                                if (ev.reason.isNotBlank()) log("FALLBACK ${ev.reason}")
                                log("MODE ${if (remote) "Remote ($modeLabel)" else modeLabel}")
                                log("STATUS ${if (remote) "Running \u00b7 remote request" else "Running \u00b7 local"}")
                                log("[${ev.taskType} | ${ev.costTier}]")
                                _output.value = steps.toString()
                            }
                            is AgentEvent.ToolCall -> {
                                toolName = ev.tool
                                toolStartedMs = System.currentTimeMillis()
                                log("[TOOL] ${ev.tool}(${ev.input})")
                                _output.value = steps.toString()
                                _engineState.value = EngineUiState.TOOL
                            }
                            is AgentEvent.Observation -> {
                                if (toolName.isNotEmpty()) {
                                    runtimeMetrics.recordTool(toolName, System.currentTimeMillis() - toolStartedMs, true)
                                    toolName = ""
                                }
                                log("[OBS] ${ev.output.take(240)}")
                                _output.value = steps.toString()
                            }
                            is AgentEvent.Verification -> {
                                if (!ev.passed && toolName.isNotEmpty()) {
                                    runtimeMetrics.recordTool(toolName, 0L, false)
                                    toolName = ""
                                }
                                log("[VERIFY] ${ev.tool}: " +
                                    if (ev.passed) "passed" else "failed")
                                _output.value = steps.toString()
                                _engineState.value = EngineUiState.VERIFYING
                            }
                            is AgentEvent.Final -> {
                                done = true
                                log("[FINAL]")
                                answer.setLength(0)
                                answer.append(ev.answer)
                                _lastSources.value = ev.sources
                                if (sessionId != null) {
                                    try {
                                        memory.recordMessage(sessionId, "agent", ev.answer)
                                    } catch (_: Exception) {
                                        // best-effort conversation persistence
                                    }
                                }
                                _output.value = steps.toString()
                                _answer.value = answer.toString()
                                _engineState.value = EngineUiState.COMPLETED
                            }
                            is AgentEvent.Error -> {
                                runtimeMetrics.recordError()
                                log("[ERROR] ${ev.message}")
                                _output.value = steps.toString()
                                _status.value = "agent error: ${ev.message}"
                                _engineState.value = EngineUiState.ERROR
                                if (sessionId != null) {
                                    runCatching {
                                        memory.recordMessage(sessionId, "agent", "[error] ${ev.message}")
                                    }
                                }
                            }
                        }
                    }
                    runtimeMetrics.recordRun(tokenCount, System.currentTimeMillis() - runStarted)
                    if (done) {
                        try {
                            withContext(Dispatchers.IO) { memory.deleteLowUtilityMemories(0.05f) }
                        } catch (_: Exception) {
                            // best-effort memory pruning — never fails the run
                        }
                    }
                    _status.value = if (done) "agent done" else "agent ended without final answer"
                    if (!done) _engineState.value = EngineUiState.READY
                } catch (e: CancellationException) {
                    runCatching {
                        if (sessionId != null) {
                            memory.recordMessage(
                                sessionId, "agent",
                                answer.toString().ifBlank { "[stopped] no output" },
                            )
                        }
                    }
                    _status.value = "agent stopped"
                    _engineState.value = EngineUiState.READY
                    throw e
                } catch (e: Exception) {
                    val friendly = friendlyGenerateError(e)
                    runCatching {
                        if (sessionId != null) {
                            memory.recordMessage(sessionId, "agent", "[error] $friendly")
                        }
                    }
                    CoreErrors.log.record("agent", "run failed: ${e.message}", e)
                    _status.value = "agent failed: $friendly"
                    _engineState.value = EngineUiState.ERROR
                } finally {
                    // The session stays open for follow-ups (P3): it closes on
                    // Clear / new conversation, not at the end of every run.
                    _running.value = false
                }
            } catch (e: CancellationException) {
                if (_output.value.isNotBlank()) {
                    _output.value = _output.value.trimEnd('\n') + "\n[STOPPED]"
                }
                _status.value = "agent stopped"
                _engineState.value = EngineUiState.READY
            } finally {
                _running.value = false
                runJob = null
                elapsedJob.cancel()
                _elapsedMs.value = 0L
            }
        }
    }

    /** Stops the run and unblocks any pending approval with DENY. */
    fun stop() {
        approvalGate?.complete(ApprovalDecision.DENY)
        if (runJob == null) {
            engine?.cancel()
            return
        }
        // Native llama.cpp decode is blocking; request native cancellation
        // first, then cancel the job. `running` stays true until the job
        // unwinds so a new run cannot overlap the previous decode.
        // The run coroutine's own CancellationException handler owns the
        // "[STOPPED]" trace line and status — appending here would double it.
        engine?.cancel()
        runJob?.cancel()
    }

    fun resolveApproval(decision: ApprovalDecision) {
        approvalGate?.complete(decision)
    }

    private suspend fun approvalCallback(req: ToolApprovalRequest): ApprovalDecision {
        val p = prefs
        if (p != null && req.tool in p.toolAlwaysAllow) return ApprovalDecision.ALLOW_ONCE
        appendTraceLine("[TOOL\u00b7APPROVAL] ${req.tool}: awaiting your decision")
        val gate = CompletableDeferred<ApprovalDecision>()
        approvalGate = gate
        _pendingApproval.value = req
        return try {
            val decision = gate.await()
            appendTraceLine("[TOOL\u00b7APPROVAL] ${req.tool}: ${decision.name.lowercase(Locale.US)}")
            if (decision == ApprovalDecision.ALWAYS_ALLOW && p != null) {
                p.toolAlwaysAllow = p.toolAlwaysAllow + req.tool
            }
            decision
        } finally {
            _pendingApproval.value = null
            approvalGate = null
        }
    }

    private fun appendTraceLine(line: String) {
        val t = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        _output.value = _output.value.trimEnd('\n') + "\n$t $line"
    }

    /** Translates raw provider errors into actionable user-facing text.
     *  Unknown errors pass through unchanged (honest, never invented). */
    private fun friendlyGenerateError(e: Exception): String {
        val msg = e.message ?: "operation failed"
        val lower = msg.lowercase(Locale.US)
        return when {
            "rate limit" in lower || "rate_limit" in lower ->
                "Free tier rate limit reached (anonymous). Add a free OpenCode Zen key via Configure, wait for the limit to reset, or use a local model."
            "unauthorized" in lower || "401" in msg || "api key" in lower ->
                "Provider rejected the request \u2014 add or fix the API key via Configure."
            "timeout" in lower || "timed out" in lower ->
                "Provider timed out \u2014 check connectivity and retry."
            "network" in lower || "no internet" in lower ->
                "Network unavailable \u2014 switch to a local model or reconnect."
            else -> msg
        }
    }

    private fun hasNetwork(context: Context): Boolean =
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            cm?.activeNetworkInfo?.isConnected ?: false
        }.getOrDefault(false)

    private fun availableRamBytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return Long.MAX_VALUE
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem
    }
}
