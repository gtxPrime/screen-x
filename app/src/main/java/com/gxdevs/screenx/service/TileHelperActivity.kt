package com.gxdevs.screenx.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gxdevs.screenx.data.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.core.net.toUri

/**
 * Transparent helper activity launched from the Quick Settings tile
 * to request screen recording and overlay permissions without showing the main app UI.
 */
class TileHelperActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var projectionManager: MediaProjectionManager

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            checkPermissionsAndStartProjection()
        } else {
            Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: true
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_VIDEO] ?: true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: true
        } else {
            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: true
        }

        if (recordAudioGranted && storageGranted) {
            startScreenCaptureIntent()
        } else {
            Toast.makeText(this, "Required permissions denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startRecordingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(this)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        if (ScreenRecordService.isRecording) {
            val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(serviceIntent)
            finish()
            return
        }
        if (AdbRecordService.isRecording) {
            val serviceIntent = Intent(this, AdbRecordService::class.java).apply {
                action = AdbRecordService.ACTION_STOP_ADB
            }
            startService(serviceIntent)
            finish()
            return
        }

        lifecycleScope.launch {
            val adbEnabled = settingsManager.adbEnabledFlow.first()
            val captureMode = settingsManager.adbCaptureModeFlow.first()
            if (adbEnabled && captureMode == "adb") {
                val isAdbPaired = settingsManager.adbPairedFlow.first()
                if (!isAdbPaired) {
                    Toast.makeText(
                        this@TileHelperActivity,
                        "Wireless ADB is not paired. Please open ScreenX to pair.",
                        Toast.LENGTH_LONG
                    ).show()
                    val appIntent = Intent(this@TileHelperActivity, com.gxdevs.screenx.MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(appIntent)
                    finish()
                    return@launch
                }
                val startIntent = Intent(this@TileHelperActivity, AdbRecordService::class.java).apply {
                    action = AdbRecordService.ACTION_START_ADB
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(startIntent)
                    } else {
                        startService(startIntent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TileHelperActivity", "Failed to start AdbRecordService", e)
                }
                finish()
                return@launch
            }

            val showFloating = settingsManager.showFloatingFlow.first()
            if (showFloating && !Settings.canDrawOverlays(this@TileHelperActivity)) {
                Toast.makeText(this@TileHelperActivity, "Please enable 'Display over other apps'", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri()
                )
                overlayPermissionLauncher.launch(intent)
            } else {
                checkPermissionsAndStartProjection()
            }
        }
    }

    private fun checkPermissionsAndStartProjection() {
        lifecycleScope.launch {
            val audioSource = settingsManager.audioSourceFlow.first()
            val permissionsToRequest = mutableListOf<String>()

            if (audioSource != "None" && ContextCompat.checkSelfPermission(this@TileHelperActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this@TileHelperActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            // Storage Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this@TileHelperActivity, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this@TileHelperActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            } else {
                if (ContextCompat.checkSelfPermission(this@TileHelperActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }

            if (permissionsToRequest.isNotEmpty()) {
                requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                startScreenCaptureIntent()
            }
        }
    }

    private fun startScreenCaptureIntent() {
        val captureIntent = projectionManager.createScreenCaptureIntent()
        if (Build.VERSION.SDK_INT >= 34) {
            val config = android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
            captureIntent.putExtra("android.media.projection.extra.EXTRA_MEDIA_PROJECTION_CONFIG", config)
        }
        captureLauncher.launch(captureIntent)
    }

    private fun startRecordingService(resultCode: Int, resultData: Intent) {
        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, resultData)
        }
        startForegroundService(serviceIntent)
    }
}
