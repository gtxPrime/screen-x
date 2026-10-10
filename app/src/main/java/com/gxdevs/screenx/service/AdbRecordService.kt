package com.gxdevs.screenx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gxdevs.screenx.MainActivity
import com.gxdevs.screenx.data.AdbManager
import com.gxdevs.screenx.data.SettingsManager
import android.content.ComponentName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Foreground service that runs `screenrecord` via the ADB TCP connection.
 *
 * Lifecycle:
 *   ACTION_START_ADB → runs screenrecord for up to TIME_LIMIT_SECS seconds
 *                       (Android's hard 3-min limit)
 *                       auto-stops when screenrecord exits, MediaScans the file.
 *   ACTION_STOP_ADB  → sends SIGINT to screenrecord, letting it finalize the file.
 */
class AdbRecordService : LifecycleService() {

    companion object {
        private const val TAG = "AdbRecordService"

        const val CHANNEL_ID      = "ScreenX_ADB_Channel"
        const val NOTIFICATION_ID = 889

        const val ACTION_START_ADB = "com.gxdevs.screenx.action.START_ADB"
        const val ACTION_STOP_ADB  = "com.gxdevs.screenx.action.STOP_ADB"

        /** screenrecord --time-limit in seconds. Stay under 180 so it saves cleanly. */
        const val TIME_LIMIT_SECS = 170

        @Volatile var isRecording = false
            private set

        private val _isRecordingFlow = MutableStateFlow(false)
        val isRecordingFlow: StateFlow<Boolean> = _isRecordingFlow.asStateFlow()
    }

    private lateinit var settingsManager: SettingsManager
    private var outputPath: String? = null
    @Volatile private var userStopRequested = false

    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                lifecycleScope.launch {
                    val stopOnScreenOff = settingsManager.stopOnScreenOffFlow.first()
                    if (stopOnScreenOff) {
                        if (isRecording) {
                            Log.i(TAG, "Screen off detected — stopping stealth recording")
                            userStopRequested = true
                            stopAdbRecording()
                        }
                        if (ScreenRecordService.isRecording) {
                            Log.i(TAG, "Screen off detected — stopping standard recording")
                            try {
                                val stopIntent = Intent(this@AdbRecordService, ScreenRecordService::class.java).apply {
                                    action = ScreenRecordService.ACTION_STOP
                                }
                                startService(stopIntent)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to send STOP to ScreenRecordService", e)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        createNotificationChannel()

        val screenOffFilter = android.content.IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenOffReceiver, screenOffFilter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_ADB -> startAdbRecording()
            ACTION_STOP_ADB  -> stopAdbRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    // ── Start ────────────────────────────────────────────────────────────────

    private fun startAdbRecording() {
        if (isRecording) return
        userStopRequested = false

        try {
            val notification = buildNotification("Stealth Recording…")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, 0)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isRecording = true
            _isRecordingFlow.value = true
            notifyTileService()
            showToast("Recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            showToast("Failed to start Stealth service: ${e.message}")
            finish(success = false)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var recordingSuccess = false
            val savedFiles = mutableListOf<File>()
            try {
                // 1. Verify ADB is paired
                val isPaired = settingsManager.adbPairedFlow.first()
                if (!isPaired) {
                    showToast("Wireless Stealth pairing is not set up. Please open ScreenX Settings to pair.")
                    return@launch
                }

                // 2. Ensure ADB is connected (reusing port)
                val connected = AdbManager.reconnectIfNeeded(this@AdbRecordService)
                if (!connected) {
                    showToast("Could not connect to Wireless ADB. Ensure Wireless Debugging is ON in Developer Options.")
                    return@launch
                }

                val mgr = AdbManager.manager
                if (mgr == null) {
                    showToast("Stealth recording connection is not ready. Please re-pair in Settings.")
                    return@launch
                }

                // Output directory: /sdcard/Movies/ScreenX
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ScreenX"
                )
                dir.mkdirs()

                // Calculate display metrics for --size to prevent resolution mismatch on app switch
                val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager
                val metrics = android.util.DisplayMetrics()
                if (wm != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val bounds = wm.maximumWindowMetrics.bounds
                        metrics.widthPixels = bounds.width()
                        metrics.heightPixels = bounds.height()
                    } else {
                        @Suppress("DEPRECATION")
                        wm.defaultDisplay.getRealMetrics(metrics)
                    }
                }
                val width = metrics.widthPixels and 1.inv()
                val height = metrics.heightPixels and 1.inv()
                val sizeParam = if (width > 0 && height > 0) " --size ${width}x${height}" else ""

                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                var partIndex = 1
                var consecutiveFailures = 0

                val sessionStartTime = System.currentTimeMillis()
                val maxSessionDurationMs = TIME_LIMIT_SECS * 1000L

                while (!userStopRequested && (System.currentTimeMillis() - sessionStartTime) < maxSessionDurationMs) {
                    val remainingSecs = ((maxSessionDurationMs - (System.currentTimeMillis() - sessionStartTime)) / 1000L)
                        .coerceAtLeast(10L).toInt()
                    val partSuffix = if (partIndex == 1) "" else "_part$partIndex"
                    val outFile = File(dir, "ADB_${dateStr}${partSuffix}.mp4")
                    outputPath = outFile.absolutePath

                    val cmd = "screenrecord --time-limit $remainingSecs$sizeParam ${outFile.absolutePath}"
                    Log.i(TAG, "Running: $cmd")

                    val partStartTime = System.currentTimeMillis()
                    val outputLog = java.lang.StringBuilder()

                    try {
                        mgr.openStream("exec:$cmd").use { stream ->
                            val buf = ByteArray(1024)
                            val inp = stream.openInputStream()
                            var bytesRead: Int
                            while (inp.read(buf).also { bytesRead = it } != -1) {
                                val chunk = String(buf, 0, bytesRead)
                                outputLog.append(chunk)
                                Log.d(TAG, "screenrecord output: $chunk")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "screenrecord stream exception: ${e.message}")
                    }

                    val partDurationMs = System.currentTimeMillis() - partStartTime
                    Log.i(TAG, "screenrecord part $partIndex exited after ${partDurationMs}ms. Output: $outputLog")

                    if (outFile.exists() && outFile.length() > 0) {
                        savedFiles.add(outFile)
                        recordingSuccess = true
                        consecutiveFailures = 0
                        partIndex++
                    } else {
                        consecutiveFailures++
                        Log.w(TAG, "Part $partIndex ended without saving valid video. Failures: $consecutiveFailures")
                        if (consecutiveFailures >= 3) {
                            Log.e(TAG, "Multiple consecutive screenrecord failures, stopping session.")
                            break
                        }
                    }

                    // If user requested stop, break immediately
                    if (userStopRequested) {
                        break
                    }

                    // If screenrecord exited prematurely without user stop (e.g. app switch or surface transition),
                    // allow the foreground window to settle before launching the next recording segment
                    if (partDurationMs < 5000L) {
                        kotlinx.coroutines.delay(400)
                    }
                }

                // Scan all created files into MediaStore
                if (savedFiles.isNotEmpty()) {
                    val paths = savedFiles.map { it.absolutePath }.toTypedArray()
                    val mimeTypes = Array(paths.size) { "video/mp4" }
                    MediaScannerConnection.scanFile(
                        this@AdbRecordService,
                        paths,
                        mimeTypes,
                        null
                    )
                    showToast("Recording stopped")
                    notifyRecordingSaved()
                } else {
                    showToast("Stealth recording ended without saving video")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Stealth recording error", e)
                showToast("Stealth error: ${e.message ?: "Unknown error"}")
            } finally {
                finish(success = recordingSuccess)
            }
        }
    }

    // ── Stop ─────────────────────────────────────────────────────────────────

    private fun stopAdbRecording() {
        if (!isRecording) return
        userStopRequested = true
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Send SIGINT so screenrecord finalises the MP4 properly
                AdbManager.manager?.openStream("exec:pkill -2 screenrecord")?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Could not send SIGINT to screenrecord: ${e.message}")
            }
            // The running shell() call in startAdbRecording will then return and finish naturally
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun finish(success: Boolean) {
        isRecording = false
        _isRecordingFlow.value = false
        notifyTileService()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifyTileService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                android.service.quicksettings.TileService.requestListeningState(
                    this,
                    ComponentName(this, ScreenXTileService::class.java)
                )
            } catch (_: Exception) {}
        }
    }

    private fun showToast(msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    /** Broadcast so MainActivity refreshes the gallery */
    private fun notifyRecordingSaved() {
        val intent = Intent("com.gxdevs.screenx.action.RECORDING_SAVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sendBroadcast(intent, null)
        } else {
            sendBroadcast(intent)
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Stealth Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Status of Stealth screen recording" }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val mainIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 20,
            Intent(this, AdbRecordService::class.java).apply { action = ACTION_STOP_ADB },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenX — Stealth Recording")
            .setContentText(status)
            .setSmallIcon(com.gxdevs.screenx.R.drawable.ic_notification)
            .setContentIntent(mainIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }
}
