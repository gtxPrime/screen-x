package com.gxdevs.screenx

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.gxdevs.screenx.data.SettingsManager
import com.gxdevs.screenx.service.ScreenRecordService
import com.gxdevs.screenx.ui.screens.HomeScreen
import com.gxdevs.screenx.ui.screens.GalleryScreen
import com.gxdevs.screenx.ui.screens.VideoTrimmerScreen
import com.gxdevs.screenx.ui.theme.ScreenXTheme
import com.gxdevs.screenx.utils.RecordedVideo
import com.gxdevs.screenx.utils.VideoHelper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ScreenState {
    HOME,
    GALLERY,
    TRIMMER
}

class MainActivity : ComponentActivity() {

    companion object {
        /** Set to true when launching from the Quick Settings tile to auto-start recording */
        const val EXTRA_START_RECORDING = "extra_start_recording"
    }

    private lateinit var settingsManager: SettingsManager
    private lateinit var projectionManager: MediaProjectionManager

    // State holders
    private val recordedVideos = mutableStateListOf<RecordedVideo>()
    private var isRecordingActive by mutableStateOf(false)

    private val recordingSavedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshVideos()
        }
    }

    // Capture Launcher
    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startRecordingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Overlay Permission Request Launcher
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                checkPermissionsAndStartProjection()
            } else {
                Toast.makeText(this, "Display over other apps permission is required for floating controls", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Scoped Storage Delete Action Launcher (Android 10+)
    private val deleteLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            refreshVideos()
            Toast.makeText(this, "Video deleted successfully", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager(this)
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        val filter = android.content.IntentFilter("com.gxdevs.screenx.action.RECORDING_SAVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(recordingSavedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(recordingSavedReceiver, filter)
        }

        setContent {
            val themeMode by settingsManager.themeModeFlow.collectAsState(initial = "system")
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val (darkTheme, dynamicColor) = remember(themeMode, isSystemDark) {
                when (themeMode) {
                    "dark" -> Pair(true, false)
                    "light" -> Pair(false, false)
                    "dynamic" -> Pair(isSystemDark, true)
                    else -> Pair(isSystemDark, false)
                }
            }
            ScreenXTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
                var currentScreen by remember { mutableStateOf(ScreenState.HOME) }
                var selectedVideoForTrimming by remember { mutableStateOf<RecordedVideo?>(null) }
                
                androidx.activity.compose.BackHandler(enabled = currentScreen != ScreenState.HOME) {
                    when (currentScreen) {
                        ScreenState.GALLERY -> {
                            currentScreen = ScreenState.HOME
                        }
                        ScreenState.TRIMMER -> {
                            currentScreen = if (selectedVideoForTrimming == null) {
                                ScreenState.HOME
                            } else {
                                ScreenState.GALLERY
                            }
                        }
                        ScreenState.HOME -> {}
                    }
                }
                
                // Track service state locally
                LaunchedEffect(Unit) {
                    while (true) {
                        isRecordingActive = ScreenRecordService.isRecording
                        kotlinx.coroutines.delay(1000)
                    }
                }

                // Initial fetch
                LaunchedEffect(Unit) {
                    refreshVideos()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            ScreenState.HOME -> {
                                HomeScreen(
                                    videos = recordedVideos,
                                    onStartRecordingClick = { handleRecordToggle() },
                                    onDeleteVideo = { deleteVideoFile(it) },
                                    isRecordingActive = isRecordingActive,
                                    settingsManager = settingsManager,
                                    onScreenshotClick = { triggerScreenshot() },
                                    onViewAllClick = { currentScreen = ScreenState.GALLERY },
                                    onTrimVideoClick = {
                                        selectedVideoForTrimming = null
                                        currentScreen = ScreenState.TRIMMER
                                    }
                                )
                            }
                            ScreenState.GALLERY -> {
                                GalleryScreen(
                                    videos = recordedVideos,
                                    onBackClick = { currentScreen = ScreenState.HOME },
                                    onDeleteVideo = { deleteVideoFile(it) },
                                    onTrimVideoClick = { video ->
                                        selectedVideoForTrimming = video
                                        currentScreen = ScreenState.TRIMMER
                                    },
                                    settingsManager = settingsManager
                                )
                            }
                            ScreenState.TRIMMER -> {
                                VideoTrimmerScreen(
                                    initialVideo = selectedVideoForTrimming,
                                    videos = recordedVideos,
                                    onBackClick = {
                                        currentScreen = if (selectedVideoForTrimming == null) {
                                            ScreenState.HOME
                                        } else {
                                            ScreenState.GALLERY
                                        }
                                    },
                                    onTrimSuccess = {
                                        refreshVideos()
                                        // Return to wherever the user came from
                                        currentScreen = if (selectedVideoForTrimming == null) {
                                            ScreenState.HOME
                                        } else {
                                            ScreenState.GALLERY
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshVideos()
        isRecordingActive = ScreenRecordService.isRecording
        handleStartRecordingIntent(intent)
        
        lifecycleScope.launch {
            val showFloating = settingsManager.showFloatingFlow.first()
            val mode = settingsManager.floatingShowModeFlow.first()
            if (showFloating && mode.startsWith("All the time") && !ScreenRecordService.isRecording) {
                val serviceIntent = Intent(this@MainActivity, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_START_FLOATING_ONLY
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStartRecordingIntent(intent)
    }

    private fun handleStartRecordingIntent(i: Intent?) {
        if (i?.getBooleanExtra(EXTRA_START_RECORDING, false) == true) {
            i.removeExtra(EXTRA_START_RECORDING)
            if (!ScreenRecordService.isRecording) handleRecordToggle()
        }
    }

    private fun refreshVideos() {
        recordedVideos.clear()
        recordedVideos.addAll(VideoHelper.fetchVideos(this))
    }

    private fun handleRecordToggle() {
        if (isRecordingActive) {
            // Stop service
            val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(serviceIntent)
            isRecordingActive = false
        } else {
            // Check Overlay permission first if needed
            lifecycleScope.launch {
                val showFloating = settingsManager.showFloatingFlow.first()
                if (showFloating && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    // Alert User and request overlay permission
                    requestOverlayPermission()
                } else {
                    checkPermissionsAndStartProjection()
                }
            }
        }
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "Please enable 'Display over other apps' to use floating controls", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun checkPermissionsAndStartProjection() {
        lifecycleScope.launch {
            val audioSource = settingsManager.audioSourceFlow.first()
            val permissionsToRequest = mutableListOf<String>()

            // Audio Permission
            if (audioSource != "None" && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
            }

            // Notification Permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }

            // Storage Permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            } else {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        isRecordingActive = true
    }

    @Suppress("DEPRECATION")
    private fun deleteVideoFile(video: RecordedVideo) {
        val deleted = VideoHelper.deleteVideo(this, video, onRecoverableException = { intent ->
            val sender = intent.getParcelableExtra<android.content.IntentSender>("intent_sender")
            if (sender != null) {
                deleteLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(sender).build()
                )
            }
        })
        
        if (deleted) {
            refreshVideos()
            Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerScreenshot() {
        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_SCREENSHOT
        }
        startService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(recordingSavedReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
