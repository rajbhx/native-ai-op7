package com.engine.nativeai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.engine.nativeai.ui.EngineScreen
import com.engine.nativeai.ui.MemoryScreen
import com.engine.nativeai.ui.OxygenOSTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * OxygenOS Compose dashboard (blueprint Phase 6): Model Hub + Live Agent
 * Trace. All engine logic stays in NativeEngine / ModelRegistry / agent —
 * this activity only wires them into the UI.
 */
class MainActivity : ComponentActivity() {
    private val engine = NativeEngine()

    // Bench mode owns its own scope (A7: no lifecycle-runtime-ktx dependency).
    private val benchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Headless benchmark mode for the Phase 7/8 sweep harness:
        //   am start -W -n com.engine.nativeai/.MainActivity \
        //     --es bench 1 --es run_id <id> --es threads 2 --es ctx 1024 \
        //     --es gpu_layers 0 --es bench_tokens 128 --es bench_categories reasoning,context
        // Emits one NATIVEAI_BENCH JSONL row per category, a done marker, then finishes.
        if (intent?.getStringExtra("bench") == "1") {
            benchScope.launch { runBenchmark(intent!!) }
            return
        }
        // Automation/test hook: seed the prompt field via
        // am start ... --es prompt "text" (avoids flaky IME injection).
        val initialPrompt = intent?.getStringExtra("prompt")
        val modelFile = File(filesDir, "models/model.gguf")
        val providerRegistry = ProviderRegistry()
        val prefs = ModelPreferences(this)
        providerRegistry.setBaseUrl(ModelCatalog.ZEN_PROVIDER, prefs.zenBaseUrl)
        val registry = ModelRegistry(File(filesDir, "models/catalog.json")).apply {
            register(
                LocalModelProvider(
                    engine,
                    EngineConfig(modelFile.absolutePath, nativeLibDir = applicationInfo.nativeLibraryDir),
                ),
            )
            ModelCatalog.freeRemoteSeeds().forEach { addDescriptor(it) }
            loadCatalog()
        }
        // Re-hydrate remote providers so the persisted selection (and any
        // cached discovered model) routes correctly right after a restart.
        RemoteProviderBootstrap.ensurePersistedSelection(
            registry,
            providerRegistry,
            prefs.lastSelectedModelId,
        )
        val discovery = ModelDiscoveryService(registry, providerRegistry)
        setContent {
            OxygenOSTheme {
                var showMemory by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showMemory) { showMemory = false }
                if (showMemory) {
                    MemoryScreen(onBack = { showMemory = false })
                } else {
                    EngineScreen(
                        engine = engine,
                        registry = registry,
                        providerRegistry = providerRegistry,
                        prefs = prefs,
                        discovery = discovery,
                        initialPrompt = initialPrompt,
                        onOpenMemory = { showMemory = true },
                    )
                }
            }
        }
    }

    /**
     * Runs the local-model benchmark headlessly and logs every result as one
     * JSONL line. Never crashes the app on a failed cell: failures become an
     * ok=false row plus a done marker so the sweep harness can move on.
     */
    private suspend fun runBenchmark(intent: Intent) {
        val runId = intent.getStringExtra("run_id") ?: "run-${System.currentTimeMillis()}"
        val threads = intent.getStringExtra("threads")?.toIntOrNull() ?: 4
        val contextSize = intent.getStringExtra("ctx")?.toIntOrNull() ?: 2048
        val gpuLayers = intent.getStringExtra("gpu_layers")?.toIntOrNull() ?: 0
        val maxTokens = intent.getStringExtra("bench_tokens")?.toIntOrNull() ?: 64
        val categories = intent.getStringExtra("bench_categories")
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        val modelFile = File(filesDir, "models/model.gguf")
        var ok = true
        try {
            if (!modelFile.isFile) {
                emit(
                    BenchmarkReporter.Report(
                        runId = runId,
                        providerId = LocalModelProvider.LOCAL_MODEL_ID,
                        category = "setup",
                        tokens = 0,
                        durationMs = 0,
                        tokensPerSec = null,
                        firstTokenMs = null,
                        rssBytes = null,
                        kvTypeK = null,
                        kvTypeV = null,
                        nCtx = null,
                        threads = threads,
                        gpuLayers = gpuLayers,
                        ok = false,
                        error = "model not found: $modelFile",
                    ),
                )
                ok = false
            } else {
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    threads = threads,
                    gpuLayers = gpuLayers,
                    contextSize = contextSize,
                    nativeLibDir = applicationInfo.nativeLibraryDir,
                )
                engine.init(config)
                val stats = engine.memoryStats()
                val registry = ModelRegistry(File(filesDir, "models/catalog.json")).apply {
                    register(LocalModelProvider(engine, config))
                }
                val results = ModelBenchmark(registry)
                    .run(LocalModelProvider.LOCAL_MODEL_ID, maxTokens = maxTokens, categories = categories)
                results.forEach { r ->
                    emit(
                        BenchmarkReporter.Report(
                            runId = runId,
                            providerId = r.providerId,
                            category = r.category,
                            tokens = r.tokens,
                            durationMs = r.durationMs,
                            tokensPerSec = r.tokensPerSec,
                            firstTokenMs = r.firstTokenMs,
                            rssBytes = stats.rssBytes,
                            kvTypeK = stats.kvTypeK,
                            kvTypeV = stats.kvTypeV,
                            nCtx = stats.nCtx,
                            threads = stats.threads,
                            gpuLayers = stats.gpuLayers,
                            ok = r.ok,
                            error = r.error,
                        ),
                    )
                }
                ok = results.all { it.ok }
            }
        } catch (e: Exception) {
            ok = false
            emit(
                BenchmarkReporter.Report(
                    runId = runId,
                    providerId = LocalModelProvider.LOCAL_MODEL_ID,
                    category = "fatal",
                    tokens = 0,
                    durationMs = 0,
                    tokensPerSec = null,
                    firstTokenMs = null,
                    rssBytes = null,
                    kvTypeK = null,
                    kvTypeV = null,
                    nCtx = null,
                    threads = threads,
                    gpuLayers = gpuLayers,
                    ok = false,
                    error = e.message ?: e.javaClass.simpleName,
                ),
            )
        } finally {
            Log.i(BenchmarkReporter.TAG, BenchmarkReporter.done(runId, ok))
            engine.close()
            finish()
        }
    }

    private fun emit(report: BenchmarkReporter.Report) {
        Log.i(BenchmarkReporter.TAG, BenchmarkReporter.line(report))
    }

    override fun onDestroy() {
        benchScope.cancel()
        engine.close()
        super.onDestroy()
    }
}
