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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.engine.nativeai.AgentEvent
import com.engine.nativeai.CalculatorTool
import com.engine.nativeai.EngineForegroundService
import com.engine.nativeai.EngineConfig
import com.engine.nativeai.FileSearchTool
import com.engine.nativeai.FinalAnswerTool
import com.engine.nativeai.GenerationConfig
import com.engine.nativeai.LocalFallbackProvider
import com.engine.nativeai.MemoryDatabase
import com.engine.nativeai.MemorySearchTool
import com.engine.nativeai.ModelCatalog
import com.engine.nativeai.ModelCostTier
import com.engine.nativeai.ModelDescriptor
import com.engine.nativeai.ModelInfoTool
import com.engine.nativeai.ModelRegistry
import com.engine.nativeai.ModelRouter
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
fun EngineScreen(engine: NativeEngine, registry: ModelRegistry) {
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
    var selectedMode by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
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

    val modes = listOf("Auto", "Free-First", "Offline")
    val routingModes = listOf(RoutingMode.HYBRID, RoutingMode.FREE_FIRST, RoutingMode.OFFLINE_ONLY)
    val models = registry.list()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OpBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("NEVER SETTLE", color = OpText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Native Agentic AI Engine · OnePlus 7", color = OpTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Text(status, color = OpTextSecondary, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Prompt / task", color = OpTextSecondary) },
            minLines = 2,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OpRed,
                unfocusedBorderColor = OpDivider,
                cursorColor = OpRed,
            ),
        )
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth()) {
            PillButton("Load model", Modifier.weight(1f), enabled = !running) {
                scope.launch {
                    status = if (!modelFile.exists()) {
                        "Model not found:\n${modelFile.absolutePath}\nCopy a GGUF there, then retry."
                    } else {
                        try {
                            engine.init(
    EngineConfig(
        modelFile.absolutePath,
        nativeLibDir = context.applicationInfo.nativeLibraryDir,
    ),
)
                            loaded = true
                            "Model loaded: ${modelFile.name}"
                        } catch (e: Exception) {
                            "init failed: ${e.message}"
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            PillButton("Generate", Modifier.weight(1f), enabled = !running) {
                scope.launch {
                    if (!loaded) {
                        status = "Load a model first."
                        return@launch
                    }
                    output = ""
                    status = "generating…"
                    try {
                        val sb = StringBuilder()
                        engine.generateStream(prompt, GenerationConfig(maxTokens = 64))
                            .collect { sb.append(it); output = sb.toString() }
                        status = "done (${sb.length} chars)"
                    } catch (e: Exception) {
                        status = "generate failed: ${e.message}"
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            PillButton("Stats", Modifier.weight(1f), enabled = !running) {
                scope.launch {
                    if (!loaded) {
                        status = "Load a model first."
                        return@launch
                    }
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
            Spacer(Modifier.width(8.dp))
            PillButton("Agent", Modifier.weight(1f), primary = true, enabled = !running) {
                runAgent(
                    context = context,
                    engine = engine,
                    registry = registry,
                    prompt = prompt,
                    mode = routingModes[selectedMode],
                    loaded = loaded,
                    setRunning = { running = it },
                    setStatus = { status = it },
                    setOutput = { output = it },
                    scope = scope,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        PillButton(
            if (serviceOn) "Stop service" else "Start service",
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
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        models.forEach { ModelCard(it) }

        Spacer(Modifier.height(18.dp))
        Text("AGENT TRACE", color = OpText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .background(OpCard, RoundedCornerShape(12.dp)),
        ) {
            Column(Modifier.padding(12.dp)) {
                SelectionContainer {
                    Text(
                        output.ifBlank { "Trace will appear here…" },
                        color = OpText,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        HorizonLight(active = running)
        Spacer(Modifier.height(24.dp))
    }
}

private fun runAgent(
    context: Context,
    engine: NativeEngine,
    registry: ModelRegistry,
    prompt: String,
    mode: RoutingMode,
    loaded: Boolean,
    setRunning: (Boolean) -> Unit,
    setStatus: (String) -> Unit,
    setOutput: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    if (!loaded) {
        setStatus("Load a model first.")
        return
    }
    setRunning(true)
    setOutput("")
    setStatus("agent running…")
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
            router = ModelRouter(mode = mode),
            registry = registry,
            memory = memory,
            tools = tools,
            networkAvailable = hasNetwork(context),
        )
        val sessionId = memory.startSession("agent: ${prompt.take(60)}")
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
                    }
                    is AgentEvent.Routed -> {
                        sb.append(" [${ev.modelId} | ${ev.provider} | ${ev.costTier} | ${ev.taskType}]\n")
                        setOutput(sb.toString())
                    }
                    is AgentEvent.ToolCall -> {
                        sb.append("\n[tool] ${ev.tool}(${ev.input})")
                        setOutput(sb.toString())
                    }
                    is AgentEvent.Observation -> {
                        sb.append("\n[obs] ${ev.output.take(240)}")
                        setOutput(sb.toString())
                    }
                    is AgentEvent.Verification -> {
                        sb.append("\n[verify] ${ev.tool}: " +
                            if (ev.passed) "passed" else "failed")
                        setOutput(sb.toString())
                    }
                    is AgentEvent.Final -> {
                        done = true
                        sb.append("\n\nFINAL: ${ev.answer}")
                        setOutput(sb.toString())
                    }
                    is AgentEvent.Error -> setStatus("agent error: ${ev.message}")
                }
            }
            setStatus(if (done) "agent done" else "agent ended without final answer")
        } catch (e: Exception) {
            setStatus("agent failed: ${e.message}")
        } finally {
            memory.endSession(sessionId)
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

@Composable
private fun ModelCard(d: ModelDescriptor) {
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
            ProviderPill(d.kind.name)
            when (d.costTier) {
                ModelCostTier.FREE -> ProviderPill("FREE", red = true)
                ModelCostTier.PAID -> ProviderPill("PAID")
                ModelCostTier.UNKNOWN -> ProviderPill("?")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "provider ${d.provider} · ctx ${d.contextLength} · tools ${yn(d.supportsTools)} · " +
                "vision ${yn(d.supportsVision)} · coding ${d.codingScore ?: "?"} · " +
                "reasoning ${d.reasoningScore ?: "?"} · ${d.availability}",
            color = OpTextSecondary,
            fontSize = 11.sp,
        )
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
