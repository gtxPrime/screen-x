package com.gxdevs.screenx.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Usb
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import com.composables.icons.lucide.Zap
import com.gxdevs.screenx.data.AdbManager
import com.gxdevs.screenx.data.SettingsManager
import com.gxdevs.screenx.service.PairingInputService
import com.gxdevs.screenx.utils.DeviceCapabilitiesHelper
import com.gxdevs.screenx.utils.NetworkUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Settings Flows
    val resolution by settingsManager.resolutionFlow.collectAsState(initial = "1080p")
    val fps by settingsManager.fpsFlow.collectAsState(initial = 30)
    val bitrate by settingsManager.bitrateFlow.collectAsState(initial = 8000000)
    val audioSource by settingsManager.audioSourceFlow.collectAsState(initial = "Mic")
    val countdown by settingsManager.countdownFlow.collectAsState(initial = 3)
    val showFloating by settingsManager.showFloatingFlow.collectAsState(initial = true)
    val themeMode by settingsManager.themeModeFlow.collectAsState(initial = "system")
    val shakeToStop by settingsManager.shakeToStopFlow.collectAsState(initial = false)
    val orientation by settingsManager.orientationFlow.collectAsState(initial = "Auto")
    val floatingShowMode by settingsManager.floatingShowModeFlow.collectAsState(initial = "Only when recording")
    val safeStorageStop by settingsManager.safeStorageStopFlow.collectAsState(initial = true)
    val safeStorageThresholdMb by settingsManager.safeStorageThresholdMbFlow.collectAsState(initial = 150)
    val adbEnabled by settingsManager.adbEnabledFlow.collectAsState(initial = false)
    val adbCaptureMode by settingsManager.adbCaptureModeFlow.collectAsState(initial = "mediaprojection")
    val adbPaired by settingsManager.adbPairedFlow.collectAsState(initial = false)

    val isAdbRecordingDefault = adbEnabled && adbCaptureMode == "adb"

    LaunchedEffect(isAdbRecordingDefault) {
        if (isAdbRecordingDefault && audioSource != "None") {
            settingsManager.setAudioSource("None")
        }
    }

    // Option Dialog Flags
    var showResDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showCountdownDialog by remember { mutableStateOf(false) }
    var showFloatingShowModeDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showStorageThresholdDialog by remember { mutableStateOf(false) }
    var showAdbModeDialog by remember { mutableStateOf(false) }
    var showAdbLimitationsDialog by remember { mutableStateOf(false) }
    var showAdbConfirmEnableDialog by remember { mutableStateOf(false) }
    var showAdbPairingDialog by remember { mutableStateOf(false) }

    var pendingFloatingMode by remember { mutableStateOf<String?>(null) }
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
            if (pendingFloatingMode != null) {
                coroutineScope.launch {
                    settingsManager.setShowFloating(true)
                    settingsManager.setFloatingShowMode(pendingFloatingMode!!)
                    val mode = pendingFloatingMode
                    pendingFloatingMode = null
                    if (mode?.startsWith("All the time") == true && !com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                        val intent = Intent(context, com.gxdevs.screenx.service.ScreenRecordService::class.java).apply {
                            action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_START_FLOATING_ONLY
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                }
            }
        } else {
            Toast.makeText(context, "Permission required to show floating control ball", Toast.LENGTH_SHORT).show()
            pendingFloatingMode = null
        }
    }

    // Dynamic storage calculations
    val freeSpaceGB = remember {
        try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            (stat.blockSizeLong * stat.availableBlocksLong) / (1024 * 1024 * 1024)
        } catch (_: Exception) {
            42L
        }
    }
    val usedPercent = remember {
        try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total = stat.blockSizeLong * stat.blockCountLong
            val free = stat.blockSizeLong * stat.availableBlocksLong
            (((total - free).toDouble() / total.toDouble()) * 100).toInt()
        } catch (_: Exception) {
            50
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.4).sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "ScreenX Preferences & Controls",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Lucide.ArrowLeft,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. STORAGE & SAFETY
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrionSectionHeader("STORAGE & SAFETY")
                    OrionStackedGroupCard {
                        OrionSettingsSwitchItem(
                            icon = Lucide.HardDrive,
                            title = "Safe Storage Auto-Stop",
                            subtitle = "Automatically stop & finalize recording before storage runs out",
                            checked = safeStorageStop,
                            onCheckedChange = { coroutineScope.launch { settingsManager.setSafeStorageStop(it) } }
                        )
                        OrionSettingsDivider()
                        OrionSettingsValueItem(
                            icon = Lucide.HardDrive,
                            title = "Safe Stop Threshold",
                            subtitle = "Buffer space to ensure recordings finalize without corruption",
                            value = "$safeStorageThresholdMb MB",
                            onClick = { showStorageThresholdDialog = true }
                        )
                        OrionSettingsDivider()
                        OrionSettingsInfoItem(
                            icon = Lucide.HardDrive,
                            title = "Internal Storage Status",
                            subtitle = "$freeSpaceGB GB available • Safe auto-stop ${if (safeStorageStop) "active" else "disabled"}",
                            badge = "$usedPercent% Used"
                        )
                        OrionSettingsDivider()
                        OrionSettingsValueItem(
                            icon = Lucide.Film,
                            title = "Save Destination",
                            subtitle = "Shared system storage directory",
                            value = "Movies/ScreenX",
                            showChevron = false,
                            onClick = {
                                Toast.makeText(context, "Location locked to standard Movies/ScreenX", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // 2. VIDEO CAPTURE
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrionSectionHeader("VIDEO CAPTURE")
                    OrionStackedGroupCard {
                        OrionSettingsValueItem(
                            icon = Lucide.Video,
                            title = "Resolution",
                            subtitle = "Screen capture output resolution",
                            value = resolution,
                            onClick = { showResDialog = true }
                        )
                        OrionSettingsDivider()
                        OrionSettingsValueItem(
                            icon = Lucide.CirclePlay,
                            title = "Frame Rate",
                            subtitle = "Frames per second fluidity",
                            value = "$fps FPS",
                            onClick = { showFpsDialog = true }
                        )
                        OrionSettingsDivider()
                        OrionSettingsValueItem(
                            icon = Lucide.Film,
                            title = "Video Bitrate",
                            subtitle = "Encoding bitrate for video quality",
                            value = "${bitrate / 1000000} Mbps",
                            onClick = { showBitrateDialog = true }
                        )
                        OrionSettingsDivider()
                        OrionSettingsValueItem(
                            icon = when (orientation) {
                                "Auto" -> Lucide.RotateCcw
                                "Portrait" -> Lucide.Smartphone
                                else -> Lucide.Tablet
                            },
                            title = "Capture Orientation",
                            subtitle = "Screen orientation lock or match",
                            value = orientation,
                            onClick = {
                                val nextOri = when (orientation) {
                                    "Auto" -> "Portrait"
                                    "Portrait" -> "Landscape"
                                    else -> "Auto"
                                }
                                coroutineScope.launch { settingsManager.setOrientation(nextOri) }
                            }
                        )
                    }
                }
            }

            // 3. AUDIO
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrionSectionHeader("AUDIO SOURCE")
                    OrionStackedGroupCard {
                        val audioSubtitle = if (isAdbRecordingDefault) {
                            "Locked to No Audio — ADB screen recording does not support sound capture"
                        } else {
                            when (audioSource) {
                                "Mic" -> "Microphone capture for voice & ambient sound"
                                "System" -> "Internal phone audio only (media, games & apps)"
                                "MicSystem" -> "Microphone + internal phone audio mixed together"
                                "None" -> "No audio — video only, no sound recorded"
                                else -> "Microphone capture for voice & ambient sound"
                            }
                        }
                        val audioDisplay = if (isAdbRecordingDefault) {
                            "No Audio (Locked)"
                        } else {
                            when (audioSource) {
                                "Mic" -> "Microphone"
                                "System" -> "Internal Audio"
                                "MicSystem" -> "Mic + Internal Audio"
                                "None" -> "No Audio"
                                else -> "Microphone"
                            }
                        }
                        val audioIcon = if (isAdbRecordingDefault) {
                            Lucide.VolumeX
                        } else {
                            when (audioSource) {
                                "System", "MicSystem" -> Lucide.Volume2
                                "None" -> Lucide.VolumeX
                                else -> Lucide.Mic
                            }
                        }
                        OrionSettingsValueItem(
                            icon = audioIcon,
                            title = "Audio Input",
                            subtitle = audioSubtitle,
                            value = audioDisplay,
                            showChevron = !isAdbRecordingDefault,
                            onClick = {
                                if (isAdbRecordingDefault) {
                                    Toast.makeText(
                                        context,
                                        "Audio is locked to 'No Audio' while ADB recording is set as default",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    showAudioDialog = true
                                }
                            }
                        )
                    }
                }
            }

            // 4. RECORDING CONTROLS
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrionSectionHeader("RECORDING CONTROLS")
                    OrionStackedGroupCard {
                        OrionSettingsValueItem(
                            icon = Lucide.RotateCcw,
                            title = "Start Countdown",
                            subtitle = "Countdown timer before screen capture begins",
                            value = if (countdown == 0) "Disabled" else "${countdown}s",
                            onClick = { showCountdownDialog = true }
                        )
                        OrionSettingsDivider()
                        OrionSettingsSwitchItem(
                            icon = Lucide.Smartphone,
                            title = "Shake to Stop",
                            subtitle = "Shake phone firmly to stop and finalize recording",
                            checked = shakeToStop,
                            onCheckedChange = { coroutineScope.launch { settingsManager.setShakeToStop(it) } }
                        )
                        OrionSettingsDivider()
                        val floatingBallSummary = when {
                            !showFloating -> "Hidden"
                            floatingShowMode.startsWith("All the time") -> "Always Active"
                            else -> "While Recording"
                        }
                        OrionSettingsValueItem(
                            icon = Lucide.CircleDot,
                            title = "Floating Control Ball",
                            subtitle = "Overlay controls for pause, draw, screenshot & stop",
                            value = floatingBallSummary,
                            onClick = { showFloatingShowModeDialog = true }
                        )
                    }
                }
            }

            // 5. APPEARANCE & THEME
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrionSectionHeader("APPEARANCE & THEME")
                    OrionStackedGroupCard {
                        val themeSummary = when (themeMode) {
                            "dark" -> "Dark Mode"
                            "light" -> "Light Mode"
                            "system" -> "System Default"
                            "dynamic" -> "Dynamic Wallpaper"
                            else -> "System Default"
                        }
                        OrionSettingsValueItem(
                            icon = Lucide.Camera,
                            title = "Theme Mode",
                            subtitle = "Material You dynamic colors & tinting",
                            value = themeSummary,
                            onClick = { showThemeDialog = true }
                        )
                    }
                }
            }

            // 6. ADB STEALTH RECORDING
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrionSectionHeader("ADB STEALTH RECORDING")
                    OrionStackedGroupCard {
                        // Merged ADB Switch: turning ON triggers info dialog first
                        OrionSettingsSwitchItem(
                            icon = Lucide.Zap,
                            title = "Enable ADB Recording",
                            subtitle = "Undetectable background capture for apps with record protection (Snapchat, Instagram, banking)",
                            checked = adbEnabled,
                            onCheckedChange = { willEnable ->
                                if (willEnable) {
                                    // Show info dialog FIRST; user confirms with OK to enable
                                    showAdbConfirmEnableDialog = true
                                } else {
                                    coroutineScope.launch { settingsManager.setAdbEnabled(false) }
                                }
                            },
                            iconTint = if (adbEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            iconBackground = if (adbEnabled) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
                        )

                        // ONLY show Pairing and Mode options when ADB is switched ON
                        if (adbEnabled) {
                            OrionSettingsDivider()
                            // Wireless ADB Pairing Row
                            OrionSettingsValueItem(
                                icon = Lucide.Usb,
                                title = "Wireless ADB Pairing",
                                subtitle = if (adbPaired)
                                    "Paired — ADB recording ready to use"
                                else
                                    "Not paired — tap to set up one-time pairing",
                                value = if (adbPaired) "✓ Paired" else "Set Up",
                                iconTint = if (adbPaired)
                                    Color(0xFF2E9E5B)
                                else
                                    MaterialTheme.colorScheme.primary,
                                iconBackground = if (adbPaired)
                                    Color(0xFF2E9E5B).copy(alpha = 0.15f)
                                else
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                onClick = { showAdbPairingDialog = true }
                            )

                            OrionSettingsDivider()
                            val adbModeDisplay = when (adbCaptureMode) {
                                "adb" -> "ADB Only"
                                "ask" -> "Ask Every Time"
                                else -> "MediaProjection (Default)"
                            }
                            OrionSettingsValueItem(
                                icon = Lucide.Settings,
                                title = "Default Recording Mode",
                                subtitle = "Engine triggered by the standard Record button",
                                value = adbModeDisplay,
                                onClick = { showAdbModeDialog = true }
                            )

                            OrionSettingsDivider()
                            OrionSettingsInfoItem(
                                icon = Lucide.Info,
                                title = "ADB Limitations & How It Works",
                                subtitle = "No audio capture, 3-min auto-stop per session. Tap to review full details.",
                                badge = "Stealth",
                                onClick = { showAdbLimitationsDialog = true }
                            )
                        }
                    }
                }
            }

            // 7. ABOUT SCREENX
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OrionSectionHeader("ABOUT SCREENX")
                    OrionStackedGroupCard {
                        OrionSettingsInfoItem(
                            icon = Lucide.Settings,
                            title = "ScreenX Recorder",
                            subtitle = "Version 1.2.0 • Material You Edition",
                            badge = "v1.2.0"
                        )
                        OrionSettingsDivider()
                        val maxFps = DeviceCapabilitiesHelper.getMaxSupportedFps(context)
                        OrionSettingsInfoItem(
                            icon = Lucide.CirclePlay,
                            title = "Hardware Display Refresh",
                            subtitle = "Display panel supports up to $maxFps Hz refresh rate",
                            badge = "$maxFps Hz"
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }

    // Confirmation Info Dialog shown when user tries to turn on the ADB switch
    if (showAdbConfirmEnableDialog) {
        AdbLimitationsDialog(
            onDismiss = { showAdbConfirmEnableDialog = false },
            onConfirmEnable = {
                coroutineScope.launch {
                    settingsManager.setAdbEnabled(true)
                    if (adbCaptureMode == "adb") {
                        settingsManager.setAudioSource("None")
                    }
                }
                showAdbConfirmEnableDialog = false
            }
        )
    }

    // Read-only ADB limitations info dialog
    if (showAdbLimitationsDialog) {
        AdbLimitationsDialog(onDismiss = { showAdbLimitationsDialog = false })
    }

    // ADB Pairing Dialog
    if (showAdbPairingDialog) {
        AdbPairingDialog(
            settingsManager = settingsManager,
            onDismiss = { showAdbPairingDialog = false }
        )
    }

    // Theme Mode Dialog
    if (showThemeDialog) {
        val mapping = mapOf(
            "light" to "Light Mode",
            "dark" to "Dark Mode",
            "system" to "System Default",
            "dynamic" to "Dynamic Wallpaper"
        )
        OptionSelectionDialog(
            title = "Select App Theme",
            options = mapping.values.toList(),
            selectedOption = mapping[themeMode] ?: "System Default",
            onDismiss = { showThemeDialog = false },
            onSelect = { displayName ->
                val key = mapping.entries.firstOrNull { it.value == displayName }?.key ?: "system"
                coroutineScope.launch { settingsManager.setThemeMode(key) }
                showThemeDialog = false
            }
        )
    }

    // Storage Threshold Dialog
    if (showStorageThresholdDialog) {
        val options = listOf("100 MB", "150 MB (Recommended)", "200 MB", "300 MB", "500 MB")
        val currentStr = when (safeStorageThresholdMb) {
            100 -> "100 MB"
            150 -> "150 MB (Recommended)"
            200 -> "200 MB"
            300 -> "300 MB"
            500 -> "500 MB"
            else -> "$safeStorageThresholdMb MB"
        }
        OptionSelectionDialog(
            title = "Safe Stop Storage Threshold",
            options = options,
            selectedOption = currentStr,
            onDismiss = { showStorageThresholdDialog = false },
            onSelect = { selected ->
                val mb = selected.substringBefore(" MB").toIntOrNull() ?: 150
                coroutineScope.launch { settingsManager.setSafeStorageThresholdMb(mb) }
                showStorageThresholdDialog = false
            }
        )
    }

    // Video Resolution Dialog
    if (showResDialog) {
        OptionSelectionDialog(
            title = "Select Resolution",
            options = DeviceCapabilitiesHelper.getResolutionOptions(context),
            selectedOption = resolution,
            onDismiss = { showResDialog = false },
            onSelect = {
                coroutineScope.launch { settingsManager.setResolution(it) }
                showResDialog = false
            }
        )
    }

    // Frame Rate Dialog
    if (showFpsDialog) {
        OptionSelectionDialog(
            title = "Select Frame Rate",
            options = DeviceCapabilitiesHelper.getFpsOptions(context),
            selectedOption = fps.toString(),
            onDismiss = { showFpsDialog = false },
            onSelect = {
                coroutineScope.launch { settingsManager.setFps(it.toInt()) }
                showFpsDialog = false
            }
        )
    }

    // Video Bitrate Dialog
    if (showBitrateDialog) {
        val options = DeviceCapabilitiesHelper.getBitrateOptions()
        val selectedString = "${bitrate / 1000000} Mbps"
        OptionSelectionDialog(
            title = "Select Bitrate",
            options = options,
            selectedOption = selectedString,
            onDismiss = { showBitrateDialog = false },
            onSelect = {
                val value = it.substringBefore(" Mbps").toInt() * 1000000
                coroutineScope.launch { settingsManager.setBitrate(value) }
                showBitrateDialog = false
            }
        )
    }

    // Audio Source Dialog
    if (showAudioDialog) {
        val mapping = linkedMapOf(
            "Mic" to "Microphone Only",
            "System" to "Internal Audio Only",
            "MicSystem" to "Mic + Internal Audio (Mixed)",
            "None" to "No Audio (Muted)"
        )
        val options = mapping.values.toList()
        val selectedOption = mapping[audioSource] ?: "Microphone Only"
        OptionSelectionDialog(
            title = "Select Audio Source",
            options = options,
            selectedOption = selectedOption,
            onDismiss = { showAudioDialog = false },
            onSelect = { displayName ->
                val sourceKey = mapping.entries.firstOrNull { it.value == displayName }?.key ?: "Mic"
                coroutineScope.launch { settingsManager.setAudioSource(sourceKey) }
                showAudioDialog = false
            }
        )
    }

    // Countdown Dialog
    if (showCountdownDialog) {
        val mapping = mapOf("0" to "Off", "3" to "3s", "5" to "5s", "10" to "10s")
        val options = mapping.values.toList()
        val selectedOption = mapping[countdown.toString()] ?: "3s"
        OptionSelectionDialog(
            title = "Select Countdown",
            options = options,
            selectedOption = selectedOption,
            onDismiss = { showCountdownDialog = false },
            onSelect = { displayName ->
                val key = mapping.entries.firstOrNull { it.value == displayName }?.key ?: "3"
                coroutineScope.launch { settingsManager.setCountdown(key.toInt()) }
                showCountdownDialog = false
            }
        )
    }

    // ADB Capture Mode Dialog
    if (showAdbModeDialog) {
        val modeMapping = mapOf(
            "mediaprojection" to "MediaProjection (Default)",
            "adb" to "ADB Only",
            "ask" to "Ask Every Time"
        )
        OptionSelectionDialog(
            title = "Default Recording Mode",
            options = modeMapping.values.toList(),
            selectedOption = modeMapping[adbCaptureMode] ?: "MediaProjection (Default)",
            onDismiss = { showAdbModeDialog = false },
            onSelect = { displayName ->
                val key = modeMapping.entries.firstOrNull { it.value == displayName }?.key ?: "mediaprojection"
                coroutineScope.launch {
                    settingsManager.setAdbCaptureMode(key)
                    if (key == "adb" && adbEnabled) {
                        settingsManager.setAudioSource("None")
                    }
                }
                showAdbModeDialog = false
            }
        )
    }

    // Floating Show Mode Dialog
    if (showFloatingShowModeDialog) {
        val options = listOf("Hide all the time", "Only while recording", "All the time (shortcut)")
        val currentSelected = when {
            !showFloating -> "Hide all the time"
            floatingShowMode.startsWith("All the time") -> "All the time (shortcut)"
            else -> "Only while recording"
        }
        OptionSelectionDialog(
            title = "Floating Control Ball",
            options = options,
            selectedOption = currentSelected,
            onDismiss = { showFloatingShowModeDialog = false },
            onSelect = { selected ->
                coroutineScope.launch {
                    val intent = Intent(context, com.gxdevs.screenx.service.ScreenRecordService::class.java)
                    when (selected) {
                        "Hide all the time" -> {
                            settingsManager.setShowFloating(false)
                            if (!com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                                intent.action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_EXIT
                                context.startService(intent)
                            }
                        }
                        "Only while recording" -> {
                            settingsManager.setShowFloating(true)
                            settingsManager.setFloatingShowMode("Only while recording")
                            if (!com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                                intent.action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_EXIT
                                context.startService(intent)
                            }
                        }
                        "All the time (shortcut)" -> {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                pendingFloatingMode = "All the time"
                                Toast.makeText(context, "Please allow 'Appear on top' to display floating ball", Toast.LENGTH_LONG).show()
                                val overlayIntent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                overlayPermissionLauncher.launch(overlayIntent)
                            } else {
                                settingsManager.setShowFloating(true)
                                settingsManager.setFloatingShowMode("All the time")
                                if (!com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                                    intent.action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_START_FLOATING_ONLY
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        context.startForegroundService(intent)
                                    } else {
                                        context.startService(intent)
                                    }
                                }
                            }
                        }
                    }
                }
                showFloatingShowModeDialog = false
            }
        )
    }
}
