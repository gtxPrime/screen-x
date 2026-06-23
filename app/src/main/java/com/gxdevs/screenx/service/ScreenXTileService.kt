package com.gxdevs.screenx.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.gxdevs.screenx.MainActivity
import com.gxdevs.screenx.R

/**
 * Quick Settings tile for ScreenX.
 * - When recording: shows ACTIVE state, tap → stop recording
 * - When idle: shows INACTIVE state, tap → opens app to start recording
 * - Supports Android 7.0+ (API 24)
 */
class ScreenXTileService : TileService() {

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
        // Close the notification shade
        try {
            @Suppress("DEPRECATION")
            val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(closeIntent)
        } catch (_: Exception) {}

        if (ScreenRecordService.isRecording) {
            // Stop recording directly via service
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(stopIntent)
            // Tile updates when onStartListening fires again after stop
        } else {
            // Launch helper activity — MediaProjection permission requires an Activity
            unlockAndRun {
                val launchIntent = Intent(this, TileHelperActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY)
                }
                if (Build.VERSION.SDK_INT >= 34) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this,
                        0,
                        launchIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(launchIntent)
                }
            }
        }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_qs_record)
        if (ScreenRecordService.isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop Recording"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = if (ScreenRecordService.isPaused) "Paused" else "Recording…"
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
