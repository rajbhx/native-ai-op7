package com.engine.nativeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.engine.nativeai.ui.EngineScreen
import com.engine.nativeai.ui.MemoryScreen
import com.engine.nativeai.ui.SourcesScreen
import com.engine.nativeai.ui.OxygenOSTheme
import java.io.File

/**
 * OxygenOS Compose dashboard (blueprint Phase 6): Model Hub + Live Agent
 * Trace. All engine logic stays in NativeEngine / ModelRegistry / agent —
 * this activity only wires them into the UI.
 */
class MainActivity : ComponentActivity() {
    private val engine = NativeEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Automation/test hook: seed the prompt field via
        // am start ... --es prompt "text" (avoids flaky IME injection).
        val initialPrompt = intent?.getStringExtra("prompt")
        val providerRegistry = ProviderRegistry()
        val prefs = ModelPreferences(this)
        // One storage resolution point (golden: models dir, catalog and the
        // memory DB must never disagree about where user data lives).
        val localLibrary = LocalModelLibrary(StoragePaths.modelsDir(this, prefs))
        providerRegistry.setBaseUrl(ModelCatalog.ZEN_PROVIDER, prefs.zenBaseUrl)
        val registry = ModelRegistry(StoragePaths.catalogFile(this, prefs)).apply {
            localLibrary.syncInto(
                this,
                engine,
                applicationInfo.nativeLibraryDir,
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
                var showSources by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = showMemory || showSources) {
                    showMemory = false
                    showSources = false
                }
                when {
                    showMemory -> MemoryScreen(onBack = { showMemory = false }, prefs = prefs)
                    showSources -> SourcesScreen(onBack = { showSources = false }, prefs = prefs)
                    else -> EngineScreen(
                        engine = engine,
                        registry = registry,
                        providerRegistry = providerRegistry,
                        prefs = prefs,
                        discovery = discovery,
                        localLibrary = localLibrary,
                        initialPrompt = initialPrompt,
                        onOpenMemory = { showMemory = true },
                        onOpenSources = { showSources = true },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        engine.close()
        super.onDestroy()
    }
}
