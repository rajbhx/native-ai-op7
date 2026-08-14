package com.engine.nativeai

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * OxygenOS "NEVER SETTLE" test UI (phase 1-3 device validation): engine
 * controls, Model Hub cards, segmented routing selector and the live Agent
 * Trace with a Horizon Light pulse during generation.
 */
class MainActivity : Activity() {
    private val engine = NativeEngine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var registry: ModelRegistry
    private var loaded = false
    private var selectedModeIndex = 0
    private var horizonAnimator: ObjectAnimator? = null

    private val modes = listOf(
        RoutingMode.HYBRID, RoutingMode.FREE_FIRST, RoutingMode.OFFLINE_ONLY,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val prompt = findViewById<EditText>(R.id.prompt_input)
        val output = findViewById<TextView>(R.id.output_view)
        val status = findViewById<TextView>(R.id.status_view)
        val loadBtn = findViewById<Button>(R.id.load_btn)
        val genBtn = findViewById<Button>(R.id.generate_btn)
        val statsBtn = findViewById<Button>(R.id.stats_btn)
        val agentBtn = findViewById<Button>(R.id.agent_btn)
        val horizon = findViewById<View>(R.id.horizon_light)
        val modelFile = File(filesDir, "models/model.gguf")

        registry = ModelRegistry(File(filesDir, "models/catalog.json")).apply {
            register(LocalModelProvider(engine, EngineConfig(modelFile.absolutePath)))
            ModelCatalog.freeRemoteSeeds().forEach { addDescriptor(it) }
            loadCatalog()
        }

        val modeButtons = listOf(
            findViewById<Button>(R.id.mode_auto_btn),
            findViewById<Button>(R.id.mode_free_btn),
            findViewById<Button>(R.id.mode_offline_btn),
        )
        fun selectMode(index: Int) {
            selectedModeIndex = index
            modeButtons.forEachIndexed { i, b ->
                val selected = i == index
                b.setBackgroundResource(if (selected) R.drawable.bg_pill_red else R.drawable.bg_pill_outline)
                b.setTextColor(getColor(if (selected) R.color.op_text else R.color.op_text_secondary))
            }
        }
        modeButtons[0].setOnClickListener { selectMode(0) }
        modeButtons[1].setOnClickListener { selectMode(1) }
        modeButtons[2].setOnClickListener { selectMode(2) }
        selectMode(0)

        renderModelHub()

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

        agentBtn.setOnClickListener {
            scope.launch {
                if (!loaded) {
                    status.text = "Load a model first."
                    return@launch
                }
                agentBtn.isEnabled = false
                output.text = ""
                status.text = "agent running…"
                startHorizonPulse(horizon)
                val memory = MemoryDatabase(this@MainActivity)
                val tools = ToolRegistry().apply {
                    register(MemorySearchTool(memory))
                    register(CalculatorTool())
                    register(SystemInfoTool(engine, memory))
                    register(WebSearchTool(LocalFallbackProvider()))
                    register(FileSearchTool(filesDir))
                    register(ModelInfoTool(registry))
                    register(FinalAnswerTool())
                }
                val agent = ThinkingAgent(
                    router = ModelRouter(mode = modes[selectedModeIndex]),
                    registry = registry,
                    memory = memory,
                    tools = tools,
                    networkAvailable = hasNetwork(),
                )
                try {
                    val sb = StringBuilder()
                    var done = false
                    agent.run(prompt.text.toString(), GenerationConfig(maxTokens = 256))
                        .collect { ev ->
                            when (ev) {
                                is AgentEvent.Token -> {
                                    sb.append(ev.text)
                                    output.text = sb.toString()
                                }
                                is AgentEvent.Routed -> {
                                    sb.append("\n\n[model] ${ev.modelId} | ${ev.provider} | " +
                                        "${ev.costTier} | ${ev.taskType}\n")
                                    output.text = sb.toString()
                                }
                                is AgentEvent.ToolCall -> {
                                    sb.append("\n[tool] ${ev.tool}(${ev.input})")
                                    output.text = sb.toString()
                                }
                                is AgentEvent.Observation -> {
                                    sb.append("\n[obs] ${ev.output.take(240)}")
                                    output.text = sb.toString()
                                }
                                is AgentEvent.Verification -> {
                                    sb.append("\n[verify] ${ev.tool}: " +
                                        if (ev.passed) "passed" else "failed")
                                    output.text = sb.toString()
                                }
                                is AgentEvent.Final -> {
                                    done = true
                                    sb.append("\n\nFINAL: ${ev.answer}")
                                    output.text = sb.toString()
                                }
                                is AgentEvent.Error -> status.text = "agent error: ${ev.message}"
                            }
                        }
                    status.text = if (done) "agent done" else "agent ended without final answer"
                } catch (e: Exception) {
                    status.text = "agent failed: ${e.message}"
                } finally {
                    stopHorizonPulse(horizon)
                    agentBtn.isEnabled = true
                }
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

    private fun renderModelHub() {
        val list = findViewById<LinearLayout>(R.id.model_hub_list)
        list.removeAllViews()
        registry.list().forEach { d ->
            list.addView(buildModelCard(d))
        }
    }

    private fun buildModelCard(d: ModelDescriptor): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundResource(R.drawable.bg_card)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) }
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = d.displayName
            setTextColor(getColor(R.color.op_text))
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        })
        top.addView(pill(d.kind.name, R.drawable.bg_pill_dark, R.color.op_text_secondary))
        when (d.costTier) {
            ModelCostTier.FREE -> top.addView(pill("FREE", R.drawable.bg_pill_red, R.color.op_text))
            ModelCostTier.PAID -> top.addView(pill("PAID", R.drawable.bg_pill_dark, R.color.op_text_secondary))
            ModelCostTier.UNKNOWN -> top.addView(pill("?", R.drawable.bg_pill_outline, R.color.op_text_secondary))
        }
        card.addView(top)
        card.addView(TextView(this).apply {
            text = "provider ${d.provider} · ctx ${d.contextLength} · tools ${yn(d.supportsTools)} · " +
                "vision ${yn(d.supportsVision)} · coding ${d.codingScore ?: "?"} · " +
                "reasoning ${d.reasoningScore ?: "?"} · ${d.availability}"
            setTextColor(getColor(R.color.op_text_secondary))
            textSize = 11f
            setPadding(0, dp(4), 0, 0)
        })
        return card
    }

    private fun pill(text: String, bg: Int, textColor: Int): TextView =
        TextView(this).apply {
            this.text = text
            setBackgroundResource(bg)
            setTextColor(getColor(textColor))
            textSize = 10f
            setPadding(dp(6), dp(2), dp(6), dp(2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = dp(8) }
        }

    private fun yn(b: Boolean): String = if (b) "yes" else "no"

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun startHorizonPulse(view: View) {
        view.visibility = View.VISIBLE
        horizonAnimator?.cancel()
        horizonAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 0.35f, 1f).apply {
            duration = 600
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopHorizonPulse(view: View) {
        horizonAnimator?.cancel()
        view.alpha = 1f
        view.visibility = View.GONE
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onDestroy() {
        horizonAnimator?.cancel()
        scope.cancel()
        engine.close()
        super.onDestroy()
    }
}
