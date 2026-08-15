package com.engine.nativeai.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.engine.nativeai.AgentEvent
import com.engine.nativeai.AgentState
import com.engine.nativeai.AgentTask
import com.engine.nativeai.CalculatorTool
import com.engine.nativeai.EngineForegroundService
import com.engine.nativeai.EngineConfig
import com.engine.nativeai.EngineServiceState
import com.engine.nativeai.FileSearchTool
import com.engine.nativeai.FinalAnswerTool
import com.engine.nativeai.GenerationConfig
import com.engine.nativeai.ExecutionManager
import com.engine.nativeai.ExecutionPolicy
import com.engine.nativeai.FrameJankMonitor
import com.engine.nativeai.ImportResult
import com.engine.nativeai.LocalFallbackProvider
import com.engine.nativeai.LocalModelImporter
import com.engine.nativeai.LocalModelLibrary
import com.engine.nativeai.LocalModelProvider
import com.engine.nativeai.MemoryDatabase
import com.engine.nativeai.MemoryPlanner
import com.engine.nativeai.ModelDownloader
import com.engine.nativeai.DownloadResult
import com.engine.nativeai.MemorySearchTool
import com.engine.nativeai.ModelCatalog
import com.engine.nativeai.ModelAvailability
import com.engine.nativeai.ModelCostTier
import com.engine.nativeai.ModelDiscoveryService
import com.engine.nativeai.ModelKind
import com.engine.nativeai.ModelDescriptor
import com.engine.nativeai.ModelStatus
import com.engine.nativeai.ModelInfoTool
import com.engine.nativeai.ModelRequest
import com.engine.nativeai.ModelRegistry
import com.engine.nativeai.ModelPreferencesStore
import com.engine.nativeai.ModelRouter
import com.engine.nativeai.ModelStreamEvent
import com.engine.nativeai.OpenAICompatibleProvider
import com.engine.nativeai.PrivacyMode
import com.engine.nativeai.ProviderRegistry
import com.engine.nativeai.RemoteProviderBootstrap
import com.engine.nativeai.NativeEngine
import com.engine.nativeai.RoutingMode
import com.engine.nativeai.RuntimeDiagnostics
import com.engine.nativeai.RuntimeMetrics
import com.engine.nativeai.DiagnosticsSnapshot
import com.engine.nativeai.SystemInfoTool
import com.engine.nativeai.TaskType
import com.engine.nativeai.TerminalTool
import com.engine.nativeai.TermuxStatus
import com.engine.nativeai.ThinkingAgent
import com.engine.nativeai.Toolbox
import com.engine.nativeai.ToolPermission
import com.engine.nativeai.ToolRegistry
import com.engine.nativeai.WebSearchTool
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * OxygenOS Model Hub + Live Agent Trace dashboard (blueprint Phase 6).
 * Plain Compose, no external UI framework; design tokens from Theme.kt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineScreen(
    engine: NativeEngine,
    registry: ModelRegistry,
    providerRegistry: ProviderRegistry,
    prefs: ModelPreferencesStore,
    discovery: ModelDiscoveryService,
    localLibrary: LocalModelLibrary,
    initialPrompt: String?,
    onOpenMemory: () -> Unit = {},
    onOpenSources: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadTarget = remember { File(context.filesDir, "models/model.gguf") }
    val modelsDir = remember { File(context.filesDir, "models") }
    var localModels by remember { mutableStateOf(localLibrary.scan()) }
    val runtimeMetrics = remember { RuntimeMetrics() }
    val diagnostics = remember { RuntimeDiagnostics(runtimeMetrics, engine) }
    val jankMonitor = remember { FrameJankMonitor(runtimeMetrics) }

    var loaded by remember { mutableStateOf(false) }
    var loadedPath by remember { mutableStateOf<String?>(null) }
    var status by remember {
        mutableStateOf(
            if (localModels.isEmpty()) {
                "No GGUF model found \u2014 use Download GGUF or \u201c+ Pick GGUF from storage\u201d"
            } else {
                "Local library ready: ${localModels.joinToString { it.file.name }}"
            },
        )
    }
    var prompt by remember { mutableStateOf(initialPrompt ?: "") }
    var output by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var traceExpanded by remember { mutableStateOf(true) }
    val serviceState by EngineForegroundService.state.collectAsState()
    val serviceRunning = serviceState != EngineServiceState.STOPPED
    var engineState by remember { mutableStateOf(EngineUiState.READY) }
    var selectedMode by remember { mutableStateOf(modeIndexFor(prefs.routingMode)) }
    var selectedModelId by remember {
        val persisted = prefs.lastSelectedModelId
        val defaultLocal = localModels.firstOrNull()?.id ?: LocalModelProvider.LOCAL_MODEL_ID
        mutableStateOf(
            if (persisted != null && registry.list().any { it.id == persisted }) persisted
            else defaultLocal,
        )
    }
    var favorites by remember { mutableStateOf(prefs.favorites()) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var runJob by remember { mutableStateOf<Job?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var showKeyDialog by remember { mutableStateOf(false) }
    var keyProviderId by remember { mutableStateOf<String?>(null) }
    var keyInput by remember { mutableStateOf("") }
    var threads by remember { mutableStateOf(4) }
    var contextSize by remember { mutableStateOf(2048) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadStatus by remember { mutableStateOf("") }
    var healthStatus by remember { mutableStateOf("") }
    var healthRunning by remember { mutableStateOf(false) }
    var lastHealthMs by remember { mutableStateOf(0L) }
    val executionManager = remember { ExecutionManager(context) }
    // One construction path for tools + sources (core-hardening C1): the
    // sheet inventory, the agent and the startup refresh share this instance.
    val toolbox = remember { Toolbox(context, engine, registry, prefs, executionManager) }
    var termuxStatus by remember { mutableStateOf(executionManager.status()) }
    var termuxReason by remember { mutableStateOf(executionManager.statusReason()) }
    var terminalEnabled by remember { mutableStateOf(prefs.terminalEnabled) }
    var allowlistText by remember {
        mutableStateOf(prefs.terminalAllowlist.joinToString(", "))
    }
    var probingTermux by remember { mutableStateOf(false) }
    var systemPromptText by remember { mutableStateOf(prefs.systemPromptOverride ?: "") }
    val downloadCancel = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    var importing by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf("") }
    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticsSnapshot by remember { mutableStateOf<DiagnosticsSnapshot?>(null) }
    val importer = remember { LocalModelImporter(modelsDir) }
    val pickLocalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                importStatus = "copying\u2026"
                val result = importer.import(
                    resolver = context.contentResolver,
                    uri = uri,
                    onProgress = { done, total ->
                        importStatus = "copying ${done / (1024 * 1024)} MB" +
                            (total?.let { " / ${it / (1024 * 1024)} MB" } ?: "")
                    },
                )
                importing = false
                when (result) {
                    is ImportResult.Success -> {
                        localModels = localLibrary.scan()
                        localLibrary.syncInto(
                            registry,
                            engine,
                            context.applicationInfo.nativeLibraryDir,
                            contextSize,
                        )
                        val entry = localModels.firstOrNull { it.file == result.file }
                        selectedModelId = entry?.id ?: LocalModelProvider.LOCAL_MODEL_ID
                        prefs.lastSelectedModelId = selectedModelId
                        status = "Imported ${result.file.name} \u2014 tap Load Model"
                    }
                    is ImportResult.Error -> {
                        importStatus = "failed: ${result.message}"
                        status = "import failed: ${result.message}"
                    }
                }
            }
        }
    }

    // Startup source refresh (roadmap Phase 8): bounded, interruptible,
    // only due sources, only when online. Never blocks the UI.
    LaunchedEffect(Unit) {
        if (hasNetwork(context)) {
            withContext(Dispatchers.IO) { toolbox.sourceUpdater.updateOnce() }
        }
    }

    DisposableEffect(Unit) {
        jankMonitor.start()
        onDispose {
            jankMonitor.stop()
            jankMonitor.flush()
        }
    }

    LaunchedEffect(Unit) {
        // Refresh the catalog once on first launch (cached metadata is used
        // afterwards; manual Refresh is always available in the picker).
        if (registry.lastRefreshMs == 0L) {
            val r = discovery.refresh()
            // Newly discovered descriptors need live providers before the
            // router (and the persisted selection) can use them.
            RemoteProviderBootstrap.registerRemoteProviders(registry, providerRegistry)
            if (r.error != null) {
                status = "catalog: cached seeds (${r.error})"
            }
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as android.app.Activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100,
            )
        }
    }

    // Auto health: one live check (60s cache) for the active remote model so
    // the card never shows a stale/static quota state (C3).
    LaunchedEffect(selectedModelId) {
        val d = registry.list().firstOrNull { it.id == selectedModelId }
            ?: return@LaunchedEffect
        if (d.kind != ModelKind.REMOTE) {
            healthStatus = ""
            return@LaunchedEffect
        }
        val provider = registry.provider(d.id) ?: return@LaunchedEffect
        if (System.currentTimeMillis() - lastHealthMs < 60_000L) return@LaunchedEffect
        healthRunning = true
        healthStatus = "checking\u2026"
        healthStatus = try {
            val h = provider.health()
            lastHealthMs = System.currentTimeMillis()
            val state = if (h.available) "available" else "unavailable"
            "\u25cf $state \u00b7 ${h.latencyMs}ms \u00b7 ${h.detail}"
        } catch (e: Exception) {
            "health check failed: ${e.message}"
        }
        healthRunning = false
    }

    val modes = listOf("AUTO", "FREE", "LOCAL", "OFFLINE")
    val routingModes = listOf(
        RoutingMode.HYBRID, RoutingMode.FREE_ONLY, RoutingMode.LOCAL_FIRST, RoutingMode.OFFLINE_ONLY,
    )
    val models = registry.list()
    val selected = models.firstOrNull { it.id == selectedModelId }
    val selectedLocalFile: File? = selected?.takeIf { it.kind == ModelKind.LOCAL }
        ?.let { localLibrary.resolve(it.id) }
    val (connText, connColor) = when {
        selected == null -> "select a model" to OpAmber
        selected.kind == ModelKind.LOCAL ->
            if (selectedLocalFile != null) "Available" to Color(0xFF2ECC71)
            else "No model file" to OpRed
        else ->
            if (providerRegistry.apiKey(selected.provider).isNotBlank()) "Connected" to Color(0xFF2ECC71)
            else "Free \u00b7 anonymous (rate-limited)" to Color(0xFFF5A623)
    }
    // Real engine state, driven only by actual operations (never faked).
    val headerState = if (routingModes[selectedMode] == RoutingMode.OFFLINE_ONLY &&
        engineState == EngineUiState.READY
    ) EngineUiState.OFFLINE else engineState

    // Loads the local GGUF on demand so Agent/Generate work with one tap.
    suspend fun ensureLocalLoaded(file: File? = selectedLocalFile): Boolean {
        val target = file
        if (target == null) {
            engineState = EngineUiState.ERROR
            status = "Model not found:\n${modelsDir.absolutePath}\nPick or download a GGUF, then retry."
            return false
        }
        if (loaded && loadedPath == target.absolutePath) return true
        // Dynamic 1.5 GB budget: pre-flight before loading (never crash).
        val plan = MemoryPlanner.plan(target.length(), contextSize, availableRamBytes(context))
        if (!plan.withinBudget) {
            engineState = EngineUiState.ERROR
            status = "MODEL MAY EXCEED AVAILABLE MEMORY \u2014 estimated " +
                "${plan.totalMb.toInt()} MB vs cap ${plan.availableCapMb.toInt()} MB; " +
                "drop context to ${plan.maxSafeNctx} or use a smaller GGUF"
            return false
        }
        engineState = EngineUiState.LOADING
        status = "loading model (threads=$threads)\u2026"
        return try {
            if (loaded) engine.close()
            val t0 = System.currentTimeMillis()
            engine.init(
                EngineConfig(
                    target.absolutePath,
                    threads = threads,
                    contextSize = contextSize,
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                ),
            )
            runtimeMetrics.recordModelLoad(System.currentTimeMillis() - t0)
            loaded = true
            loadedPath = target.absolutePath
            engineState = EngineUiState.COMPLETED
            status = "Model loaded: ${target.name} (threads=$threads)"
            true
        } catch (e: Exception) {
            loadedPath = null
            engineState = EngineUiState.ERROR
            status = "init failed: ${e.message}"
            false
        }
    }

    // Quick completion with the selected model (one tap / IME Send).
    fun sendQuick() {
        if (prompt.isBlank() || running) return
        runJob = scope.launch {
            try {
                val effectiveMode = if (prefs.privacyMode == PrivacyMode.LOCAL_ONLY) {
                    RoutingMode.OFFLINE_ONLY
                } else {
                    routingModes[selectedMode]
                }
                val descriptor = ModelRouter(effectiveMode).route(
                    AgentTask(
                        prompt = prompt,
                        taskType = TaskType.CHAT,
                        contextLength = contextSize,
                        networkAvailable = hasNetwork(context),
                    ),
                    registry,
                    preferredId = selectedModelId,
                )
                if (descriptor == null) {
                    status = "no model available for quick completion"
                    engineState = EngineUiState.ERROR
                    return@launch
                }
                if (descriptor.kind == ModelKind.LOCAL &&
                    !ensureLocalLoaded(localLibrary.resolve(descriptor.id))
                ) {
                    return@launch
                }
                val provider = registry.providerFor(descriptor)
                if (provider == null) {
                    status = "provider not ready: ${descriptor.id}"
                    engineState = EngineUiState.ERROR
                    return@launch
                }
                running = true
                output = ""
                answer = ""
                engineState = EngineUiState.THINKING
                status = "generating via ${descriptor.id}\u2026"
                val sb = StringBuilder()
                provider.stream(
                    ModelRequest(
                        prompt = prompt,
                        system = prefs.systemPromptOverride ?: "",
                        maxTokens = 64,
                    ),
                ).collect { ev ->
                    when (ev) {
                        is ModelStreamEvent.Token -> {
                            sb.append(ev.text)
                            answer = sb.toString()
                        }
                        is ModelStreamEvent.Reasoning -> Unit
                        is ModelStreamEvent.Done -> Unit
                        is ModelStreamEvent.Error ->
                            throw IllegalStateException(ev.message)
                    }
                }
                status = "generation complete (${sb.length} chars)"
                engineState = EngineUiState.COMPLETED
            } catch (e: kotlinx.coroutines.CancellationException) {
                status = "generation stopped"
                engineState = EngineUiState.READY
            } catch (e: Exception) {
                status = "generate failed: ${e.message}"
                engineState = EngineUiState.ERROR
            } finally {
                running = false
                runJob = null
            }
        }
    }

    // Stop aborts the running agent/generation and records it in the trace.
    fun stopRun() {
        if (runJob == null) {
            engine.cancel()
            return
        }
        // Native llama.cpp decode is a blocking call that coroutine
        // cancellation alone cannot interrupt. Request native cancellation
        // first, then cancel the job. Keep `running` true until the job
        // unwinds in `finally` so a new run cannot start while the native
        // engine is still decoding the previous turn (prevents overlapping
        // generations on one context, which corrupted the graph and crashed
        // in ggml_compute_forward_rope).
        engine.cancel()
        runJob?.cancel()
        status = "agent stopped"
        if (output.isNotBlank()) {
            output = output.trimEnd('\n') + "\n[STOPPED]"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OpBg)
            .padding(16.dp),
    ) {
        // ---------------- HEADER (real engine state) ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NEVER SETTLE", color = OpText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Native Agentic AI \u00b7 OnePlus 7", color = OpTextSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                when (headerState) {
                                    EngineUiState.COMPLETED -> OpSuccess
                                    EngineUiState.ERROR -> OpRed
                                    EngineUiState.OFFLINE -> OpTextSecondary
                                    else -> OpAmber
                                },
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(headerState.label, color = OpText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("OP7", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("SD855 \u00b7 8 GB", color = OpTextSecondary, fontSize = 9.sp)
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                onClick = onOpenMemory,
                shape = RoundedCornerShape(8.dp),
                color = OpCard,
                border = BorderStroke(1.dp, OpBorder),
            ) {
                Text(
                    "MEMORY",
                    color = OpTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Surface(
                onClick = onOpenSources,
                shape = RoundedCornerShape(8.dp),
                color = OpCard,
                border = BorderStroke(1.dp, OpBorder),
            ) {
                Text(
                    "SOURCES",
                    color = OpTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // ---------------- ACTIVE MODEL CHIP (opens settings sheet) ----------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(OpCard, RoundedCornerShape(16.dp))
                .border(BorderStroke(1.dp, OpBorder), RoundedCornerShape(16.dp))
                .clickable { showSettingsSheet = true }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                if (selected == null) "Model \u00b7 none selected" else "Model \u00b7 ${selected.displayName}",
                color = OpText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (selected == null) "tap to choose" else activeModelSummary(
                    selected,
                    selectedLocalFile != null,
                    providerRegistry.apiKey(selected.provider).isNotBlank(),
                ),
                color = OpTextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text("\u2699", color = OpTextSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(status, color = OpTextSecondary, fontSize = 12.sp)
        if (importing) {
            Text(importStatus, color = OpAmber, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- PROMPT (primary interaction) ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("What do you want me to do?", color = OpTextSecondary) },
                minLines = 2,
                maxLines = 6,
                isError = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendQuick() }),
                trailingIcon = {
                    if (prompt.isNotEmpty()) {
                        Text(
                            "\u00d7",
                            color = OpTextSecondary,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .clickable { prompt = "" }
                                .padding(8.dp),
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OpRed,
                    unfocusedBorderColor = OpBorder,
                    errorBorderColor = OpRed,
                    cursorColor = OpRed,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { sendQuick() },
                enabled = prompt.isNotBlank() && !running,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OpCard,
                    contentColor = OpText,
                    disabledContainerColor = OpCard.copy(alpha = 0.4f),
                    disabledContentColor = OpTextSecondary,
                ),
                border = BorderStroke(1.dp, OpBorder),
                modifier = Modifier
                    .height(56.dp)
                    .semantics { contentDescription = "Send prompt" },
            ) {
                Text("Send", fontWeight = FontWeight.Bold)
            }
        }
        Text(
            "\u2191 Send \u00b7 quick completion with selected model \u2014 Agent runs the full tool loop",
            color = OpTextSecondary,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp, start = 2.dp),
        )
        Spacer(Modifier.height(12.dp))

        // ---------------- PRIMARY ACTIONS ----------------
        Row(Modifier.fillMaxWidth()) {
            PillButton(
                if (running) "Agent \u00b7 RUNNING" else "Agent",
                Modifier.weight(1f),
                primary = true,
                enabled = !running,
            ) {
                scope.launch {
                    if (selected != null && selected.kind == ModelKind.LOCAL && !ensureLocalLoaded()) return@launch
                    runAgent(
                        context = context,
                        registry = registry,
                        prefs = prefs,
                        toolbox = toolbox,
                        prompt = prompt,
                        mode = routingModes[selectedMode],
                        modeLabel = modes[selectedMode],
                        preferredId = selectedModelId,
                        loaded = loaded,
                        metrics = runtimeMetrics,
                        setRunning = { running = it },
                        setStatus = { status = it },
                        setOutput = { output = it },
                        setAnswer = { answer = it },
                        setRunJob = { runJob = it },
                        setEngineState = { engineState = it },
                        scope = scope,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (running) {
                PillButton("Stop", Modifier.weight(1f), enabled = true) {
                    stopRun()
                }
            } else {
                PillButton(
                    "Clear",
                    Modifier.weight(1f),
                    enabled = prompt.isNotBlank() || answer.isNotBlank() || output.isNotBlank(),
                ) {
                    prompt = ""
                    answer = ""
                    output = ""
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- LOG ZONE: AGENT TRACE ----------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { traceExpanded = !traceExpanded },
        ) {
            Text("AGENT TRACE", color = OpText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(if (traceExpanded) "\u25be" else "\u25b8", color = OpTextSecondary, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (traceExpanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(OpCard, RoundedCornerShape(12.dp)),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                ) {
                    if (output.isBlank() && answer.isBlank()) {
                        Text("Trace will appear here\u2026", color = OpTextSecondary, fontSize = 13.sp)
                    } else {
                        output.lineSequence().forEach { line -> TraceLine(line) }
                        if (answer.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(OpBg, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                            ) {
                                SelectionContainer {
                                    Text(
                                        answer,
                                        color = OpText,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        HorizonLight(active = running)
    }

    // ---------------- ENGINE SETTINGS (modal bottom sheet) ----------------
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = OpCard,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text("ENGINE SETTINGS", color = OpText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Active execution view stays clean \u2014 tune hardware here.", color = OpTextSecondary, fontSize = 11.sp)
                Spacer(Modifier.height(14.dp))

                Text("MODEL TIER", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(OpBg, RoundedCornerShape(24.dp))
                        .padding(4.dp),
                ) {
                    modes.forEachIndexed { i, label ->
                        ValuePill(label, selected = i == selectedMode, modifier = Modifier.weight(1f)) {
                            selectedMode = i
                            prefs.routingMode = routingModes[i]
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                Text("ACTIVE MODEL", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                models.filter { it.id == selectedModelId }.forEach {
                    ModelCard(
                        d = it,
                        connectionText = connText,
                        connectionColor = connColor,
                        localLoaded = loaded,
                        modelFileExists = selectedLocalFile != null,
                        modelFileSizeMb = selectedLocalFile?.let { f -> f.length() / (1024L * 1024L) },
                        networkAvailable = hasNetwork(context),
                        quantTag = selectedLocalFile?.let { f -> ModelStatus.quantTag(f.name) },
                        onLoadLocal = {
                            scope.launch {
                                engineState = EngineUiState.LOADING
                                status = "loading model\u2026"
                                if (ensureLocalLoaded()) {
                                    engineState = EngineUiState.COMPLETED
                                }
                            }
                        },
                        onChange = {
                            showSettingsSheet = false
                            showPicker = true
                        },
                        onDeleteLocal = if (it.kind == ModelKind.LOCAL) {
                            {
                                scope.launch {
                                    if (localLibrary.delete(it.id)) {
                                        localModels = localLibrary.scan()
                                        localLibrary.syncInto(
                                            registry,
                                            engine,
                                            context.applicationInfo.nativeLibraryDir,
                                            contextSize,
                                        )
                                        if (selectedModelId == it.id) {
                                            selectedModelId = localModels.firstOrNull()?.id
                                                ?: LocalModelProvider.LOCAL_MODEL_ID
                                            prefs.lastSelectedModelId = selectedModelId
                                        }
                                        status = "Deleted ${it.displayName}"
                                    }
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                if (selected != null && selected.kind == ModelKind.REMOTE &&
                    registry.provider(selected.id) != null
                ) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillButton(
                            if (healthRunning) "Checking\u2026" else "Check health",
                            modifier = Modifier.weight(1f),
                            enabled = !healthRunning,
                        ) {
                            scope.launch {
                                healthRunning = true
                                healthStatus = "checking\u2026"
                                healthStatus = try {
                                    val h = registry.provider(selected.id)!!.health()
                                    val state = if (h.available) "available" else "unavailable"
                                    "\u25cf $state \u00b7 ${h.latencyMs}ms \u00b7 ${h.detail}"
                                } catch (e: Exception) {
                                    "health check failed: ${e.message}"
                                }
                                healthRunning = false
                            }
                        }
                    }
                    if (healthStatus.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(healthStatus, color = OpTextSecondary, fontSize = 10.sp)
                    }
                }
                if (registry.lastRefreshMs > 0) {
                    Text(
                        "Catalog updated: ${formatCatalogTime(registry.lastRefreshMs)} \u00b7 ${models.size} models",
                        color = OpTextSecondary,
                        fontSize = 10.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))

                Spacer(Modifier.height(12.dp))

                Text("SYSTEM PROMPT", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = systemPromptText,
                    onValueChange = {
                        systemPromptText = it
                        prefs.systemPromptOverride = it.ifBlank { null }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text("Optional persona/rules override (blank = default)", color = OpTextSecondary)
                    },
                    minLines = 2,
                    maxLines = 5,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OpRed,
                        unfocusedBorderColor = OpBorder,
                        errorBorderColor = OpRed,
                        cursorColor = OpRed,
                    ),
                )

                Text("TOOLS", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Terminal",
                        color = OpText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        when (termuxStatus) {
                            TermuxStatus.READY -> "\u25cf READY"
                            TermuxStatus.SETUP_REQUIRED -> "\u25cf SETUP REQUIRED"
                            TermuxStatus.NOT_INSTALLED -> "\u25cf NOT INSTALLED"
                            TermuxStatus.INSTALLED -> "\u25cf INSTALLED"
                            TermuxStatus.ERROR -> "\u25cf ERROR"
                        },
                        color = when (termuxStatus) {
                            TermuxStatus.READY -> OpSuccess
                            TermuxStatus.SETUP_REQUIRED -> OpAmber
                            TermuxStatus.ERROR -> OpRed
                            else -> OpTextSecondary
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (termuxReason.isNotBlank()) {
                    Text(
                        termuxReason,
                        color = OpTextSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    PillButton(
                        if (terminalEnabled) "Terminal ON" else "Terminal OFF",
                        Modifier.weight(1f),
                    ) {
                        val next = !terminalEnabled
                        if (next && Build.VERSION.SDK_INT <= 32 &&
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.READ_EXTERNAL_STORAGE,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            ActivityCompat.requestPermissions(
                                context as android.app.Activity,
                                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                                101,
                            )
                        }
                        terminalEnabled = next
                        prefs.terminalEnabled = next
                    }
                    Spacer(Modifier.width(8.dp))
                    PillButton(
                        if (probingTermux) "Probing\u2026" else "Test connection",
                        Modifier.weight(1f),
                        enabled = !probingTermux,
                    ) {
                        scope.launch {
                            probingTermux = true
                            termuxStatus = executionManager.probe()
                            termuxReason = executionManager.statusReason()
                            probingTermux = false
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = allowlistText,
                    onValueChange = {
                        allowlistText = it
                        prefs.terminalAllowlist = it.split(",")
                            .map { x -> x.trim() }
                            .filter { y -> y.isNotEmpty() }
                            .toSet()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "allowed commands, comma-separated (empty = deny all)",
                            color = OpTextSecondary,
                        )
                    },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OpRed,
                        unfocusedBorderColor = OpBorder,
                        errorBorderColor = OpRed,
                        cursorColor = OpRed,
                    ),
                )

                Spacer(Modifier.height(10.dp))
                Text("TOOL INVENTORY", color = OpTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                toolbox.tools.snapshot().forEach { td ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Text(
                            td.name,
                            color = OpText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            when {
                                !td.enabled -> "DISABLED"
                                !td.available -> "UNAVAILABLE"
                                td.permission == ToolPermission.REQUIRES_APPROVAL -> "APPROVAL"
                                td.permission == ToolPermission.PRIVILEGED -> "PRIVILEGED"
                                else -> "AVAILABLE"
                            },
                            color = if (td.enabled && td.available) OpSuccess else OpTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Text("HARDWARE TUNING", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("CPU THREADS", color = OpTextSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(2, 3, 4, 5, 6).forEach { n ->
                        ValuePill("$n", selected = threads == n, modifier = Modifier.padding(end = 8.dp)) {
                            threads = n
                        }
                    }
                }
                Text("Recommended: 4 \u00b7 Snapdragon 855 (Kryo 485)", color = OpTextSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Text("CONTEXT", color = OpTextSecondary, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(512, 1024, 2048).forEach { n ->
                        ValuePill("$n", selected = contextSize == n, modifier = Modifier.padding(end = 8.dp)) {
                            contextSize = n
                        }
                    }
                }
                val dynPlan = MemoryPlanner.plan(selectedLocalFile?.length() ?: 0L, contextSize, availableRamBytes(context))
                Text(
                    "Dynamic budget: est. ${dynPlan.totalMb.toInt()} MB \u00b7 cap ${dynPlan.availableCapMb.toInt()} MB \u00b7 max ctx ${dynPlan.maxSafeNctx}",
                    color = if (dynPlan.withinBudget) OpTextSecondary else OpAmber,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(14.dp))

                Row(Modifier.fillMaxWidth()) {
                    PillButton(
                        if (serviceRunning) "STOP SERVICE" else "Start service",
                        Modifier.weight(1f),
                        enabled = !running,
                    ) {
                        try {
                            if (serviceRunning) {
                                EngineForegroundService.stop(context)
                            } else {
                                EngineForegroundService.start(context)
                            }
                        } catch (e: Exception) {
                            status = "service failed: ${e.message}"
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Download GGUF model", Modifier.weight(1f), enabled = !running && !downloading) {
                        downloadStatus = ""
                        downloadProgress = null
                        showSettingsSheet = false
                        showDownloadDialog = true
                    }
                }
                Spacer(Modifier.height(8.dp))
                PillButton("Stats", Modifier.fillMaxWidth(), enabled = !running) {
                    scope.launch {
                        jankMonitor.flush()
                        diagnosticsSnapshot = diagnostics.snapshot()
                        showDiagnostics = true
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Service keeps the engine alive in the background \u00b7 Download fetches a GGUF to ${downloadTarget.name}",
                    color = OpTextSecondary,
                    fontSize = 10.sp,
                )
            }
        }
    }

    // ---------------- DIALOGS (overlays) ----------------
    if (showPicker) {
        ModelPickerDialog(
            models = models,
            selectedId = selectedModelId,
            lastUpdated = registry.lastRefreshMs,
            favorites = favorites,
            localEntries = localModels,
            onPickLocal = {
                pickLocalLauncher.launch(arrayOf("*/*"))
            },
            onSelect = { d ->
                selectedModelId = d.id
                prefs.lastSelectedModelId = d.id
                if (d.kind == ModelKind.REMOTE) {
                    ensureRemoteProvider(registry, providerRegistry, prefs, d)
                }
                showPicker = false
            },
            onToggleFavorite = { id ->
                favorites = prefs.toggleFavorite(id)
            },
            onRefresh = {
                scope.launch {
                    val r = discovery.refresh()
                    RemoteProviderBootstrap.registerRemoteProviders(registry, providerRegistry)
                    status = if (r.error == null) {
                        "discovery: ${r.found} new models, ${registry.list().size} total (${r.endpoint})"
                    } else {
                        "discovery failed: ${r.error} (using cached catalog)"
                    }
                }
            },
            onConfigure = { d ->
                keyProviderId = d.provider
                keyInput = providerRegistry.apiKey(d.provider)
                showPicker = false
                showKeyDialog = true
            },
            onDismiss = { showPicker = false },
        )
    }

    if (showKeyDialog && keyProviderId != null) {
        ApiKeyDialog(
            provider = keyProviderId!!,
            initial = keyInput,
            onSave = { key ->
                providerRegistry.setApiKey(keyProviderId!!, key.trim())
                models.firstOrNull { it.provider == keyProviderId }?.let { d ->
                    registry.remove(d.id)
                    ensureRemoteProvider(registry, providerRegistry, prefs, d)
                }
                showKeyDialog = false
                status = "API key set for ${keyProviderId} (memory only, never persisted)"
            },
            onClear = {
                providerRegistry.clearApiKey(keyProviderId!!)
                models.firstOrNull { it.provider == keyProviderId }?.let { d ->
                    registry.remove(d.id)
                    ensureRemoteProvider(registry, providerRegistry, prefs, d)
                }
                showKeyDialog = false
                status = "API key cleared for ${keyProviderId}"
            },
            onDismiss = { showKeyDialog = false },
        )
    }

    if (showDownloadDialog) {
        ModelDownloadDialog(
            downloading = downloading,
            progress = downloadProgress,
            status = downloadStatus,
            onPick = { url ->
                downloadStatus = ""
                downloadProgress = null
            },
            onDownload = { url ->
                scope.launch {
                    downloading = true
                    downloadProgress = null
                    downloadStatus = "connecting\u2026"
                    val result = ModelDownloader(downloadTarget).download(
                        url = url,
                        onProgress = { done, total ->
                            downloadProgress = if (total != null && total > 0) {
                                (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                null
                            }
                            downloadStatus = "downloaded ${done / (1024 * 1024)} MB" +
                                (total?.let { " / ${it / (1024 * 1024)} MB" } ?: "")
                        },
                        cancelled = downloadCancel,
                    )
                    downloading = false
                    when (result) {
                        is DownloadResult.Success -> {
                            downloadProgress = 1f
                            localModels = localLibrary.scan()
                            localLibrary.syncInto(
                                registry,
                                engine,
                                context.applicationInfo.nativeLibraryDir,
                                contextSize,
                            )
                            selectedModelId = LocalModelProvider.LOCAL_MODEL_ID
                            prefs.lastSelectedModelId = selectedModelId
                            downloadStatus = "saved ${downloadTarget.name} (${result.bytes / (1024 * 1024)} MB) \u2014 tap Load Model"
                            status = "Model downloaded: ${downloadTarget.name} \u2014 tap Load Model"
                        }
                        is DownloadResult.Error -> {
                            downloadStatus = "failed: ${result.message}"
                            status = "download failed: ${result.message}"
                        }
                    }
                }
            },
            onCancel = { downloadCancel.set(true) },
            onDismiss = {
                if (!downloading) showDownloadDialog = false
            },
        )
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            snapshot = diagnosticsSnapshot,
            serviceState = serviceState.name,
            onClear = {
                runtimeMetrics.reset()
                diagnosticsSnapshot = null
                status = "runtime metrics cleared"
            },
            onDismiss = { showDiagnostics = false },
        )
    }
}

/** Diagnostics panel: real measured values, never guessed (spec §16/§21). */
@Composable
private fun DiagnosticsDialog(
    snapshot: DiagnosticsSnapshot?,
    serviceState: String,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val lines = snapshot?.let { snap ->
        val m = snap.metrics
        val toolsLine = if (m.tools.isEmpty()) {
            "none"
        } else {
            m.tools.entries.joinToString(" \u00b7 ") { (tool, tm) ->
                val fail = if (tm.failures > 0) ", ${tm.failures} fail" else ""
                "$tool \u00d7${tm.calls} (${tm.totalMs} ms$fail)"
            }
        }
        val tps = m.tokensPerSec?.let { "%.1f tok/s".format(it) } ?: "n/a"
        val rssLine = "RSS          ${snap.rssBytes / (1024 * 1024)} MB / " +
            "${snap.ceilingBytes / (1024 * 1024)} MB" +
            (if (snap.overLimit) " OVER LIMIT" else "")
        val engineLine = "ENGINE       " +
            (if (snap.engineLoaded) (snap.backend ?: "loaded") else "not loaded")
        listOf(
            "MODEL LOAD   ${m.modelLoadMs?.let { "$it ms" } ?: "n/a"}",
            "FIRST TOKEN  ${m.firstTokenMs?.let { "$it ms" } ?: "n/a"}",
            "LAST RUN     ${m.lastRunTokens} tok \u00b7 ${m.lastRunDurationMs} ms \u00b7 $tps",
            "TOOLS        $toolsLine",
            "ERRORS       ${m.errors} \u00b7 RETRIES ${m.retries}",
            "RESTARTS     ${m.serviceRestarts}",
            "JANK         ${m.droppedFrames} dropped \u00b7 ${m.jankyFrames} janky",
            rssLine,
            engineLine,
            "SERVICE      $serviceState",
        )
    } ?: listOf("collecting\u2026")
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpBg,
        title = { Text("DIAGNOSTICS", color = OpText, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                lines.forEach { line ->
                    Text(
                        line,
                        color = OpText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
                if (snapshot == null) {
                    Spacer(Modifier.height(4.dp))
                    Text("Run a task or load a model first.", color = OpTextSecondary, fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onClear, colors = ButtonDefaults.buttonColors(containerColor = OpCard)) {
                Text("Clear")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Close") }
        },
    )
}

/** One line of the agent step log: steps get a colored dot, metadata is
 *  indented, and raw text stays left-aligned. */
@Composable
private fun TraceLine(line: String) {
    val raw = line.trim()
    // Structured trace lines carry an "HH:mm:ss " prefix; strip it before
    // classifying so step colors keep working.
    val t = if (raw.length >= 9 && raw[2] == ':' && raw[5] == ':') {
        raw.substring(9).trim()
    } else {
        raw
    }
    when {
        t.startsWith("[ERROR]") -> StepRow(t, OpRed)
        t.startsWith("[VERIFY]") -> StepRow(t, OpSuccess)
        t.startsWith("[TOOL]") -> StepRow(t, OpBlue)
        t.startsWith("[OBS]") -> Text(
            t, color = OpTextSecondary, fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
        )
        t.startsWith("[") && t.endsWith("]") -> StepRow(t, OpAmber)
        t.startsWith("MODEL ") || t.startsWith("PROVIDER ") ||
            t.startsWith("MODE ") || t.startsWith("STATUS ") -> Text(
            t, color = OpTextSecondary, fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp),
        )
        t.isBlank() -> Spacer(Modifier.height(2.dp))
        else -> Text(
            t, color = OpText, fontSize = 12.sp,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp),
        )
    }
}

@Composable
private fun StepRow(text: String, color: Color) {
    Row(
        Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = OpText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Engine console state shown in the header, driven by real operations. */
private enum class EngineUiState(val label: String) {
    READY("READY"),
    LOADING("LOADING MODEL"),
    THINKING("THINKING"),
    TOOL("EXECUTING TOOL"),
    VERIFYING("VERIFYING"),
    COMPLETED("COMPLETED"),
    ERROR("ERROR"),
    OFFLINE("OFFLINE"),
}

private fun runAgent(
    context: Context,
    registry: ModelRegistry,
    prefs: ModelPreferencesStore,
    toolbox: Toolbox,
    prompt: String,
    mode: RoutingMode,
    modeLabel: String,
    preferredId: String?,
    loaded: Boolean,
    metrics: RuntimeMetrics,
    setRunning: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setOutput: (String) -> Unit,
    setAnswer: (String) -> Unit,
    setRunJob: (Job?) -> Unit,
    setEngineState: (EngineUiState) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    // Privacy: Local Only forces offline routing regardless of the mode.
    val effectiveMode = if (prefs.privacyMode == PrivacyMode.LOCAL_ONLY) {
        RoutingMode.OFFLINE_ONLY
    } else {
        mode
    }
    setRunning(true)
    setOutput("")
    setAnswer("")
    setStatus("agent running\u2026")
    setEngineState(EngineUiState.THINKING)
    setRunJob(scope.launch {
        val memory = toolbox.memory
        val tools = toolbox.tools
        val agent = ThinkingAgent(
            router = ModelRouter(mode = effectiveMode),
            registry = registry,
            memory = memory,
            tools = tools,
            networkAvailable = hasNetwork(context),
            preferredId = preferredId,
            systemPromptOverride = prefs.systemPromptOverride,
        )
        val sessionId = try {
            val id = memory.startSession("agent: ${prompt.take(60)}")
            memory.recordMessage(id, "user", prompt)
            id
        } catch (e: Exception) {
            null // memory failure must never crash the agent
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
            agent.run(prompt, GenerationConfig(maxTokens = 256)).collect { ev ->
                when (ev) {
                    is AgentEvent.Token -> {
                        tokenCount++
                        metrics.recordFirstToken(System.currentTimeMillis() - runStarted)
                        answer.append(ev.text)
                        setAnswer(answer.toString())
                    }
                    is AgentEvent.Stage -> {
                        log("[${ev.state}]")
                        setOutput(steps.toString())
                        setEngineState(
                            when (ev.state) {
                                AgentState.VERIFY -> EngineUiState.VERIFYING
                                AgentState.EXECUTE, AgentState.OBSERVE -> EngineUiState.TOOL
                                AgentState.FINALIZE, AgentState.STORE -> EngineUiState.THINKING
                                else -> EngineUiState.THINKING
                            },
                        )
                    }
                    is AgentEvent.Routed -> {
                        routedCount++
                        if (routedCount > 1) metrics.recordRetry()
                        val remote = ev.provider != "local"
                        log("MODEL ${ev.modelId}")
                        log("PROVIDER ${ev.provider}")
                        log("MODE ${if (remote) "Remote (${modeLabel})" else modeLabel}")
                        log("STATUS ${if (remote) "Running \u00b7 remote request" else "Running \u00b7 local"}")
                        log("[${ev.taskType} | ${ev.costTier}]")
                        setOutput(steps.toString())
                    }
                    is AgentEvent.ToolCall -> {
                        toolName = ev.tool
                        toolStartedMs = System.currentTimeMillis()
                        log("[TOOL] ${ev.tool}(${ev.input})")
                        setOutput(steps.toString())
                        setEngineState(EngineUiState.TOOL)
                    }
                    is AgentEvent.Observation -> {
                        if (toolName.isNotEmpty()) {
                            metrics.recordTool(toolName, System.currentTimeMillis() - toolStartedMs, true)
                            toolName = ""
                        }
                        log("[OBS] ${ev.output.take(240)}")
                        setOutput(steps.toString())
                    }
                    is AgentEvent.Verification -> {
                        if (!ev.passed && toolName.isNotEmpty()) {
                            metrics.recordTool(toolName, 0L, false)
                            toolName = ""
                        }
                        log("[VERIFY] ${ev.tool}: " +
                            if (ev.passed) "passed" else "failed")
                        setOutput(steps.toString())
                        setEngineState(EngineUiState.VERIFYING)
                    }
                    is AgentEvent.Final -> {
                        done = true
                        log("[FINAL]")
                        answer.setLength(0)
                        answer.append(ev.answer)
                        if (sessionId != null) {
                            try {
                                memory.recordMessage(sessionId, "agent", ev.answer)
                            } catch (_: Exception) {
                                // best-effort conversation persistence
                            }
                        }
                        setOutput(steps.toString())
                        setAnswer(answer.toString())
                        setEngineState(EngineUiState.COMPLETED)
                    }
                    is AgentEvent.Error -> {
                        metrics.recordError()
                        log("[ERROR] ${ev.message}")
                        setOutput(steps.toString())
                        setStatus("agent error: ${ev.message}")
                        setEngineState(EngineUiState.ERROR)
                    }
                }
            }
            metrics.recordRun(tokenCount, System.currentTimeMillis() - runStarted)
            setStatus(if (done) "agent done" else "agent ended without final answer")
            if (!done) setEngineState(EngineUiState.READY)
        } catch (e: kotlinx.coroutines.CancellationException) {
            setStatus("agent stopped")
            setEngineState(EngineUiState.READY)
        } catch (e: Exception) {
            setStatus("agent failed: ${e.message}")
            setEngineState(EngineUiState.ERROR)
        } finally {
            if (sessionId != null) {
                try {
                    memory.endSession(sessionId)
                } catch (_: Exception) {
                    // best-effort session close
                }
            }
            setRunning(false)
        }
        setRunJob(null)
    })
}

@Composable
private fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) OpRed else OpCard,
            contentColor = OpText,
            disabledContainerColor = OpCard.copy(alpha = 0.4f),
            disabledContentColor = OpTextSecondary,
        ),
        border = if (primary) null else BorderStroke(1.dp, OpDivider),
    ) {
        Text(text)
    }
}

@Composable
private fun ValuePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = if (selected) OpText else OpTextSecondary,
        fontSize = 12.sp,
        maxLines = 1,
        modifier = modifier
            .background(
                if (selected) OpCard else Color.Transparent,
                RoundedCornerShape(20.dp),
            )
            .border(
                BorderStroke(1.dp, if (selected) OpBorder else OpDivider),
                RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

private fun availableRamBytes(context: Context): Long {
    val am = context.getSystemService(android.app.ActivityManager::class.java)
        ?: return Long.MAX_VALUE
    val info = android.app.ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return info.availMem
}

private fun modeIndexFor(mode: RoutingMode): Int = when (mode) {
    RoutingMode.HYBRID -> 0      // AUTO
    RoutingMode.FREE_FIRST, RoutingMode.FREE_ONLY -> 1 // FREE
    RoutingMode.LOCAL_FIRST -> 2 // LOCAL
    RoutingMode.OFFLINE_ONLY -> 3 // OFFLINE
}

private fun activeModelSummary(d: ModelDescriptor, hasModelFile: Boolean, hasKey: Boolean): String {
    val tier = when (d.costTier) {
        ModelCostTier.FREE -> "FREE"
        ModelCostTier.PAID -> "PAID"
        ModelCostTier.UNKNOWN -> if (d.kind == ModelKind.LOCAL) "LOCAL" else "?"
    }
    val ctx = d.contextLength?.let { "$it ctx" } ?: "? ctx"
    val access = if (d.kind == ModelKind.LOCAL) {
        if (hasModelFile) "gguf" else "no file"
    } else {
        if (hasKey) "key" else "anon"
    }
    return "$tier \u00b7 $ctx \u00b7 $access"
}

private fun formatCatalogTime(epochMs: Long): String {
    val f = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
    return f.format(java.util.Date(epochMs))
}

private fun ensureRemoteProvider(
    registry: ModelRegistry,
    providerRegistry: ProviderRegistry,
    prefs: ModelPreferencesStore,
    d: ModelDescriptor,
) {
    if (registry.provider(d.id) != null) return
    // Key lives in memory only (runtime entry); never persisted, never logged.
    registry.register(
        OpenAICompatibleProvider(
            descriptor = d,
            apiKey = providerRegistry.apiKey(d.provider),
        ),
    )
}

@Composable
private fun ModelCard(
    d: ModelDescriptor,
    connectionText: String,
    connectionColor: Color,
    localLoaded: Boolean,
    modelFileExists: Boolean,
    modelFileSizeMb: Long?,
    networkAvailable: Boolean,
    quantTag: String?,
    onLoadLocal: () -> Unit,
    onChange: () -> Unit,
    onDeleteLocal: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(OpCard, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                d.displayName,
                color = OpText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            ProviderPill(if (d.kind == ModelKind.LOCAL) "LOCAL" else "REMOTE")
            when (d.costTier) {
                ModelCostTier.FREE -> ProviderPill("FREE", color = OpAmber)
                ModelCostTier.PAID -> ProviderPill("PAID", color = OpTextSecondary)
                ModelCostTier.UNKNOWN -> ProviderPill("\u2014", color = OpTextSecondary)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            modelMetadata(d),
            fontSize = 11.sp,
        )
        val statusLine = ModelStatus.line(d, localLoaded, modelFileExists, networkAvailable)
        Text(
            statusLine,
            color = when {
                statusLine.startsWith("READY") || statusLine.startsWith("ONLINE") -> OpSuccess
                statusLine.startsWith("OFFLINE") || statusLine.startsWith("NO MODEL") -> OpAmber
                else -> OpTextSecondary
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 3.dp),
        )
        if (d.kind == ModelKind.LOCAL && modelFileSizeMb != null) {
            Text(
                buildString {
                    append("GGUF \u00b7 ${modelFileSizeMb} MB")
                    if (quantTag != null) append(" \u00b7 $quantTag")
                    append(" on device")
                },
                color = OpTextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "\u25cf $connectionText",
                color = connectionColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                availabilityLabel(d.availability),
                color = if (d.availability == ModelAvailability.UNKNOWN) {
                    OpTextSecondary.copy(alpha = 0.45f)
                } else {
                    OpTextSecondary
                },
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            if (d.kind == ModelKind.LOCAL) {
                when {
                    localLoaded -> Text(
                        "Loaded",
                        color = OpSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                    )
                    modelFileExists -> PillButton("Load", Modifier.weight(1f)) { onLoadLocal() }
                    else -> Text(
                        "No model file \u2014 download or import one",
                        color = OpTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 8.dp),
                    )
                }
                if (d.kind == ModelKind.LOCAL && onDeleteLocal != null) {
                    Text(
                        "Delete",
                        color = OpRed,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(top = 6.dp, start = 4.dp, end = 8.dp)
                            .clickable(onClick = onDeleteLocal),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            PillButton("Change", Modifier.weight(1f)) { onChange() }
        }
    }
}

/** Metadata line: confirmed values in normal text, unfetched values dimmed. */
private fun modelMetadata(d: ModelDescriptor): AnnotatedString {
    val unknown = OpTextSecondary.copy(alpha = 0.45f)
    val confirmed = OpTextSecondary
    fun meta(label: String, text: String, isKnown: Boolean): AnnotatedString {
        val base = AnnotatedString.Builder()
        base.append("$label ")
        base.pushStyle(SpanStyle(color = if (isKnown) confirmed else unknown))
        base.append(text)
        base.pop()
        base.append("  ")
        return base.toAnnotatedString()
    }
    val builder = AnnotatedString.Builder()
    builder.append("${d.provider} \u00b7 ")
    builder.append(meta("ctx", d.contextLength?.toString() ?: "\u2014", d.contextLength != null))
    val toolsKnown = d.kind != ModelKind.REMOTE || d.supportsTools
    builder.append(meta("tools", if (d.supportsTools) "yes" else if (toolsKnown) "no" else "\u2014", toolsKnown))
    val visionKnown = d.kind != ModelKind.REMOTE || d.supportsVision
    builder.append(meta("vision", if (d.supportsVision) "yes" else if (visionKnown) "no" else "\u2014", visionKnown))
    builder.append(meta("coding", d.codingScore?.toString() ?: "\u2014", d.codingScore != null))
    builder.append(meta("reasoning", d.reasoningScore?.toString() ?: "\u2014", d.reasoningScore != null))
    return builder.toAnnotatedString()
}

private fun availabilityLabel(a: ModelAvailability): String = when (a) {
    ModelAvailability.AVAILABLE -> "Available"
    ModelAvailability.LIMITED -> "Temporarily unavailable"
    ModelAvailability.UNAVAILABLE -> "Offline"
    ModelAvailability.UNKNOWN -> "UNKNOWN"
}

@Composable
private fun ProviderPill(text: String, color: Color = OpTextSecondary) {
    Text(
        text = text,
        color = color,
        fontSize = 10.sp,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun HorizonLight(active: Boolean) {
    AnimatedVisibility(visible = active) {
        val pulse = rememberInfiniteTransition(label = "horizon")
        val alpha by pulse.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "alpha",
        )
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(4.dp)
                .alpha(alpha)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient(listOf(OpRed, OpBlue, OpRed))),
        )
    }
}


private fun hasNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
private fun ApiKeyDialog(
    provider: String,
    initial: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API key · $provider", color = OpText) },
        text = {
            Column {
                Text(
                    "Free models work without a key (anonymous, rate-limited). A key raises your quota. " +
                        "Entered keys live only in memory for this session \u2014 never persisted or logged.",
                    color = OpTextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Free OpenCode account for a higher quota: opencode.ai/auth \u2197",
                    color = OpBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://opencode.ai/auth")),
                                )
                            } catch (_: Exception) {
                            }
                        }
                        .padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Bearer token") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OpRed,
                        unfocusedBorderColor = OpBorder,
                        cursorColor = OpRed,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(value) }, enabled = value.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                if (initial.isNotBlank()) {
                    Button(onClick = onClear) { Text("Clear") }
                    Spacer(Modifier.width(8.dp))
                }
                Button(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}


@Composable
private fun ModelDownloadDialog(
    downloading: Boolean,
    progress: Float?,
    status: String,
    onPick: (String) -> Unit,
    onDownload: (String) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(QUICK_MODELS.first().url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download GGUF model", color = OpText) },
        text = {
            Column {
                Text(
                    "Saves to ${"models/model.gguf"}. Quick picks are verified OP7-sized quantized models.",
                    color = OpTextSecondary,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(8.dp))
                QUICK_MODELS.forEach { q ->
                    Text(
                        q.label,
                        color = OpRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clickable { url = q.url; onPick(q.url) }
                            .padding(vertical = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("GGUF URL") },
                    minLines = 2,
                    singleLine = false,
                    enabled = !downloading,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OpRed,
                        unfocusedBorderColor = OpBorder,
                        cursorColor = OpRed,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (downloading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        color = OpRed,
                        trackColor = OpDivider,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(status, color = OpTextSecondary, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            if (downloading) {
                Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = OpCard)) {
                    Text("Cancel")
                }
            } else {
                Button(onClick = { onDownload(url.trim()) }, enabled = url.isNotBlank()) {
                    Text("Download")
                }
            }
        },
        dismissButton = {
            if (!downloading) {
                Button(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

private data class QuickModel(val label: String, val url: String)

private val QUICK_MODELS = listOf(
    QuickModel(
        "Qwen2.5-0.5B Q4_K_M \u00b7 ModelScope (~380 MB)",
        "https://modelscope.cn/models/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/master/qwen2.5-0.5b-instruct-q4_k_m.gguf",
    ),
    QuickModel(
        "Qwen2.5-0.5B Q4_K_M \u00b7 HuggingFace (~380 MB)",
        "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
    ),
    QuickModel(
        "Qwen2.5-1.5B Q4_K_M \u00b7 ModelScope (~1 GB)",
        "https://modelscope.cn/models/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/master/qwen2.5-1.5b-instruct-q4_k_m.gguf",
    ),
)
