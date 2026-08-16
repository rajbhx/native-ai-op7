package com.engine.nativeai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Single authoritative engine-service state for the UI (Track A R3). */
enum class EngineServiceState {
    STOPPED, STARTING, READY, BUSY, ERROR,
}

/**
 * Phase 4 spec — LMK protection & background engine (original spec Phase 4).
 *
 * A foreground service (specialUse) holds the NativeEngine and MemoryDatabase
 * instances so OxygenOS does not kill the native process in the background.
 * It also runs the RSS watchdog against the 1.5 GB ceiling and periodically
 * checks the verified-experience count against the LoRA eligibility gate —
 * it never trains silently (the dataset is preserved for external training).
 *
 * Phase A4: lifecycle state is driven by [EngineServiceStateMachine] — real
 * transitions only (STARTING -> READY -> BUSY/READY, ERROR on init failure,
 * STOPPED on destroy). onDestroy also shuts down the shared execution layer
 * so no managed shell process survives the service.
 */
class EngineForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: NativeEngine? = null
    private var memory: MemoryDatabase? = null
    private var training: SelfLearningPipeline? = null
    private var watchdog: MemoryWatchdog? = null
    private var lastEligibilityCheck = 0L

    override fun onCreate() {
        super.onCreate()
        createChannel()
        machine.start()
        publishState()
        startForeground(NOTIFICATION_ID, buildNotification("Engine starting"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            if (engine == null) {
                engine = NativeEngine()
                memory = MemoryDatabase(
                    this,
                    StoragePaths.memoryDbPath(this, ModelPreferences(this)),
                )
                training = SelfLearningPipeline(
                    memory!!,
                    File(StoragePaths.dataDir(this, ModelPreferences(this)), "training"),
                )
                watchdog = MemoryWatchdog(engine!!)
                scope.launch { watchdogLoop() }
            }
            machine.ready()
            publishState()
            notify("Engine online \u00b7 ${machine.state}")
        } catch (e: Exception) {
            machine.error()
            publishState()
            notify("Engine error \u00b7 ${e.message?.take(60) ?: "init failed"} (retrying)")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun publishState() {
        _state.value = machine.state
    }

    private suspend fun watchdogLoop() {
        var tick = 0
        while (scope.isActive) {
            try {
                val snap = watchdog?.snapshot()
                if (snap != null) {
                    val mb = snap.rssBytes / (1024 * 1024)
                    if (snap.overLimit) {
                        // Honest watchdog: report the breach; the caller decides
                        // to reduce context / abort. Never silently exceed.
                        notify("RSS ${mb}MB over ${snap.ceilingBytes / (1024 * 1024)}MB ceiling")
                    } else if (tick % 6 == 0) {
                        notify("Engine online \u00b7 RSS ${mb}MB")
                    }
                }
                // Learning eligibility gate (never auto-trains).
                if (tick % 30 == 0) {
                    checkLearningEligibility()
                }
            } catch (e: Exception) {
                // Watchdog must never crash the service.
            }
            tick++
            delay(10_000)
        }
    }

    private suspend fun checkLearningEligibility() {
        if (System.currentTimeMillis() - lastEligibilityCheck < 5 * 60_000L) return
        lastEligibilityCheck = System.currentTimeMillis()
        val memory = memory ?: return
        val training = training ?: return
        val export = training.exportVerifiedTrainingData()
        val eligibility = training.triggerBackgroundFinetune(export)
        if (eligibility.eligible) {
            notify("Training eligibility met \u2014 preserved for external training")
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Native AI Engine",
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Native AI Engine")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun notify(text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(text))
        }
    }

    override fun onDestroy() {
        machine.stop()
        publishState()
        scope.cancel()
        // Graceful shutdown (Phase A4): terminate managed exec processes
        // before releasing the engine; never leave orphaned shells.
        runCatching { ExecutionManager.shared(this).shutdown() }
        engine?.close()
        notify("Engine stopped")
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "native_ai_engine"
        private const val NOTIFICATION_ID = 1001

        private val machine = EngineServiceStateMachine()
        private val _state = MutableStateFlow(EngineServiceState.STOPPED)
        val state: StateFlow<EngineServiceState> = _state.asStateFlow()

        /** Agent/generation activity mirrors into BUSY/READY (Phase A4). */
        fun reportBusy(busy: Boolean) {
            machine.busy(busy)
            _state.value = machine.state
        }

        fun start(context: Context) {
            val intent = Intent(context, EngineForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, EngineForegroundService::class.java))
        }
    }
}
