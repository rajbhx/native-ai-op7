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
        val modelFile = File(filesDir, "models/model.gguf")
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
        setContent {
            OxygenOSTheme {
                EngineScreen(engine = engine, registry = registry)
            }
        }
    }

    override fun onDestroy() {
        engine.close()
        super.onDestroy()
    }
}
