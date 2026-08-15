package com.engine.nativeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.engine.nativeai.ui.EngineScreen
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
                EngineScreen(
                    engine = engine,
                    registry = registry,
                    providerRegistry = providerRegistry,
                    prefs = prefs,
                    discovery = discovery,
                    initialPrompt = initialPrompt,
                )
            }
        }
    }

    override fun onDestroy() {
        engine.close()
        super.onDestroy()
    }
}
