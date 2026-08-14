package com.engine.nativeai

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Minimal test UI for the engine (phase 1/2 device validation). Not a product
 * UI: load a GGUF model, generate, inspect memory/backend stats.
 */
class MainActivity : Activity() {
    private val engine = NativeEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prompt = findViewById<EditText>(R.id.prompt_input)
        val output = findViewById<TextView>(R.id.output_view)
        val status = findViewById<TextView>(R.id.status_view)
        val loadBtn = findViewById<Button>(R.id.load_btn)
        val genBtn = findViewById<Button>(R.id.generate_btn)
        val statsBtn = findViewById<Button>(R.id.stats_btn)
        val modelFile = File(filesDir, "models/model.gguf")

        status.text = "Engine library loaded. Put a GGUF at:\n${modelFile.absolutePath}"

        loadBtn.setOnClickListener {
            scope.launch {
                loadBtn.isEnabled = false
                status.text = if (!modelFile.exists()) {
                    "Model not found:\n${modelFile.absolutePath}\nCopy a GGUF there, then retry."
                } else {
                    try {
                        engine.init(EngineConfig(modelFile.absolutePath))
                        loaded = true
                        "Model loaded: ${modelFile.name}"
                    } catch (e: Exception) {
                        "init failed: ${e.message}"
                    }
                }
                loadBtn.isEnabled = true
            }
        }

        genBtn.setOnClickListener {
            scope.launch {
                if (!loaded) {
                    status.text = "Load a model first."
                    return@launch
                }
                genBtn.isEnabled = false
                output.text = ""
                status.text = "generating…"
                try {
                    val sb = StringBuilder()
                    engine.generateStream(
                        prompt.text.toString(),
                        GenerationConfig(maxTokens = 64),
                    ).collect { sb.append(it); output.text = sb.toString() }
                    status.text = "done (${sb.length} chars)"
                } catch (e: Exception) {
                    status.text = "generate failed: ${e.message}"
                }
                genBtn.isEnabled = true
            }
        }

        statsBtn.setOnClickListener {
            scope.launch {
                if (!loaded) {
                    status.text = "Load a model first."
                    return@launch
                }
                try {
                    val s = engine.memoryStats()
                    status.text = "model=${s.modelBytes / (1024 * 1024)} MB | ctx=${s.nCtx} | " +
                        "kv=${s.kvTypeK}/${s.kvTypeV} | threads=${s.threads} | gpu=${s.gpuLayers} | " +
                        "gpuOffload=${s.gpuOffloadSupported}"
                } catch (e: Exception) {
                    status.text = "stats failed: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        engine.close()
        super.onDestroy()
    }
}
