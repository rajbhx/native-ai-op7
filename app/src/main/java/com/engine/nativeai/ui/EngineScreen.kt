package com.engine.nativeai.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.engine.nativeai.AgentEvent
import com.engine.nativeai.AgentState
import com.engine.nativeai.CalculatorTool
import com.engine.nativeai.EngineForegroundService
import com.engine.nativeai.EngineConfig
import com.engine.nativeai.FileSearchTool
import com.engine.nativeai.FinalAnswerTool
import com.engine.nativeai.GenerationConfig
import com.engine.nativeai.LocalFallbackProvider
import com.engine.nativeai.MemoryDatabase
import com.engine.nativeai.ModelDownloader
import com.engine.nativeai.DownloadResult
import com.engine.nativeai.MemorySearchTool
import com.engine.nativeai.ModelCatalog
import com.engine.nativeai.ModelCostTier
import com.engine.nativeai.ModelDiscoveryService
import com.engine.nativeai.ModelKind
import com.engine.nativeai.ModelDescriptor
import com.engine.nativeai.ModelInfoTool
import com.engine.nativeai.ModelRegistry
import com.engine.nativeai.ModelPreferencesStore
import com.engine.nativeai.ModelRouter
import com.engine.nativeai.OpenAICompatibleProvider
import com.engine.nativeai.PrivacyMode
import com.engine.nativeai.ProviderRegistry
import com.engine.nativeai.NativeEngine
import com.engine.nativeai.RoutingMode
import com.engine.nativeai.SystemInfoTool
import com.engine.nativeai.ThinkingAgent
import com.engine.nativeai.ToolRegistry
import com.engine.nativeai.WebSearchTool
import java.io.File
import kotlinx.coroutines.launch

/**
 * OxygenOS Model Hub + Live Agent Trace dashboard (blueprint Phase 6).
 * Plain Compose, no external UI framework; design tokens from Theme.kt.
 */
