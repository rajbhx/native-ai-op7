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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.stateDescription
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
import android.content.ClipData
import android.content.ClipboardManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.engine.nativeai.AgentEvent
import com.engine.nativeai.ApprovalDecision
import com.engine.nativeai.EngineUiState
import com.engine.nativeai.EngineViewModel
import com.engine.nativeai.ToolApprovalRequest
import com.engine.nativeai.AgentState
import com.engine.nativeai.AgentTask
import com.engine.nativeai.CalculatorTool
import com.engine.nativeai.CoreErrors
import com.engine.nativeai.ErrorEntry
import com.engine.nativeai.ChatHistory
import com.engine.nativeai.ChatSession
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
import com.engine.nativeai.GgufMetaCache
import com.engine.nativeai.MemoryPlanner
import com.engine.nativeai.ModelBenchmark
import com.engine.nativeai.ModelIntegrity
import com.engine.nativeai.ModelManifest
import com.engine.nativeai.ModelDownloader
import com.engine.nativeai.DownloadResult
import com.engine.nativeai.SelfLearningPipeline
import com.engine.nativeai.Skill
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
import com.engine.nativeai.MnnBackend
import com.engine.nativeai.RuntimeKind
import com.engine.nativeai.USearchVectorIndex
import com.engine.nativeai.OpenAICompatibleProvider
import com.engine.nativeai.PrivacyMode
import com.engine.nativeai.ProviderRegistry
import com.engine.nativeai.RemoteProviderBootstrap
import com.engine.nativeai.NativeEngine
import com.engine.nativeai.RoutingMode
import com.engine.nativeai.RuntimeDiagnostics
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
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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

    val vm: EngineViewModel = viewModel()
    // Metrics live in the VM so accumulated measurements survive rotation.
    val runtimeMetrics = vm.runtimeMetrics
    val diagnostics = remember { RuntimeDiagnostics(runtimeMetrics, engine) }
    val jankMonitor = remember { FrameJankMonitor(runtimeMetrics) }
    val loaded by vm.loaded.collectAsState()
    val status by vm.status.collectAsState()
    val prompt by vm.prompt.collectAsState()
    val output by vm.output.collectAsState()
    val answer by vm.answer.collectAsState()
    val running by vm.running.collectAsState()
    val engineState by vm.engineState.collectAsState()
    val elapsed by vm.elapsedMs.collectAsState()
    val lastRoute by vm.lastRoute.collectAsState()
    val pendingApproval by vm.pendingApproval.collectAsState()
    val lastSources by vm.lastSources.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var traceExpanded by remember { mutableStateOf(true) }
    val serviceState by EngineForegroundService.state.collectAsState()
    val serviceRunning = serviceState != EngineServiceState.STOPPED

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
    var toolAlwaysAllow by remember { mutableStateOf(prefs.toolAlwaysAllow) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Mirror agent activity into the service lifecycle (Phase A4): the
    // service applies its own transition rules, the UI only reports events.
    LaunchedEffect(running) {
        EngineForegroundService.reportBusy(running)
    }
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
    val executionManager = remember { ExecutionManager.shared(context) }
    // One construction path for tools + sources (core-hardening C1): the
    // sheet inventory, the agent and the startup refresh share this instance.
    val toolbox = remember { Toolbox(context, engine, registry, prefs, executionManager) }

    // Attach once per ViewModel lifetime: the VM owns run state so rotation
    // never loses prompt/output/trace/state, and runs survive recreation.
    LaunchedEffect(Unit) {
        vm.attach(engine, context, localLibrary, registry, prefs, toolbox)
        if (vm.prompt.value.isBlank() && !initialPrompt.isNullOrBlank()) vm.setPrompt(initialPrompt)
        if (vm.status.value.isBlank()) {
            vm.setStatus(
                if (localModels.isEmpty()) {
                    "No GGUF model found \u2014 use Download GGUF or \u201c+ Pick GGUF from storage\u201d"
                } else {
                    "Local library ready: ${localModels.joinToString { it.file.name }}"
                },
            )
        }
    }
    LaunchedEffect(Unit) {
        vm.events.collect { ev ->
            val result = snackbarHostState.showSnackbar(
                message = ev.text,
                actionLabel = ev.actionLabel,
                withDismissAction = ev.actionLabel == null,
            )
            if (result == SnackbarResult.ActionPerformed) ev.action?.invoke()
        }
    }

    // Chat history (Phase 3 gap): runs were persisted but never visible.
    var historyExpanded by remember { mutableStateOf(false) }
    var historyLoaded by remember { mutableStateOf(false) }
    var historySessions by remember { mutableStateOf<List<ChatSession>>(emptyList()) }
    val chatHistory = remember {
        ChatHistory(toolbox.memory::recentSessions, toolbox.memory::recentMessages)
    }

    fun loadHistory() {
        historyLoaded = true
        scope.launch {
            historySessions = chatHistory.recent(limit = 8, messagesPerSession = 10)
        }
    }

    // Core error log (ERRORS tab): every recorded failure is visible here.
    var errorsExpanded by remember { mutableStateOf(false) }
    var errorEntries by remember { mutableStateOf(CoreErrors.log.all()) }
    var errorDetailId by remember { mutableStateOf<Long?>(null) }

    fun showErrors() {
        errorsExpanded = true
        historyExpanded = false
        errorEntries = CoreErrors.log.all()
    }
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
    var benchmarkLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var benchmarking by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<ModelDescriptor?>(null) }
    // Phase 10: skill management (create/update/delete user skills).
    var skillEditor by remember { mutableStateOf<Skill?>(null) }
    var skillToDelete by remember { mutableStateOf<Skill?>(null) }
    var skillsVersion by remember { mutableStateOf(0) }
    // Phase 4: verified dataset export + LoRA eligibility (Diagnostics).
    var trainingExportLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var exportingTraining by remember { mutableStateOf(false) }
    val trainingPipeline = remember {
        SelfLearningPipeline(toolbox.memory, File(context.filesDir, "training"))
    }
    var firstRunDismissed by remember { mutableStateOf(prefs.firstRunDismissed) }
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
                        val importedSha = ModelIntegrity.sha256(result.file) ?: ""
                        ModelManifest.record(
                            modelsDir,
                            result.file.name,
                            importedSha,
                            result.file.length(),
                        )
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
                        vm.setStatus("Imported ${result.file.name} \u2014 tap Load Model")
                        vm.notify("Imported ${result.file.name}")
                    }
                    is ImportResult.Error -> {
                        importStatus = "failed: ${result.message}"
                        vm.setStatus("import failed: ${result.message}")
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
                vm.setStatus("catalog: cached seeds (${r.error})")
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
            if (selectedLocalFile != null) "Available" to OpStatusSuccess
            else "No model file" to OpStatusWarn
        else ->
            if (providerRegistry.apiKey(selected.provider).isNotBlank()) "Connected" to OpStatusSuccess
            else "Free \u00b7 anonymous (rate-limited)" to OpStatusWarn
    }
    // Real engine state, driven only by actual operations (never faked).
    val headerState = if (routingModes[selectedMode] == RoutingMode.OFFLINE_ONLY &&
        engineState == EngineUiState.READY
    ) EngineUiState.OFFLINE else engineState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OpBg)
            .imePadding()
            .padding(16.dp),
    ) {
        // ---------------- HEADER (real engine state) ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NEVER SETTLE", color = OpText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Native Agentic AI \u00b7 OnePlus 7", color = OpTextSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { stateDescription = "Engine state ${headerState.label}" },
                ) {
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
                    Text(
                        if (running) "${headerState.label} \u00b7 ${formatElapsed(elapsed)}" else headerState.label,
                        color = OpText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
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
                .semantics { contentDescription = "Engine settings" }
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
        Text(
            status,
            color = OpTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (importing) {
            Text(importStatus, color = OpAmber, fontSize = 11.sp)
        }
        if (models.isEmpty() && localModels.isEmpty() && !firstRunDismissed) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(OpCard, RoundedCornerShape(12.dp))
                    .border(BorderStroke(1.dp, OpBorder), RoundedCornerShape(12.dp))
                    .padding(10.dp),
            ) {
                Text("FIRST RUN", color = OpText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    "1. Pick a model \u00b7 2. Download or import a GGUF \u00b7 3. Add sources",
                    color = OpTextSecondary,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    PillButton("Pick a model", Modifier.weight(1f), primary = true) { showPicker = true }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Download GGUF", Modifier.weight(1f)) {
                        downloadStatus = resumeHint(downloadTarget)
                        showDownloadDialog = true
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    PillButton("Add sources", Modifier.weight(1f)) { onOpenSources() }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Skip", Modifier.weight(1f)) {
                        firstRunDismissed = true
                        prefs.firstRunDismissed = true
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        } else {
            Spacer(Modifier.height(12.dp))
        }

        // ---------------- PROMPT (primary interaction) ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { vm.setPrompt(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("What do you want me to do?", color = OpTextSecondary) },
                minLines = 2,
                maxLines = 6,
                isError = false,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    vm.sendQuick(routingModes[selectedMode], selectedModelId, contextSize, threads, modelsDir)
                }),
                trailingIcon = {
                    if (prompt.isNotEmpty()) {
                        Text(
                            "\u00d7",
                            color = OpTextSecondary,
                            fontSize = 18.sp,
                            modifier = Modifier
                                .semantics { contentDescription = "Clear prompt" }
                                .clickable { vm.setPrompt("") }
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
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.sendQuick(routingModes[selectedMode], selectedModelId, contextSize, threads, modelsDir)
                },
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
            // One primary action that visibly toggles: Agent (start) <-> Stop.
            PillButton(
                if (running) "Stop" else "Agent",
                Modifier.weight(1f),
                primary = true,
                enabled = true,
                loading = running,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (running) {
                    vm.stop()
                } else {
                    scope.launch {
                        if (selected != null && selected.kind == ModelKind.LOCAL &&
                            !vm.ensureLocalLoaded(selectedLocalFile, contextSize, threads, modelsDir)
                        ) return@launch
                        vm.runAgent(
                            context = context,
                            registry = registry,
                            prefs = prefs,
                            toolbox = toolbox,
                            prompt = prompt,
                            mode = routingModes[selectedMode],
                            modeLabel = modes[selectedMode],
                            preferredId = selectedModelId,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            PillButton(
                "Clear",
                Modifier.weight(1f),
                enabled = !running && (prompt.isNotBlank() || answer.isNotBlank() || output.isNotBlank()),
            ) {
                vm.clearRun()
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- LOG ZONE: AGENT TRACE / HISTORY / ERRORS ----------------
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "AGENT TRACE",
                color = if (!historyExpanded && !errorsExpanded) OpText else OpTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        historyExpanded = false
                        errorsExpanded = false
                    }
                    .padding(end = 12.dp),
            )
            Text(
                if (historyLoaded) "HISTORY \u00b7 ${historySessions.size}" else "HISTORY",
                color = if (historyExpanded) OpText else OpTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable {
                        historyExpanded = true
                        errorsExpanded = false
                        if (!historyLoaded) loadHistory()
                    }
                    .padding(end = 12.dp),
            )
            val errorCount = CoreErrors.log.count()
            Text(
                if (errorCount > 0) "ERRORS \u00b7 $errorCount" else "ERRORS",
                color = if (errorsExpanded) OpText else OpTextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { showErrors() }
                    .padding(end = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (!historyExpanded && !errorsExpanded && traceExpanded) "\u25be" else "\u25b8",
                color = OpTextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.clickable {
                    if (!historyExpanded && !errorsExpanded) traceExpanded = !traceExpanded
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        if (errorsExpanded) {
            ErrorLogView(
                modifier = Modifier.weight(1f),
                entries = errorEntries,
                detailId = errorDetailId,
                onToggleDetail = { errorDetailId = if (errorDetailId == it) null else it },
                onClear = {
                    CoreErrors.log.clear()
                    errorEntries = emptyList()
                    errorDetailId = null
                },
                onRefresh = { errorEntries = CoreErrors.log.all() },
                onCopy = {
                    copyToClipboard(
                        context,
                        errorEntries.joinToString("\n") { "[${it.id}] ${it.source}: ${it.message}" },
                    )
                },
            )
        } else if (historyExpanded) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(OpCard, RoundedCornerShape(12.dp)),
            ) {
                if (!historyLoaded) {
                    Text(
                        "Loading\u2026",
                        color = OpTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                } else if (historySessions.isEmpty()) {
                    Text(
                        "No conversation history yet \u2014 agent runs are saved as you go.",
                        color = OpTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp),
                    )
                } else {
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(historySessions) { session ->
                            HistorySessionCard(session, onReuse = { vm.setPrompt(it) })
                        }
                    }
                }
            }
        } else if (traceExpanded) {
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
                            lastRoute?.let { route ->
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (route.isRemote) "REMOTE \u00b7 ${route.provider}" else "LOCAL \u00b7 GGUF",
                                    color = if (route.isRemote) OpBlue else OpSuccess,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            if (lastSources.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                lastSources.forEach { src ->
                                    Text(
                                        "SOURCE \u00b7 $src",
                                        color = OpTextSecondary,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
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
        SnackbarHost(snackbarHostState)
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
                                vm.ensureLocalLoaded(selectedLocalFile, contextSize, threads, modelsDir)
                            }
                        },
                        onChange = {
                            showSettingsSheet = false
                            showPicker = true
                        },
                        onDeleteLocal = if (it.kind == ModelKind.LOCAL) {
                            { deleteTarget = it }
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
                        if (td.permission == ToolPermission.REQUIRES_APPROVAL) {
                            val always = td.name in toolAlwaysAllow
                            TextButton(
                                onClick = {
                                    toolAlwaysAllow =
                                        if (always) toolAlwaysAllow - td.name
                                        else toolAlwaysAllow + td.name
                                    prefs.toolAlwaysAllow = toolAlwaysAllow
                                },
                            ) {
                                Text(
                                    if (always) "ALWAYS" else "ALLOW",
                                    color = if (always) OpStatusInfo else OpTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text("SKILLS", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Local workflows injected into the system prompt by task type.",
                    color = OpTextSecondary,
                    fontSize = 10.sp,
                )
                Spacer(Modifier.height(2.dp))
                val skills = remember(skillsVersion) { toolbox.skillManager.list() }
                if (skills.isEmpty()) {
                    Text(
                        "No skills \u2014 defaults re-seed on next start.",
                        color = OpTextSecondary,
                        fontSize = 10.sp,
                    )
                }
                skills.forEach { skill ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(skill.id, color = OpText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(skill.purpose, color = OpTextSecondary, fontSize = 10.sp)
                        }
                        Text(
                            if (skill.builtin) "SEEDED" else "CUSTOM",
                            color = if (skill.builtin) OpTextSecondary else OpStatusInfo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        if (!skill.builtin) {
                            TextButton(onClick = { skillEditor = skill }) {
                                Text("EDIT", color = OpStatusInfo, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { skillToDelete = skill }) {
                                Text("DEL", color = OpRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                PillButton("+ New skill", Modifier.fillMaxWidth()) {
                    skillEditor = Skill("", "")
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
                val localMeta = selectedLocalFile?.let { GgufMetaCache.metaFor(it) }
                val dynPlan = MemoryPlanner.plan(
                    modelBytes = selectedLocalFile?.length() ?: 0L,
                    nCtx = contextSize,
                    availRamBytes = availableRamBytes(context),
                    layers = localMeta?.layers,
                    hiddenDim = localMeta?.embeddingDim,
                )
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
                            vm.setStatus("service failed: ${e.message}")
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Download GGUF model", Modifier.weight(1f), enabled = !running && !downloading) {
                        downloadStatus = resumeHint(downloadTarget)
                        downloadProgress = null
                        showSettingsSheet = false
                        showDownloadDialog = true
                    }
                }
                Text(
                    "Service \u00b7 ${serviceState.name}",
                    color = if (serviceState == EngineServiceState.ERROR) OpAmber else OpTextSecondary,
                    fontSize = 10.sp,
                )
                Text(
                    "STORAGE \u00b7 ${localLibrary.storageUsedBytes() / (1024L * 1024L)} MB used",
                    color = OpTextSecondary,
                    fontSize = 10.sp,
                )
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
                vm.notify("Selected ${d.displayName}")
                showPicker = false
            },
            onToggleFavorite = { id ->
                favorites = prefs.toggleFavorite(id)
            },
            onRefresh = {
                scope.launch {
                    val r = discovery.refresh()
                    RemoteProviderBootstrap.registerRemoteProviders(registry, providerRegistry)
                    vm.setStatus(if (r.error == null) {
                        "discovery: ${r.found} new models, ${registry.list().size} total (${r.endpoint})"
                    } else {
                        "discovery failed: ${r.error} (using cached catalog)"
                    })
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
                vm.setStatus("API key set for ${keyProviderId} (memory only, never persisted)")
            },
            onClear = {
                providerRegistry.clearApiKey(keyProviderId!!)
                models.firstOrNull { it.provider == keyProviderId }?.let { d ->
                    registry.remove(d.id)
                    ensureRemoteProvider(registry, providerRegistry, prefs, d)
                }
                showKeyDialog = false
                vm.setStatus("API key cleared for ${keyProviderId}")
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
                            ModelManifest.record(
                                modelsDir,
                                downloadTarget.name,
                                result.sha256,
                                result.bytes,
                                url = url,
                            )
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
                            vm.setStatus("Model downloaded: ${downloadTarget.name} \u2014 tap Load Model")
                            vm.notify("Model downloaded: ${downloadTarget.name}")
                        }
                        is DownloadResult.Error -> {
                            if (result.message == "cancelled") {
                                val cachedMb = ModelDownloader(downloadTarget).existingBytes / (1024 * 1024)
                                downloadStatus = "paused \u2014 $cachedMb MB cached; tap Download to resume"
                                vm.setStatus("download paused \u2014 $cachedMb MB cached; tap Download to resume")
                            } else {
                                downloadStatus = "failed: ${result.message}"
                                vm.setStatus("download failed: ${result.message}")
                            }
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

    pendingApproval?.let { req ->
        AlertDialog(
            onDismissRequest = { },
            containerColor = OpBg,
            title = { Text("Approve tool call?", color = OpText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("${req.tool} wants to run:", color = OpText, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        redactSensitive(req.input),
                        color = OpTextSecondary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Permission: ${req.permission}",
                        color = OpStatusWarn,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { vm.resolveApproval(ApprovalDecision.ALLOW_ONCE) }) {
                        Text("Allow once", color = OpText)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { vm.resolveApproval(ApprovalDecision.ALWAYS_ALLOW) }) {
                        Text("Always allow", color = OpText)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.resolveApproval(ApprovalDecision.DENY) }) {
                    Text("Deny", color = OpRed)
                }
            },
        )
    }

    if (deleteTarget != null) {
        val d = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = OpBg,
            title = { Text("Delete ${d.displayName}?", color = OpText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Removes the local GGUF file from storage. This cannot be undone.",
                    color = OpTextSecondary,
                    fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        if (localLibrary.delete(d.id)) {
                            localModels = localLibrary.scan()
                            localLibrary.syncInto(
                                registry,
                                engine,
                                context.applicationInfo.nativeLibraryDir,
                                contextSize,
                            )
                            if (selectedModelId == d.id) {
                                selectedModelId = localModels.firstOrNull()?.id
                                    ?: LocalModelProvider.LOCAL_MODEL_ID
                                prefs.lastSelectedModelId = selectedModelId
                            }
                            vm.setStatus("Deleted ${d.displayName}")
                            vm.notify("Deleted ${d.displayName}")
                        }
                    }
                }) { Text("Delete", color = OpRed) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    skillToDelete?.let { skill ->
        AlertDialog(
            onDismissRequest = { skillToDelete = null },
            containerColor = OpBg,
            title = { Text("Delete skill?", color = OpText, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Delete \u201c${skill.id}\u201d? It is removed from storage and the registry.",
                    color = OpTextSecondary,
                    fontSize = 12.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    toolbox.skillManager.delete(skill.id)
                    skillsVersion++
                    skillToDelete = null
                    vm.setStatus("Deleted skill ${skill.id}")
                }) { Text("Delete", color = OpRed) }
            },
            dismissButton = { TextButton(onClick = { skillToDelete = null }) { Text("Cancel") } },
        )
    }

    skillEditor?.let { target ->
        SkillEditorDialog(
            skill = target,
            onDismiss = { skillEditor = null },
            onSave = { updated ->
                toolbox.skillManager.register(updated)
                skillsVersion++
                skillEditor = null
                vm.setStatus(if (target.id.isBlank()) "Created skill ${updated.id}" else "Updated skill ${updated.id}")
                vm.notify(if (target.id.isBlank()) "Created skill ${updated.id}" else "Updated skill ${updated.id}")
            },
        )
    }

    if (showDiagnostics) {
        DiagnosticsDialog(
            snapshot = diagnosticsSnapshot,
            serviceState = serviceState.name,
            runtimeLines = buildList {
                add(runCatching { "MNN    ${MnnBackend().status()}" }.getOrElse { e ->
                    CoreErrors.log.record("diagnostics", "MNN probe failed: ${e.message}", e)
                    "MNN    error: ${e.message}"
                })
                add(runCatching {
                    "VECT   ${if (USearchVectorIndex.selfTest()) "USearch ready (self-test ok)" else "USearch unavailable (self-test failed)"}"
                }.getOrElse { e ->
                    CoreErrors.log.record("diagnostics", "USearch probe failed: ${e.message}", e)
                    "VECT   error: ${e.message}"
                })
            },
            onBenchmark = {
                if (!benchmarking && !running) {
                    scope.launch {
                        benchmarking = true
                        benchmarkLines = listOf("running benchmark \u2026")
                        val results = runCatching {
                            ModelBenchmark(registry, toolbox.memory).run(selectedModelId)
                        }.getOrDefault(emptyList())
                        benchmarkLines = if (results.isEmpty()) {
                            listOf("benchmark unavailable for $selectedModelId")
                        } else {
                            results.map { r ->
                                val speed = r.tokensPerSec?.let { "%.1f tok/s".format(it) } ?: ""
                                val latency = r.remoteLatencyMs?.let { "${it} ms latency" } ?: ""
                                if (r.ok) {
                                    "${r.category}: ${r.tokens} tok \u00b7 ${r.durationMs} ms" +
                                        (if (speed.isNotBlank()) " \u00b7 $speed" else "") +
                                        (if (latency.isNotBlank()) " \u00b7 $latency" else "")
                                } else {
                                    "${r.category}: failed \u2014 ${r.error}"
                                }
                            }
                        }
                        benchmarking = false
                    }
                }
            },
            benchmarking = benchmarking,
            benchmarkLines = benchmarkLines,
            trainingExportLines = trainingExportLines,
            exportingTraining = exportingTraining,
            onExportTraining = {
                if (!exportingTraining && !running) {
                    scope.launch {
                        exportingTraining = true
                        trainingExportLines = listOf("exporting \u2026")
                        val r = trainingPipeline.exportVerifiedTrainingData()
                        val elig = trainingPipeline.triggerBackgroundFinetune(r)
                        trainingExportLines = buildList {
                            add("DATASET  ${r.file?.name ?: "none"}")
                            add("PAIRS    ${r.exported} / min ${trainingPipeline.minPairs}")
                            if (r.reason != "ok") add("REASON   ${r.reason}")
                            add("ON-DEVICE LoRA ${if (elig.eligible) "ELIGIBLE" else "NOT ELIGIBLE"}")
                            elig.reasons.forEach { add("  \u00b7 $it") }
                        }
                        exportingTraining = false
                    }
                }
            },
            onClear = {
                runtimeMetrics.reset()
                diagnosticsSnapshot = null
                vm.setStatus("runtime metrics cleared")
            },
            onCopy = { text -> copyToClipboard(context, text) },
            onShare = { text -> shareText(context, text) },
            onDismiss = { showDiagnostics = false },
        )
    }
}

/** Diagnostics panel: real measured values, never guessed (spec §16/§21). */
@Composable
private fun DiagnosticsDialog(
    snapshot: DiagnosticsSnapshot?,
    serviceState: String,
    runtimeLines: List<String> = emptyList(),
    onBenchmark: () -> Unit = {},
    benchmarking: Boolean = false,
    benchmarkLines: List<String> = emptyList(),
    trainingExportLines: List<String> = emptyList(),
    exportingTraining: Boolean = false,
    onExportTraining: () -> Unit = {},
    onClear: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
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
        ) + runtimeLines
    } ?: listOf("collecting\u2026") + runtimeLines
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
                if (snapshot != null && snapshot.ceilingBytes > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (snapshot.rssBytes.toFloat() / snapshot.ceilingBytes.toFloat()).coerceIn(0f, 1f) },
                        color = if (snapshot.overLimit) OpRed else OpStatusInfo,
                        trackColor = OpDivider,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (benchmarkLines.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    benchmarkLines.forEach { line ->
                        Text(
                            line,
                            color = OpTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    PillButton(
                        if (exportingTraining) "Export \u2026" else "Export dataset",
                        Modifier.weight(1f),
                        enabled = !exportingTraining,
                        loading = exportingTraining,
                    ) { onExportTraining() }
                }
                if (trainingExportLines.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    trainingExportLines.forEach { line ->
                        Text(
                            line,
                            color = OpTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 1.dp),
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    PillButton(
                        if (benchmarking) "Benchmark \u2026" else "Benchmark",
                        Modifier.weight(1f),
                        enabled = !benchmarking,
                        loading = benchmarking,
                    ) { onBenchmark() }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Copy", Modifier.weight(1f)) { onCopy(lines.joinToString("\n")) }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Share", Modifier.weight(1f)) { onShare(lines.joinToString("\n")) }
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
            t.startsWith("MODE ") || t.startsWith("STATUS ") ||
            t.startsWith("FALLBACK ") -> Text(
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

/** Phase 10 — create/update a user skill (never executable code). */
@Composable
private fun SkillEditorDialog(
    skill: Skill,
    onDismiss: () -> Unit,
    onSave: (Skill) -> Unit,
) {
    val editing = skill.id.isNotBlank()
    var id by remember { mutableStateOf(skill.id) }
    var purpose by remember { mutableStateOf(skill.purpose) }
    var tools by remember { mutableStateOf(skill.tools.joinToString(", ")) }
    var workflow by remember { mutableStateOf(skill.workflow.joinToString("\n")) }
    var constraints by remember { mutableStateOf(skill.constraints) }
    val idValid = id.matches(Regex("[a-z0-9][a-z0-9-]*")) && id.isNotBlank()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = OpRed,
        unfocusedBorderColor = OpBorder,
        errorBorderColor = OpRed,
        cursorColor = OpRed,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = OpBg,
        title = {
            Text(
                if (editing) "EDIT SKILL" else "NEW SKILL",
                color = OpText,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it.lowercase() },
                    enabled = !editing,
                    label = { Text("id", color = OpTextSecondary) },
                    isError = id.isNotBlank() && !idValid,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("purpose", color = OpTextSecondary) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = tools,
                    onValueChange = { tools = it },
                    label = { Text("tools (comma-separated, optional)", color = OpTextSecondary) },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = workflow,
                    onValueChange = { workflow = it },
                    label = { Text("workflow (one step per line)", color = OpTextSecondary) },
                    minLines = 3,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = constraints,
                    onValueChange = { constraints = it },
                    label = { Text("constraints (optional)", color = OpTextSecondary) },
                    minLines = 2,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = idValid && purpose.isNotBlank(),
                onClick = {
                    onSave(
                        Skill(
                            id = id.trim(),
                            purpose = purpose.trim(),
                            tools = tools.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                            workflow = workflow.lineSequence()
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toList(),
                            constraints = constraints.trim(),
                            builtin = false,
                        ),
                    )
                },
            ) { Text("Save", color = OpRed) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Honest resume hint for the download dialog (partial .tmp exists). */
private fun resumeHint(target: File): String {
    val cachedMb = ModelDownloader(target).existingBytes / (1024 * 1024)
    return if (cachedMb > 0L) "partial download found \u2014 $cachedMb MB cached; Download resumes" else ""
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


@Composable
private fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) OpRed else OpCard,
            contentColor = OpText,
            disabledContainerColor = OpCard.copy(alpha = 0.4f),
            disabledContentColor = OpTextSecondary,
        ),
        border = if (primary) null else BorderStroke(1.dp, OpDivider),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = if (primary) Color.White else OpTextSecondary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(text)
        }
    }
}

@Composable
private fun HistorySessionCard(session: ChatSession, onReuse: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(OpBg, RoundedCornerShape(10.dp))
            .border(1.dp, OpBorder, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(
            "${formatHistoryTime(session.startedAt)} \u00b7 ${session.messages.size} turn${if (session.messages.size == 1) "" else "s"} \u00b7 ${session.meta.ifBlank { "session ${session.id}" }}",
            color = OpTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        session.messages.forEach { msg ->
            val user = msg.role == "user"
            Text(
                "${if (user) "YOU" else "AGENT"}  ${msg.content.take(140)}",
                color = if (user) OpText else OpTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = if (user) {
                    Modifier
                        .fillMaxWidth()
                        .clickable { onReuse(msg.content) }
                        .padding(top = 6.dp)
                } else {
                    Modifier.fillMaxWidth().padding(top = 6.dp)
                },
            )
        }
        Text(
            "tap a YOU line to reuse it as your next prompt",
            color = OpTextSecondary,
            fontSize = 9.sp,
        )
    }
}

private fun formatHistoryTime(ms: Long): String =
    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(ms))

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
            .defaultMinSize(minHeight = 48.dp)
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

private fun formatElapsed(ms: Long): String =
    String.format(Locale.US, "%d:%02d", ms / 60000, (ms % 60000) / 1000)

private fun redactSensitive(input: String): String =
    input.replace(
        Regex("(?i)(token|key|secret|password|api[_ -]?key|authorization)[=:][^ ,;\\n]+"),
        "$1=***",
    ).take(240)

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm?.setPrimaryClip(ClipData.newPlainText("native-ai", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Share")) }
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
            if (d.runtime != RuntimeKind.UNKNOWN) {
                ProviderPill(
                    when (d.runtime) {
                        RuntimeKind.LLAMA_GGUF -> "GGUF"
                        RuntimeKind.MNN -> "MNN"
                        RuntimeKind.API -> "API"
                        RuntimeKind.UNKNOWN -> ""
                    },
                    color = OpTextSecondary,
                )
            }
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
                    append(
                        when (d.runtime) {
                            RuntimeKind.MNN -> "MNN"
                            RuntimeKind.LLAMA_GGUF -> "GGUF"
                            else -> "LOCAL"
                        },
                    )
                    append(" \u00b7 ${modelFileSizeMb} MB")
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
                        color = OpLinkAccent,
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
                        color = OpStatusInfo,
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

@Composable
private fun ErrorLogView(
    modifier: Modifier = Modifier,
    entries: List<ErrorEntry>,
    detailId: Long?,
    onToggleDetail: (Long) -> Unit,
    onClear: () -> Unit,
    onRefresh: () -> Unit,
    onCopy: () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(OpCard, RoundedCornerShape(12.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${entries.size} error${if (entries.size == 1) "" else "s"} \u00b7 last failures visible here",
                color = OpTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                "CLEAR",
                color = OpTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = entries.isNotEmpty()) { onClear() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "REFRESH",
                color = OpTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onRefresh() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "COPY",
                color = OpTextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(enabled = entries.isNotEmpty()) { onCopy() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        if (entries.isEmpty()) {
            Text(
                "No errors recorded.",
                color = OpTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(12.dp),
            )
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries) { entry ->
                    val expanded = detailId == entry.id
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(OpBg, RoundedCornerShape(10.dp))
                            .border(1.dp, OpBorder, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onToggleDetail(entry.id) }
                            .padding(10.dp),
                    ) {
                        Text(
                            "${formatHistoryTime(entry.atMs)} \u00b7 ${entry.source}${if (expanded) " \u25be" else " \u25b8"}",
                            color = OpTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            entry.message,
                            color = OpText,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (expanded && !entry.detail.isNullOrBlank()) {
                            Text(
                                entry.detail,
                                color = OpTextSecondary,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(OpBg, RoundedCornerShape(6.dp))
                                    .padding(6.dp)
                                    .padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
