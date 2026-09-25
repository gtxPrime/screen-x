package com.gxdevs.screenx.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.gxdevs.screenx.R
import com.gxdevs.screenx.data.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for ScreenX.
 * - Aware of both standard MediaProjection and wireless ADB recording.
 * - When recording (standard or ADB): shows ACTIVE state, tap → stops recording.
 * - When idle: checks Settings. If ADB capture mode is active, starts ADB recording directly.
 *   Otherwise opens TileHelperActivity to prompt for MediaProjection.
 */
class ScreenXTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        try {
            @Suppress("DEPRECATION")
            val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeIntent)
        } catch (_: Exception) {}

        val isStandard = ScreenRecordService.isRecording
        val isAdb = AdbRecordService.isRecording

        if (isStandard) {
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(stopIntent)
            updateTile()
        } else if (isAdb) {
            val stopIntent = Intent(this, AdbRecordService::class.java).apply {
                action = AdbRecordService.ACTION_STOP_ADB
            }
            startService(stopIntent)
            updateTile()
        } else {
            serviceScope.launch {
                val settingsManager = SettingsManager(this@ScreenXTileService)
                val adbEnabled = settingsManager.adbEnabledFlow.first()
                val captureMode = settingsManager.adbCaptureModeFlow.first()

                if (adbEnabled && captureMode == "adb") {
                    val isAdbPaired = settingsManager.adbPairedFlow.first()
                    if (!isAdbPaired) {
                        unlockAndRun {
                            val launchIntent = Intent(this@ScreenXTileService, com.gxdevs.screenx.MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            if (Build.VERSION.SDK_INT >= 34) {
                                val pendingIntent = PendingIntent.getActivity(
                                    this@ScreenXTileService,
                                    0,
                                    launchIntent,
                                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                                )
                                startActivityAndCollapse(pendingIntent)
                            } else {
                                @Suppress("DEPRECATION")
                                startActivityAndCollapse(launchIntent)
                            }
                        }
                        return@launch
                    }
                    // Start ADB record directly without full UI
                    val startIntent = Intent(this@ScreenXTileService, AdbRecordService::class.java).apply {
                        action = AdbRecordService.ACTION_START_ADB
                    }
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(startIntent)
                        } else {
                            startService(startIntent)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ScreenXTileService", "Failed to start AdbRecordService", e)
                    }
                    updateTile()
                } else {
                    // Standard MediaProjection requires activity context
                    unlockAndRun {
                        val launchIntent = Intent(this@ScreenXTileService, TileHelperActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                        }
                        if (Build.VERSION.SDK_INT >= 34) {
                            val pendingIntent = PendingIntent.getActivity(
                                this@ScreenXTileService,
                                0,
                                launchIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            startActivityAndCollapse(pendingIntent)
                        } else {
                            @Suppress("DEPRECATION")
                            startActivityAndCollapse(launchIntent)
                        }
                    }
                }
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_record)

        val isStandard = ScreenRecordService.isRecording
        val isAdb = AdbRecordService.isRecording

        if (isStandard || isAdb) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = if (isAdb) "Stop Stealth Record" else "Stop Recording"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (isAdb) "Stealth Recording…" else if (ScreenRecordService.isPaused) "Paused" else "Recording…"
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "ScreenX"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Tap to record"
            }
        }
        tile.updateTile()
    }
}