@Composable
fun EngineScreen(
    engine: NativeEngine,
    registry: ModelRegistry,
    providerRegistry: ProviderRegistry,
    prefs: ModelPreferencesStore,
    discovery: ModelDiscoveryService,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modelFile = remember { File(context.filesDir, "models/model.gguf") }

    var loaded by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf("Engine library loaded. Put a GGUF at:\n${modelFile.absolutePath}")
    }
    var prompt by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var serviceOn by remember { mutableStateOf(false) }
    var engineState by remember { mutableStateOf(EngineUiState.READY) }
    var selectedMode by remember { mutableStateOf(modeIndexFor(prefs.routingMode)) }
    var selectedModelId by remember { mutableStateOf(prefs.lastSelectedModelId ?: "local-llama") }
    var favorites by remember { mutableStateOf(prefs.favorites()) }
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
    val downloadCancel = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    LaunchedEffect(Unit) {
        // Refresh the catalog once on first launch (cached metadata is used
        // afterwards; manual Refresh is always available in the picker).
        if (registry.lastRefreshMs == 0L) {
            val r = discovery.refresh()
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

    val modes = listOf("AUTO", "FREE", "LOCAL", "OFFLINE")
    val routingModes = listOf(
        RoutingMode.HYBRID, RoutingMode.FREE_ONLY, RoutingMode.LOCAL_FIRST, RoutingMode.OFFLINE_ONLY,
    )
    val models = registry.list()
    val selected = models.firstOrNull { it.id == selectedModelId }
    val (connText, connOk) = when {
        selected == null -> "not found" to false
        selected.kind == ModelKind.LOCAL ->
            if (modelFile.exists()) "Available" to true else "No model file" to false
        else ->
            if (providerRegistry.apiKey(selected.provider).isNotBlank()) "Connected" to true
            else "Not connected" to false
    }
    // Real engine state, driven only by actual operations (never faked).
    val headerState = if (routingModes[selectedMode] == RoutingMode.OFFLINE_ONLY &&
        engineState == EngineUiState.READY
    ) EngineUiState.OFFLINE else engineState

    // Loads the local GGUF on demand so Agent/Generate work with one tap.
    suspend fun ensureLocalLoaded(): Boolean {
        if (loaded) return true
        if (!modelFile.exists()) {
            engineState = EngineUiState.ERROR
            status = "Model not found:\n${modelFile.absolutePath}\nCopy a GGUF there, then retry."
            return false
        }
        engineState = EngineUiState.LOADING
        status = "loading model (threads=$threads)\u2026"
        return try {
            if (loaded) engine.close()
            engine.init(
                EngineConfig(
                    modelFile.absolutePath,
                    threads = threads,
                    contextSize = contextSize,
                    nativeLibDir = context.applicationInfo.nativeLibraryDir,
                ),
            )
            loaded = true
            engineState = EngineUiState.COMPLETED
            status = "Model loaded: ${modelFile.name} (threads=$threads)"
            true
        } catch (e: Exception) {
            engineState = EngineUiState.ERROR
            status = "init failed: ${e.message}"
            false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OpBg)
            .verticalScroll(rememberScrollState())
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
                                    EngineUiState.COMPLETED -> Color(0xFF2ECC71)
                                    EngineUiState.ERROR -> OpRed
                                    EngineUiState.OFFLINE -> OpTextSecondary
                                    else -> OpRed
                                },
                                RoundedCornerShape(4.dp),
                            ),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(headerState.label, color = OpText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("OP7", color = OpTextSecondary, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(status, color = OpTextSecondary, fontSize = 12.sp)
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
                    unfocusedBorderColor = OpDivider,
                    cursorColor = OpRed,
                ),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (prompt.isBlank()) return@launch
                        if (!ensureLocalLoaded()) return@launch
                        running = true
                        output = ""
                        engineState = EngineUiState.THINKING
                        status = "generating\u2026"
                        try {
                            val sb = StringBuilder()
                            engine.generateStream(prompt, GenerationConfig(maxTokens = 64))
                                .collect { sb.append(it); output = sb.toString() }
                            status = "done (${sb.length} chars)"
                            engineState = EngineUiState.COMPLETED
                        } catch (e: Exception) {
                            status = "generate failed: ${e.message}"
                            engineState = EngineUiState.ERROR
                        } finally {
                            running = false
                        }
                    }
                },
                enabled = prompt.isNotBlank() && !running,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (running) OpCard else OpRed,
                    contentColor = OpText,
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Text("\u2191", fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- CPU THREADS ----------------
        Text("CPU THREADS", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(2, 3, 4, 5, 6).forEach { n ->
                SegmentedPill("$n", selected = threads == n, modifier = Modifier.padding(end = 8.dp)) {
                    threads = n
                }
            }
        }
        Text("Recommended: 4 \u00b7 Snapdragon 855 (Kryo 485)", color = OpTextSecondary, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        Text("CONTEXT", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf(512, 1024, 2048).forEach { n ->
                SegmentedPill("$n", selected = contextSize == n, modifier = Modifier.padding(end = 8.dp)) {
                    contextSize = n
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- PRIMARY ACTIONS ----------------
        Row(Modifier.fillMaxWidth()) {
            PillButton("Load Model", Modifier.weight(1f), enabled = !running) {
                scope.launch {
                    engineState = EngineUiState.LOADING
                    status = "loading model\u2026"
                    if (ensureLocalLoaded()) {
                        engineState = EngineUiState.COMPLETED
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            PillButton("Generate", Modifier.weight(1f), enabled = !running && prompt.isNotBlank()) {
                scope.launch {
                    if (!ensureLocalLoaded()) return@launch
                    running = true
                    output = ""
                    engineState = EngineUiState.THINKING
                    status = "generating\u2026"
                    try {
                        val sb = StringBuilder()
                        engine.generateStream(prompt, GenerationConfig(maxTokens = 64))
                            .collect { sb.append(it); output = sb.toString() }
                        status = "done (${sb.length} chars)"
                        engineState = EngineUiState.COMPLETED
                    } catch (e: Exception) {
                        status = "generate failed: ${e.message}"
                        engineState = EngineUiState.ERROR
                    } finally {
                        running = false
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            PillButton(
                if (running) "Agent \u00b7 RUNNING" else "Agent",
                Modifier.weight(1f),
                primary = true,
                enabled = !running,
            ) {
                scope.launch {
                    // One-tap usability: load the local model first when the
                    // user picked local but hasn't loaded it yet.
                    if (selectedModelId == "local-llama" && !ensureLocalLoaded()) return@launch
                    runAgent(
                        context = context,
                        engine = engine,
                        registry = registry,
                        providerRegistry = providerRegistry,
                        prefs = prefs,
                        prompt = prompt,
                        mode = routingModes[selectedMode],
                        modeLabel = modes[selectedMode],
                        preferredId = selectedModelId,
                        loaded = loaded,
                        setRunning = { running = it },
                        setStatus = { status = it },
                        setOutput = { output = it },
                        setEngineState = { engineState = it },
                        scope = scope,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            PillButton("Stats", Modifier.weight(1f), enabled = !running) {
                scope.launch {
                    if (!ensureLocalLoaded()) return@launch
                    try {
                        val s = engine.memoryStats()
                        status = "model=${s.modelBytes / (1024 * 1024)} MB | ctx=${s.nCtx} | " +
                            "kv=${s.kvTypeK}/${s.kvTypeV} | threads=${s.threads} | " +
                            "gpu=${s.gpuLayers} | gpuOffload=${s.gpuOffloadSupported} | " +
                            "rss=${s.rssBytes / (1024 * 1024)} MB" +
                            (if (s.rssOverLimit) " | OVER 1.5GB LIMIT" else "")
                    } catch (e: Exception) {
                        status = "stats failed: ${e.message}"
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PillButton(
            if (serviceOn) "STOP SERVICE" else "Start service",
            Modifier.fillMaxWidth(),
            primary = serviceOn,
            enabled = !running,
        ) {
            val next = !serviceOn
            try {
                if (next) {
                    EngineForegroundService.start(context)
                } else {
                    EngineForegroundService.stop(context)
                }
                serviceOn = next
            } catch (e: Exception) {
                status = "service failed: ${e.message}"
            }
        }
        Spacer(Modifier.height(8.dp))
        PillButton("Download GGUF model", Modifier.fillMaxWidth(), enabled = !running && !downloading) {
            downloadStatus = ""
            downloadProgress = null
            showDownloadDialog = true
        }

        Spacer(Modifier.height(18.dp))
        Text("MODEL HUB", color = OpText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .background(OpCard, RoundedCornerShape(24.dp))
                .padding(4.dp),
        ) {
            modes.forEachIndexed { i, label ->
                SegmentedPill(label, selected = i == selectedMode, Modifier.weight(1f)) {
                    selectedMode = i
                    prefs.routingMode = routingModes[i]
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("SELECTED", color = OpTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        models.filter { it.id == selectedModelId }.forEach { ModelCard(it, connText, connOk) }
        if (registry.lastRefreshMs > 0) {
            Text(
                "Catalog updated: ${formatCatalogTime(registry.lastRefreshMs)} \u00b7 ${models.size} models",
                color = OpTextSecondary,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            if (selected?.kind == ModelKind.REMOTE) {
                PillButton("Configure", Modifier.weight(1f), enabled = !running) {
                    keyProviderId = selected.provider
                    keyInput = providerRegistry.apiKey(selected.provider)
                    showKeyDialog = true
                }
                Spacer(Modifier.width(8.dp))
            }
            PillButton("Pick Model", Modifier.weight(1f), primary = true, enabled = !running) {
                showPicker = true
            }
        }
        Spacer(Modifier.height(4.dp))
        if (showPicker) {
            ModelPickerDialog(
                models = models,
                selectedId = selectedModelId,
                lastUpdated = registry.lastRefreshMs,
                favorites = favorites,
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
                        status = if (r.error == null) {
                            "discovery: ${r.found} new models, ${registry.list().size} total (${r.endpoint})"
                        } else {
                            "discovery failed: ${r.error} (using cached catalog)"
                        }
                    }
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
                    // Recreate the live provider so the running agent picks up the new key.
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
                        val result = ModelDownloader(modelFile).download(
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
                                downloadStatus = "saved ${modelFile.name} (${result.bytes / (1024 * 1024)} MB) \u2014 tap Load Model"
                                status = "Model downloaded: ${modelFile.name} \u2014 tap Load Model"
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

        Spacer(Modifier.height(18.dp))
        Text("AGENT TRACE", color = OpText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(OpCard, RoundedCornerShape(12.dp)),
        ) {
            Column(Modifier.padding(12.dp)) {
                if (output.isBlank()) {
                    Text("Trace will appear here\u2026", color = OpTextSecondary, fontSize = 13.sp)
                } else {
                    SelectionContainer {
                        Text(
                            formatTrace(output),
                            color = OpText,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
        HorizonLight(active = running)
        Spacer(Modifier.height(24.dp))
    }
}

/** Renders the real agent event log with a structured step rail. */
private fun formatTrace(raw: String): String =
    raw.lineSequence().joinToString("\n") { line ->
        when {
            line.startsWith("[tool]") || line.startsWith("[obs]") || line.startsWith("[verify]") ->
                "   $line"
            line.startsWith("[") -> "\u25cf $line"
            else -> line
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
    engine: NativeEngine,
    registry: ModelRegistry,
    providerRegistry: ProviderRegistry,
    prefs: ModelPreferencesStore,
    prompt: String,
    mode: RoutingMode,
    modeLabel: String,
    preferredId: String?,
    loaded: Boolean,
    setRunning: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setOutput: (String) -> Unit,
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
    setStatus("agent running\u2026")
    setEngineState(EngineUiState.THINKING)
    scope.launch {
        val memory = MemoryDatabase(context)
        val tools = ToolRegistry().apply {
            register(MemorySearchTool(memory))
            register(CalculatorTool())
            register(SystemInfoTool(engine, memory))
            register(WebSearchTool(LocalFallbackProvider()))
            register(FileSearchTool(context.filesDir))
            register(ModelInfoTool(registry))
            register(FinalAnswerTool())
        }
        val agent = ThinkingAgent(
            router = ModelRouter(mode = effectiveMode),
            registry = registry,
            memory = memory,
            tools = tools,
            networkAvailable = hasNetwork(context),
            preferredId = preferredId,
        )
        val sessionId = try {
            memory.startSession("agent: ${prompt.take(60)}")
        } catch (e: Exception) {
            null // memory failure must never crash the agent
        }
        try {
            val sb = StringBuilder()
            var done = false
            agent.run(prompt, GenerationConfig(maxTokens = 256)).collect { ev ->
                when (ev) {
                    is AgentEvent.Token -> {
                        sb.append(ev.text)
                        setOutput(sb.toString())
                    }
                    is AgentEvent.Stage -> {
                        sb.append("\n[${ev.state}]")
                        setOutput(sb.toString())
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
                        val remote = ev.provider != "local"
                        sb.append(
                            "\nMODEL ${ev.modelId}" +
                                "\nPROVIDER ${ev.provider}" +
                                "\nMODE ${if (remote) "Remote (${modeLabel})" else modeLabel}" +
                                "\nSTATUS ${if (remote) "Running · remote request" else "Running · local"}" +
                                "\n[${ev.taskType} | ${ev.costTier}]\n",
                        )
                        setOutput(sb.toString())
                    }
                    is AgentEvent.ToolCall -> {
                        sb.append("\n[tool] ${ev.tool}(${ev.input})")
                        setOutput(sb.toString())
                        setEngineState(EngineUiState.TOOL)
                    }
                    is AgentEvent.Observation -> {
                        sb.append("\n[obs] ${ev.output.take(240)}")
                        setOutput(sb.toString())
                    }
                    is AgentEvent.Verification -> {
                        sb.append("\n[verify] ${ev.tool}: " +
                            if (ev.passed) "passed" else "failed")
                        setOutput(sb.toString())
                        setEngineState(EngineUiState.VERIFYING)
                    }
                    is AgentEvent.Final -> {
                        done = true
                        sb.append("\n\nFINAL: ${ev.answer}")
                        setOutput(sb.toString())
                        setEngineState(EngineUiState.COMPLETED)
                    }
                    is AgentEvent.Error -> {
                        setStatus("agent error: ${ev.message}")
                        setEngineState(EngineUiState.ERROR)
                    }
                }
            }
            setStatus(if (done) "agent done" else "agent ended without final answer")
            if (!done) setEngineState(EngineUiState.READY)
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
    }
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
private fun SegmentedPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) OpRed else Color.Transparent,
            contentColor = if (selected) OpText else OpTextSecondary,
        ),
    ) {
        Text(text, fontSize = 12.sp)
    }
}

private fun modeIndexFor(mode: RoutingMode): Int = when (mode) {
    RoutingMode.HYBRID -> 0      // AUTO
    RoutingMode.FREE_FIRST, RoutingMode.FREE_ONLY -> 1 // FREE
    RoutingMode.LOCAL_FIRST -> 2 // LOCAL
    RoutingMode.OFFLINE_ONLY -> 3 // OFFLINE
}

private fun selectedModelName(models: List<ModelDescriptor>, id: String?): String =
    models.firstOrNull { it.id == id }?.displayName ?: (id ?: "none")

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
private fun ModelCard(d: ModelDescriptor, connectionText: String, connectionOk: Boolean) {
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
                ModelCostTier.FREE -> ProviderPill("FREE", red = true)
                ModelCostTier.PAID -> ProviderPill("PAID")
                ModelCostTier.UNKNOWN -> ProviderPill("?")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${d.provider} · ctx ${d.contextLength ?: "UNKNOWN"} · tools ${yn(d.supportsTools)} · " +
                "vision ${yn(d.supportsVision)} · coding ${d.codingScore ?: "?"} · " +
                "reasoning ${d.reasoningScore ?: "?"}",
            color = OpTextSecondary,
            fontSize = 11.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (connectionOk) "\u25cf $connectionText" else "\u00d7 $connectionText",
                color = if (connectionOk) Color(0xFF2ECC71) else OpRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Text(d.availability.name.replace('_', ' '), color = OpTextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ProviderPill(text: String, red: Boolean = false) {
    Text(
        text = text,
        color = if (red) OpText else OpTextSecondary,
        fontSize = 10.sp,
        modifier = Modifier
            .padding(start = 6.dp)
            .background(if (red) OpRed else OpCard, RoundedCornerShape(12.dp))
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

private fun yn(b: Boolean): String = if (b) "yes" else "no"

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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API key · $provider", color = OpText) },
        text = {
            Column {
                Text(
                    "Entered keys live only in memory for this session. They are never persisted, logged, or uploaded anywhere.",
                    color = OpTextSecondary,
                    fontSize = 12.sp,
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
                        unfocusedBorderColor = OpDivider,
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
                        unfocusedBorderColor = OpDivider,
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
