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

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager(this)
        createNotificationChannel()
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            showToast("Failed to start Stealth service: ${e.message}")
            finish(success = false)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var recordingSuccess = false
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

                // Build output file path  /sdcard/Movies/ScreenX/ADB_YYYYMMDD_HHmmss.mp4
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "ScreenX"
                )
                dir.mkdirs()
                val outFile = File(dir, "ADB_$dateStr.mp4")
                outputPath = outFile.absolutePath

                // The stream blocks until screenrecord exits
                // (either at TIME_LIMIT_SECS or when SIGINT arrives via ACTION_STOP_ADB)
                val cmd = "screenrecord --time-limit $TIME_LIMIT_SECS ${outFile.absolutePath}"
                Log.i(TAG, "Running: $cmd")
                mgr.openStream("exec:$cmd").use { stream ->
                    // Drain output — the stream closes when screenrecord exits
                    val buf = ByteArray(1024)
                    try {
                        val inp = stream.openInputStream()
                        while (inp.read(buf) != -1) { /* drain */ }
                    } catch (_: Exception) {}
                }
                Log.i(TAG, "screenrecord finished")

                // Scan into MediaStore
                if (outFile.exists() && outFile.length() > 0) {
                    recordingSuccess = true
                    MediaScannerConnection.scanFile(
                        this@AdbRecordService,
                        arrayOf(outFile.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                    showToast("Stealth recording saved ✓")
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
}
