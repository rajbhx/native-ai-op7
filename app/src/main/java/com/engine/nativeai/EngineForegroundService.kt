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
        _state.value = EngineServiceState.STARTING
        startForeground(NOTIFICATION_ID, buildNotification("Engine online"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (engine == null) {
            engine = NativeEngine()
            memory = MemoryDatabase(this)
            training = SelfLearningPipeline(memory!!, File(filesDir, "training"))
            watchdog = MemoryWatchdog(engine!!)
            scope.launch { watchdogLoop() }
        }
        _state.value = EngineServiceState.READY
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
                        notify("Engine online · RSS ${mb}MB")
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
            notify("Training eligibility met — preserved for external training")
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
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        _state.value = EngineServiceState.STOPPED
        scope.cancel()
        engine?.close()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "native_ai_engine"
        private const val NOTIFICATION_ID = 1001

        private val _state = MutableStateFlow(EngineServiceState.STOPPED)
        val state: StateFlow<EngineServiceState> = _state.asStateFlow()

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
