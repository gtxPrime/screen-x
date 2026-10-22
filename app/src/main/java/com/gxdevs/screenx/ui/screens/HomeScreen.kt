package com.gxdevs.screenx.ui.screens

import android.content.Intent
import android.content.ContentUris
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.gxdevs.screenx.ui.theme.EmeraldAccent
import com.gxdevs.screenx.ui.components.MainRecordShaderCard
import android.util.Size
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.CircleDot
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.Ear
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Film
import com.composables.icons.lucide.HardDrive
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Mic
import com.composables.icons.lucide.MicOff
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Scissors
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Shield
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tablet
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.Usb
import com.composables.icons.lucide.Video
import com.composables.icons.lucide.Volume2
import com.composables.icons.lucide.VolumeX
import com.composables.icons.lucide.X
import com.composables.icons.lucide.Zap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.gxdevs.screenx.ui.components.VideoPreviewBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.gxdevs.screenx.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.ContextCompat
import com.gxdevs.screenx.data.AdbManager
import com.gxdevs.screenx.data.SettingsManager
import com.gxdevs.screenx.service.PairingInputService
import com.gxdevs.screenx.utils.NetworkUtils
import com.gxdevs.screenx.utils.RecordedVideo
import com.gxdevs.screenx.utils.VideoHelper
import com.gxdevs.screenx.utils.DeviceCapabilitiesHelper
import kotlinx.coroutines.launch



// Spring-based bouncy clickable modifier
fun Modifier.bouncyClickable(
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) = composed {
    val actualInteractionSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualInteractionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bouncyScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = actualInteractionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .bouncyClickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

suspend fun loadVideoThumbnail(context: android.content.Context, videoUri: Uri): Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(videoUri, Size(256, 256), null)
        } else {
            val videoId = ContentUris.parseId(videoUri)
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(
                context.contentResolver,
                videoId,
                MediaStore.Video.Thumbnails.MINI_KIND,
                null
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    videos: List<RecordedVideo>,
    onStartRecordingClick: () -> Unit,
    onAdbRecordClick: () -> Unit,
    onDeleteVideo: (RecordedVideo) -> Unit,
    isRecordingActive: Boolean,
    isSaving: Boolean = false,
    settingsManager: SettingsManager,
    onScreenshotClick: () -> Unit,
    onViewAllClick: () -> Unit,
    onTrimVideoClick: (RecordedVideo?) -> Unit = {},
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit = {}
) {
    val context = LocalContext.current
    val appIconBitmap = remember(context) {
        try {
            val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
            if (drawable != null) {
                val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, width, height)
                drawable.draw(canvas)
                bitmap.asImageBitmap()
            } else null
        } catch (_: Exception) {
            null
        }
    }
    val coroutineScope = rememberCoroutineScope()
    var selectedVideoForPlayback by remember { mutableStateOf<RecordedVideo?>(null) }
    var selectedScreenshotForViewer by remember { mutableStateOf<RecordedVideo?>(null) }
    var mediaToDelete by remember { mutableStateOf<RecordedVideo?>(null) }

    // Refresh animation state
    var isRefreshing by remember { mutableStateOf(false) }
    val refreshRotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = tween(
            durationMillis = 650,
            easing = LinearEasing
        ),
        finishedListener = { isRefreshing = false },
        label = "homeRefreshAnim"
    )

    // Settings Flows
    val resolution by settingsManager.resolutionFlow.collectAsState(initial = "1080p")
    val fps by settingsManager.fpsFlow.collectAsState(initial = 30)
    val bitrate by settingsManager.bitrateFlow.collectAsState(initial = 8000000)
    val audioSource by settingsManager.audioSourceFlow.collectAsState(initial = "Mic")
    val voicePriority by settingsManager.voicePriorityFlow.collectAsState(initial = true)
    val orientation by settingsManager.orientationFlow.collectAsState(initial = "Auto")
    val safeStorageStop by settingsManager.safeStorageStopFlow.collectAsState(initial = true)
    val adbEnabled by settingsManager.adbEnabledFlow.collectAsState(initial = false)
    val adbPaired by settingsManager.adbPairedFlow.collectAsState(initial = false)
    val swapHomeCards by settingsManager.swapHomeCardsFlow.collectAsState(initial = false)

    val isAdbRecordingActual by com.gxdevs.screenx.service.AdbRecordService.isRecordingFlow.collectAsState()
    var isAdbStarting by remember { mutableStateOf(false) }
    LaunchedEffect(isAdbRecordingActual) {
        isAdbStarting = false
    }
    val isAdbRecording = isAdbRecordingActual || isAdbStarting

    // Option Dialog Flags
    var showAudioDialog by remember { mutableStateOf(false) }
    var showAdbLimitationsDialog by remember { mutableStateOf(false) }
    var showAdbPairingDialog by remember { mutableStateOf(false) }

    val triggerAdbRecord: () -> Unit = {
        if (!isAdbRecordingActual) {
            if (!adbPaired) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.home_toast_stealth_not_paired),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                showAdbPairingDialog = true
            } else {
                isAdbStarting = true
                onAdbRecordClick()
            }
        } else {
            onAdbRecordClick()
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
    val totalSpaceGB = remember {
        try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            (stat.blockSizeLong * stat.blockCountLong) / (1024 * 1024 * 1024)
        } catch (_: Exception) {
            128L
        }
    }
    val usedPercent = remember {
        try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val total = stat.blockSizeLong * stat.blockCountLong
            val free = stat.blockSizeLong * stat.availableBlocksLong
            if (total > 0) (((total - free) * 100) / total).toInt() else 24
        } catch (_: Exception) {
            24
        }
    }

    // Check internal audio status
    val isInternalAudioSelected = audioSource == "System" || audioSource == "MicSystem"

    // Configuration / Orientation & Foldable / Tablet detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isWideScreen = configuration.screenWidthDp >= 600
    val useMultiColumn = isLandscape || isWideScreen

    if (useMultiColumn) {
        // Multi-column Layout (Landscape, Foldables unfolded & Tablets)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 1200.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            // Left Side: Record Card (sized down to fit vertically)
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    val landscapeRecordingActive = if (swapHomeCards) isAdbRecordingActual else isRecordingActive
                    Text(
                        text = if (landscapeRecordingActive) {
                            if (swapHomeCards) stringResource(R.string.home_stealth_recording) else stringResource(R.string.home_recording_screen)
                        } else {
                            if (swapHomeCards) stringResource(R.string.home_ready_for_stealth) else stringResource(R.string.home_ready_to_record)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    
                    // Bouncy Record Button (Sized to 90dp in landscape)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(90.dp)
                            .clip(CircleShape)
                            .background(if (landscapeRecordingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            .bouncyClickable {
                                if (swapHomeCards) triggerAdbRecord() else onStartRecordingClick()
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    width = 8.dp,
                                    color = if (landscapeRecordingActive) Color.White else MaterialTheme.colorScheme.onPrimary,
                                    shape = CircleShape
                                )
                        )
                    }

                    // 3 Status Info Toggles row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Toggle 1: Resolution
                        StatusToggleItem(
                            icon = Lucide.Video,
                            label = resolution,
                            onClick = {
                                val nextRes = when (resolution) {
                                    "1080p" -> "720p"
                                    "720p" -> "480p"
                                    "480p" -> "Original"
                                    else -> "1080p"
                                }
                                coroutineScope.launch { settingsManager.setResolution(nextRes) }
                            }
                        )

                        // Toggle 2: Audio Source
                        StatusToggleItem(
                            icon = when (audioSource) {
                                "System", "MicSystem" -> Lucide.Volume2
                                "None" -> Lucide.VolumeX
                                else -> Lucide.Mic
                            },
                            label = when (audioSource) {
                                "System" -> stringResource(R.string.audio_display_system)
                                "MicSystem" -> stringResource(R.string.audio_display_dual)
                                "None" -> stringResource(R.string.audio_display_none)
                                else -> stringResource(R.string.audio_display_mic)
                            },
                            isActive = true,
                            onClick = { showAudioDialog = true }
                        )

                        // Toggle 3: Orientation
                        StatusToggleItem(
                            icon = when (orientation) {
                                "Auto" -> Lucide.RotateCcw
                                "Portrait" -> Lucide.Smartphone
                                else -> Lucide.Tablet
                            },
                            label = when (orientation) {
                                "Portrait" -> stringResource(R.string.orientation_portrait)
                                "Landscape" -> stringResource(R.string.orientation_landscape)
                                else -> stringResource(R.string.orientation_auto)
                            },
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

            // Right Side: Header, Quick Tools, and Recent recordings list
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Compact Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (appIconBitmap != null) {
                            Image(
                                bitmap = appIconBitmap,
                                contentDescription = "ScreenX Icon",
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.logo_no_bg),
                                contentDescription = "ScreenX Icon",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            "ScreenX",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    BouncyIconButton(
                        onClick = onSettingsClick
                    ) {
                        Icon(
                            imageVector = Lucide.Settings,
                            contentDescription = "Open Settings",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Quick Tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val internalAudioBg = if (isInternalAudioSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val internalAudioText = if (isInternalAudioSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = internalAudioBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .bouncyClickable {
                                coroutineScope.launch {
                                    val nextSource = when (audioSource) {
                                        "Mic" -> "System"
                                        "System" -> "MicSystem"
                                        "MicSystem" -> "None"
                                        else -> "Mic"
                                    }
                                    settingsManager.setAudioSource(nextSource)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = when (audioSource) {
                                    "System", "MicSystem" -> Lucide.Volume2
                                    "None" -> Lucide.VolumeX
                                    else -> Lucide.Mic
                                },
                                contentDescription = null,
                                tint = internalAudioText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (audioSource) {
                                    "System" -> stringResource(R.string.audio_display_system)
                                    "MicSystem" -> stringResource(R.string.audio_display_dual)
                                    "None" -> stringResource(R.string.audio_display_none)
                                    else -> stringResource(R.string.audio_display_mic)
                                },
                                color = internalAudioText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .bouncyClickable { onScreenshotClick() }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Lucide.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.home_take_screenshot),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Recent Recordings Title & Refresh
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.home_recent_recordings),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = {
                            if (isSaving) {
                                Toast.makeText(context, context.getString(R.string.home_toast_finalizing), Toast.LENGTH_SHORT).show()
                            } else {
                                isRefreshing = true
                                onRefresh()
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(650)
                                    isRefreshing = false
                                    Toast.makeText(context, context.getString(R.string.home_toast_refreshed_fmt, videos.size), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.RotateCcw,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer { rotationZ = refreshRotation }
                        )
                    }
                }

                if (isSaving) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.home_toast_finalizing), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }

                // List of Recent Videos (Vertical scrollable column for landscape)
                if (videos.isEmpty() && !isSaving) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.home_no_recordings),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(videos.take(2), key = { it.id }) { video ->
                            RecentVideoCard(
                                video = video,
                                onClick = {
                                    if (video.isVideo) {
                                        selectedVideoForPlayback = video
                                    } else {
                                        selectedScreenshotForViewer = video
                                    }
                                },
                                onShare = { VideoHelper.shareVideo(context, video) },
                                onDelete = { mediaToDelete = video }
                            )
                        }
                    }
                }
            }
        }
        }
    } else {
        // Portrait Layout
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.background else Color(0xFFF5F6F9)),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(scrollState)
            ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (appIconBitmap != null) {
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = "ScreenX Icon",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                        )
                    } else {
                        Image(
                            painter = painterResource(id = R.drawable.logo_no_bg),
                            contentDescription = "ScreenX Icon",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        "ScreenX",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                }
                BouncyIconButton(
                    onClick = onSettingsClick
                ) {
                    Icon(
                        imageVector = Lucide.Settings,
                        contentDescription = "Open Settings",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Bento Grid Layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Tall Record Card + Right Column (Storage & Audio)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(236.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Permanent Dynamic AGSL Shader Gradient with Interactive Chromatic Shockwave Ripple
                    MainRecordShaderCard(
                        isRecordingActive = if (swapHomeCards) isAdbRecording else isRecordingActive,
                        onClick = {
                            if (swapHomeCards) triggerAdbRecord() else onStartRecordingClick()
                        },
                        titleText = if (swapHomeCards) {
                            if (isAdbRecording) stringResource(R.string.home_stealth_recording) else stringResource(R.string.home_stealth_title)
                        } else null,
                        subtitleText = if (swapHomeCards) {
                            if (isAdbRecording) stringResource(R.string.home_tap_to_stop) else stringResource(R.string.home_stealth_sub)
                        } else null,
                        badgeText = if (swapHomeCards) {
                            if (isAdbRecording) stringResource(R.string.home_rec_badge) else if (adbPaired) stringResource(R.string.badge_paired) else stringResource(R.string.home_unpaired)
                        } else null,
                        icon = if (swapHomeCards) Lucide.Zap else null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    // Right Column (Storage + Audio Source)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Storage Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .bouncyClickable { onSettingsClick() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (freeSpaceGB < 2) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                             else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Lucide.HardDrive,
                                        contentDescription = null,
                                        tint = if (freeSpaceGB < 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                
                                Column {
                                    Text(
                                        text = "$freeSpaceGB GB",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { (usedPercent / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp)),
                                        color = if (freeSpaceGB < 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = "$freeSpaceGB GB free of $totalSpaceGB GB",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        lineHeight = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Audio Source Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .bouncyClickable { showAudioDialog = true }
                        ) {
                            val isAudioActive = audioSource != "None"
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isAudioActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                                             else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            1.dp,
                                            if (isAudioActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        imageVector = when (audioSource) {
                                            "System", "MicSystem" -> Lucide.Volume2
                                            "None" -> Lucide.VolumeX
                                            else -> Lucide.Mic
                                        },
                                        contentDescription = null,
                                        tint = if (isAudioActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                                
                                Column {
                                    Text(
                                        text = when (audioSource) {
                                            "System" -> stringResource(R.string.audio_display_system)
                                            "MicSystem" -> "Mic + System"
                                            "None" -> stringResource(R.string.audio_display_muted)
                                            else -> stringResource(R.string.audio_display_mic)
                                        },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 18.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = if (audioSource == "None") {
                                            stringResource(R.string.audio_display_muted)
                                        } else if (audioSource == "MicSystem" && voicePriority) {
                                            "Auto-Ducking ON"
                                        } else {
                                            stringResource(R.string.setting_audio_input_title)
                                        },
                                        color = if (audioSource == "MicSystem" && voicePriority) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (audioSource == "MicSystem" && voicePriority) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                // Row 2: Resolution & Orientation side-by-side (Identical vertical layout to Storage & Audio)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Resolution Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bouncyClickable {
                                val nextRes = when (resolution) {
                                    "1080p" -> "720p"
                                    "720p" -> "480p"
                                    "480p" -> "Original"
                                    else -> "1080p"
                                }
                                coroutineScope.launch { settingsManager.setResolution(nextRes) }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Lucide.Video,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = resolution,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.fps_video_fmt, fps),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Orientation Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bouncyClickable {
                                val nextOri = when (orientation) {
                                    "Auto" -> "Portrait"
                                    "Portrait" -> "Landscape"
                                    else -> "Auto"
                                }
                                coroutineScope.launch { settingsManager.setOrientation(nextOri) }
                            }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = when (orientation) {
                                        "Auto" -> Lucide.RotateCcw
                                        "Portrait" -> Lucide.Smartphone
                                        else -> Lucide.Tablet
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = when (orientation) {
                                        "Portrait" -> stringResource(R.string.orientation_portrait)
                                        "Landscape" -> stringResource(R.string.orientation_landscape)
                                        else -> stringResource(R.string.auto_rotate)
                                    },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.setting_orientation_title),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                // Row 3: Full Width Secondary Card (only when ADB switch is enabled)
                if (adbEnabled) {
                    val secondaryIsRecording = if (swapHomeCards) isRecordingActive else isAdbRecording
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = if (secondaryIsRecording)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                        else
                            null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(78.dp)
                            .bouncyClickable {
                                if (swapHomeCards) {
                                    onStartRecordingClick()
                                } else {
                                    triggerAdbRecord()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (secondaryIsRecording)
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (swapHomeCards) Lucide.CircleDot else Lucide.Zap,
                                    contentDescription = null,
                                    tint = if (secondaryIsRecording)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (swapHomeCards) {
                                            if (isRecordingActive) stringResource(R.string.home_recording_screen) else stringResource(R.string.home_standard_title)
                                        } else {
                                            if (isAdbRecording) stringResource(R.string.home_stealth_recording) else stringResource(R.string.home_stealth_title)
                                        },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (secondaryIsRecording)
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        else if (swapHomeCards || adbPaired)
                                            EmeraldAccent.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (secondaryIsRecording) stringResource(R.string.home_rec_badge)
                                            else if (swapHomeCards) "MediaProjection"
                                            else if (adbPaired) stringResource(R.string.badge_paired)
                                            else stringResource(R.string.badge_set_up),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (secondaryIsRecording)
                                                MaterialTheme.colorScheme.error
                                            else if (swapHomeCards || adbPaired)
                                                EmeraldAccent
                                            else
                                                MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (swapHomeCards) {
                                        if (isRecordingActive) stringResource(R.string.home_tap_to_stop)
                                        else stringResource(R.string.home_standard_sub)
                                    } else {
                                        if (isAdbRecording) stringResource(R.string.home_tap_to_stop)
                                        else stringResource(R.string.home_stealth_sub)
                                    },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .bouncyClickable { showAdbLimitationsDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Lucide.Info,
                                    contentDescription = "Stealth Recording Information",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Gallery / Recent Recordings Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.library_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (videos.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.home_view_all),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable {
                                onViewAllClick()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (isSaving) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.finalizing_recording),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.merging_audio_gallery),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (videos.isNotEmpty()) {
                    // Featured Latest Recording Card
                    val latestVideo = videos.first()
                    RecentVideoCard(
                        video = latestVideo,
                        onClick = {
                            if (latestVideo.isVideo) {
                                selectedVideoForPlayback = latestVideo
                            } else {
                                selectedScreenshotForViewer = latestVideo
                            }
                        },
                        onShare = { VideoHelper.shareVideo(context, latestVideo) },
                        onDelete = { mediaToDelete = latestVideo }
                    )

                    // Previous Recordings Horizontal Row
                    if (videos.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(end = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(videos.drop(1), key = { it.id }) { video ->
                                RecentThumbnailItem(
                                    video = video,
                                    onClick = {
                                        if (video.isVideo) {
                                            selectedVideoForPlayback = video
                                        } else {
                                            selectedScreenshotForViewer = video
                                        }
                                    },
                                    onDelete = { mediaToDelete = video }
                                )
                            }
                        }
                    }
                } else if (!isSaving) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isRefreshing = true
                                onRefresh()
                                coroutineScope.launch {
                                    kotlinx.coroutines.delay(650)
                                    isRefreshing = false
                                    Toast.makeText(context, context.getString(R.string.home_toast_refreshed_fmt, videos.size), Toast.LENGTH_SHORT).show()
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Lucide.RotateCcw,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = refreshRotation }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.no_recordings_tap_refresh),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quick Tools Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = stringResource(R.string.quick_tools),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                // Trim Video Premium Card (sole quick tool)
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                        .bouncyClickable {
                            onTrimVideoClick(null)
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Lucide.Scissors,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = stringResource(R.string.trimmer_mode_trim),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = stringResource(R.string.browse_internal_storage),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Lucide.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
        }
    }

    if (showAudioDialog) {
        val micStr = stringResource(R.string.audio_opt_mic)
        val sysStr = stringResource(R.string.audio_opt_system)
        val dualStr = stringResource(R.string.audio_opt_dual)
        val noneStr = stringResource(R.string.audio_opt_none)
        val mapping = linkedMapOf(
            "Mic" to micStr,
            "System" to sysStr,
            "MicSystem" to dualStr,
            "None" to noneStr
        )
        val options = mapping.values.toList()
        val selectedOption = mapping[audioSource] ?: micStr
        OptionSelectionDialog(
            title = stringResource(R.string.dialog_select_audio),
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

    if (showAdbLimitationsDialog) {
        AdbLimitationsDialog(onDismiss = { showAdbLimitationsDialog = false })
    }

    if (showAdbPairingDialog) {
        AdbPairingDialog(
            settingsManager = settingsManager,
            onDismiss = { showAdbPairingDialog = false }
        )
    }

    // Playback Bottom Sheet (Full-height / Native Aspect Ratio Video Preview)
    selectedVideoForPlayback?.let { video ->
        VideoPreviewBottomSheet(
            video = video,
            onDismiss = { selectedVideoForPlayback = null },
            onShare = { VideoHelper.shareVideo(context, video) },
            onTrim = {
                selectedVideoForPlayback = null
                onTrimVideoClick(video)
            },
            onRename = { _ ->
                onRefresh()
            },
            onDelete = {
                selectedVideoForPlayback = null
                mediaToDelete = video
            }
        )
    }

    // Screenshot Viewer Dialog
    selectedScreenshotForViewer?.let { shot ->
        ScreenshotViewerDialog(
            imageUri = shot.uri,
            imageName = shot.name,
            onDismiss = { selectedScreenshotForViewer = null },
            onShare = { VideoHelper.shareVideo(context, shot) },
            onDelete = {
                mediaToDelete = shot
                selectedScreenshotForViewer = null
            }
        )
    }

    // Delete Confirmation Dialog
    mediaToDelete?.let { item ->
        DeleteConfirmDialog(
            media = item,
            onDismiss = { mediaToDelete = null },
            onConfirm = {
                val target = item
                mediaToDelete = null
                onDeleteVideo(target)
            }
        )
    }
}

@Composable
fun StatusToggleItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .bouncyClickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RecentVideoCard(
    video: RecordedVideo,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }
    var thumbnail by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(video.uri) {
        thumbnail = VideoHelper.loadThumbnail(context, video.uri, video.isVideo)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Video Thumbnail
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = if (video.isVideo) Lucide.CirclePlay else Lucide.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0xCC000000), RoundedCornerShape(topStart = 6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    if (video.isVideo) {
                        Text(
                            VideoHelper.formatDuration(video.duration),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "IMG",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = VideoHelper.getHumanizedTitle(video),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = VideoHelper.getHumanizedSubtitle(video),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Dot Menu Button
            Box {
                IconButton(onClick = { expandedMenu = true }) {
                    Icon(
                        imageVector = Lucide.EllipsisVertical,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(if (video.isVideo) stringResource(R.string.trimmer_play) else stringResource(R.string.home_view_all)) },
                        onClick = {
                            expandedMenu = false
                            onClick()
                        },
                        leadingIcon = { Icon(if (video.isVideo) Lucide.Play else Lucide.Image, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.share)) },
                        onClick = {
                            expandedMenu = false
                            onShare()
                        },
                        leadingIcon = { Icon(Lucide.Share2, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            expandedMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Lucide.Trash2, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}

enum class StackPosition {
    Single, Top, Middle, Bottom
}

fun getStackedShape(position: StackPosition): RoundedCornerShape {
    return when (position) {
        StackPosition.Single -> RoundedCornerShape(26.dp)
        StackPosition.Top -> RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
        StackPosition.Middle -> RoundedCornerShape(6.dp)
        StackPosition.Bottom -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 26.dp, bottomEnd = 26.dp)
    }
}

@Composable
fun OrionSectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 6.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
fun OrionStackedGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
        content = content
    )
}

@Composable
fun OrionSettingsDivider() {
    // No divider line needed as cards are individually stacked with a sleek 3dp gap
}

@Composable
fun OrionSettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    position: StackPosition = StackPosition.Middle,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Surface(
        shape = getStackedShape(position),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBackground)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true,
                    lineHeight = 20.sp
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        softWrap = true
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    }
}

@Composable
fun OrionSettingsValueItem(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    position: StackPosition = StackPosition.Middle,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    showChevron: Boolean = true
) {
    Surface(
        shape = getStackedShape(position),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBackground)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 110.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true,
                    lineHeight = 20.sp
                )
                if (!subtitle.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        softWrap = true
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(vertical = 2.dp)
                    .widthIn(min = 36.dp, max = 140.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 15.sp,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (showChevron) {
                        Icon(
                            imageVector = Lucide.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OrionSettingsInfoItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    position: StackPosition = StackPosition.Middle,
    iconTint: Color = MaterialTheme.colorScheme.onSurface,
    iconBackground: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Surface(
        shape = getStackedShape(position),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(iconBackground)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .widthIn(min = 110.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    softWrap = true,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    softWrap = true
                )
            }

            if (badge != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .widthIn(min = 36.dp, max = 140.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (onClick != null) {
                            Icon(
                                imageVector = Lucide.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomSheetSectionHeader(title: String) {
    OrionSectionHeader(text = title)
}

@Composable
fun BottomSheetMenuItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    OrionSettingsValueItem(
        icon = Lucide.Settings,
        title = title,
        value = value,
        onClick = onClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerDialog(
    videoUri: Uri,
    videoName: String,
    onDismiss: () -> Unit
) {
    // Legacy fallback forwarder to the upgraded VideoPreviewBottomSheet
    val context = LocalContext.current
    VideoPreviewBottomSheet(
        video = RecordedVideo(
            id = 0L,
            uri = videoUri,
            name = videoName,
            path = "",
            size = 0L,
            duration = 0L,
            dateAdded = System.currentTimeMillis() / 1000L,
            resolution = "1080p",
            isVideo = true
        ),
        onDismiss = onDismiss,
        onShare = {
            VideoHelper.shareVideo(
                context,
                RecordedVideo(0L, videoUri, videoName, "", 0L, 0L, 0L, "1080p", true)
            )
        },
        onTrim = onDismiss,
        onRename = {},
        onDelete = onDismiss
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentThumbnailItem(
    video: RecordedVideo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }
    var thumbnail by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()

    LaunchedEffect(video.uri) {
        thumbnail = VideoHelper.loadThumbnail(context, video.uri, video.isVideo)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .width(110.dp)
            .height(175.dp)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { onClick() },
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    expandedMenu = true
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (video.isVideo) Lucide.CirclePlay else Lucide.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Bottom-up subtle black gradient scrim over bottom 48.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
            )

            // Single pill badge at bottom-start
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                if (video.isVideo) {
                    Text(
                        text = VideoHelper.formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "IMG",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Long-press context menu
            DropdownMenu(
                expanded = expandedMenu,
                onDismissRequest = { expandedMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (video.isVideo) stringResource(R.string.trimmer_play) else stringResource(R.string.home_view_all)) },
                    onClick = {
                        expandedMenu = false
                        onClick()
                    },
                    leadingIcon = { Icon(if (video.isVideo) Lucide.Play else Lucide.Image, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share)) },
                    onClick = {
                        expandedMenu = false
                        VideoHelper.shareVideo(context, video)
                    },
                    leadingIcon = { Icon(Lucide.Share2, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        expandedMenu = false
                        onDelete()
                    },
                    leadingIcon = { Icon(Lucide.Trash2, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

// ─── ADB Pairing Setup Dialog ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbPairingDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isPaired by settingsManager.adbPairedFlow.collectAsState(initial = false)

    // Detect device Wi-Fi IP (critical for Samsung / One UI)
    val detectedIp = remember { NetworkUtils.getLocalIpAddress(context) ?: "192.168.1.9" }

    // 0 = Notification Pairing (Recommended), 1 = Manual Entry
    var selectedTab by remember { mutableStateOf(if (isPaired) 1 else 0) }
    var serviceStarted by remember { mutableStateOf(false) }

    // Manual pairing fields (prefill with detected local device IP)
    var pairingHost by remember { mutableStateOf(detectedIp) }
    var pairingPort by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var connectionPort by remember { mutableStateOf("") }

    var isWorking by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var successMsg by remember { mutableStateOf(if (isPaired && AdbManager.isConnected) "Connected and ready!" else "") }

    LaunchedEffect(isPaired) {
        if (isPaired) {
            serviceStarted = false
            try {
                context.stopService(Intent(context, PairingInputService::class.java))
            } catch (_: Exception) {}
            successMsg = "Device successfully paired and ready for stealth recording!"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                context.stopService(Intent(context, PairingInputService::class.java))
            } catch (_: Exception) {}
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun launchServiceAndSettings() {
        try {
            val serviceIntent = Intent(context, PairingInputService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
            serviceStarted = true

            // Launch Developer Options
            val settingsIntent = Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
            try {
                context.startActivity(settingsIntent)
            } catch (_: Exception) {
                context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
            }
        } catch (e: Exception) {
            errorMsg = "Could not start pairing service: ${e.message}"
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchServiceAndSettings()
        } else {
            errorMsg = "Notification permission is needed to enter the 6-digit code via notification reply."
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isWorking) onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row with Title, Status & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (AdbManager.isConnected)
                                        EmeraldAccent.copy(alpha = 0.18f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant
                                )
                        ) {
                            Icon(
                                imageVector = if (AdbManager.isConnected) Lucide.Shield else Lucide.Usb,
                                contentDescription = null,
                                tint = if (AdbManager.isConnected) EmeraldAccent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                stringResource(R.string.adb_pairing_dialog_stealth_setup),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (AdbManager.isConnected) EmeraldAccent
                                            else if (isPaired) Color(0xFFFFA000)
                                            else MaterialTheme.colorScheme.outline
                                        )
                                )
                                Text(
                                    if (AdbManager.isConnected) stringResource(R.string.adb_status_connected_ready)
                                    else if (isPaired) stringResource(R.string.adb_status_paired_needs_connect)
                                    else stringResource(R.string.adb_status_onetime_pairing),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (AdbManager.isConnected) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { if (!isWorking) onDismiss() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.X,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Tab Switcher: Notification vs Manual
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        stringResource(R.string.adb_tab_quick_notification) to 0,
                        stringResource(R.string.adb_tab_manual_port) to 1
                    ).forEach { (label, index) ->
                        val isSelected = selectedTab == index
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedTab = index }
                                .padding(vertical = 9.dp)
                        ) {
                            Text(
                                label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Tab 0: Quick Notification Pairing
                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.adb_pair_notification_hint),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        AdbSetupStep(
                            number = "1",
                            title = stringResource(R.string.adb_step1_launch_title),
                            text = stringResource(R.string.adb_step1_launch_desc)
                        )
                        AdbSetupStep(
                            number = "2",
                            title = stringResource(R.string.adb_step2_pair_code_title),
                            text = stringResource(R.string.adb_step2_pair_code_desc)
                        )
                        AdbSetupStep(
                            number = "3",
                            title = stringResource(R.string.adb_step3_reply_title),
                            text = stringResource(R.string.adb_step3_reply_desc)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                errorMsg = ""
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    launchServiceAndSettings()
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Lucide.Zap, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.start_pairing_helper), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (serviceStarted && !isPaired) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.listening_pairing_dialog),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            stringResource(R.string.tap_pair_device_code),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            context.stopService(Intent(context, PairingInputService::class.java))
                                            serviceStarted = false
                                        }
                                    ) {
                                        Text(stringResource(R.string.stop), fontSize = 12.sp)
                                    }
                                }
                            }
                        } else if (isPaired) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = EmeraldAccent.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Lucide.Shield,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.pairing_complete),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldAccent
                                        )
                                        Text(
                                            stringResource(R.string.device_paired_stealth),
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tab 1: Manual Entry
                if (selectedTab == 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(
                            onClick = {
                                val intent = Intent("android.settings.APPLICATION_DEVELOPMENT_SETTINGS")
                                try { context.startActivity(intent) } catch (_: Exception) {
                                    context.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                                }
                            },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Lucide.Settings, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.open_developer_settings), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }

                        // Step 1: Pair
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.adb_manual_step1_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.adb_manual_step1_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )

                                OutlinedTextField(
                                    value = pairingHost,
                                    onValueChange = { v -> pairingHost = v },
                                    label = { Text(stringResource(R.string.adb_device_ip_label), fontSize = 11.sp) },
                                    placeholder = { Text(detectedIp, fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pairingPort,
                                        onValueChange = { v -> pairingPort = v.filter(Char::isDigit).take(5) },
                                        label = { Text(stringResource(R.string.adb_pairing_port_label), fontSize = 11.sp) },
                                        placeholder = { Text(stringResource(R.string.adb_pairing_port_placeholder), fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = pairingCode,
                                        onValueChange = { v -> pairingCode = v.filter(Char::isDigit).take(6) },
                                        label = { Text(stringResource(R.string.adb_pairing_code_label), fontSize = 11.sp) },
                                        placeholder = { Text("009154", fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }

                                Button(
                                    onClick = {
                                        errorMsg = ""
                                        successMsg = ""
                                        val port = pairingPort.toIntOrNull()
                                        if (port == null || port !in 1..65535) {
                                            errorMsg = context.getString(R.string.adb_err_invalid_port); return@Button
                                        }
                                        if (pairingCode.trim().length != 6) {
                                            errorMsg = context.getString(R.string.adb_err_invalid_code); return@Button
                                        }
                                        isWorking = true
                                        coroutineScope.launch {
                                            val result = AdbManager.pair(
                                                context = context,
                                                pairingPort = port,
                                                pairingCode = pairingCode.trim(),
                                                host = pairingHost.trim().ifEmpty { null }
                                            )
                                            isWorking = false
                                            result.onSuccess {
                                                successMsg = context.getString(R.string.adb_success_paired_now_connect)
                                                // Also attempt auto-connect immediately
                                                AdbManager.connect(context, 0, pairingHost.trim().ifEmpty { null })
                                            }.onFailure {
                                                errorMsg = it.message ?: context.getString(R.string.adb_pairing_failed_fmt, "")
                                            }
                                        }
                                    },
                                    enabled = !isWorking,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.adb_pairing_btn), fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Step 2: Connect
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    stringResource(R.string.adb_manual_step2_title),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    stringResource(R.string.adb_manual_step2_desc),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = connectionPort,
                                    onValueChange = { v -> connectionPort = v.filter(Char::isDigit).take(5) },
                                    label = { Text(stringResource(R.string.adb_connect_port_label), fontSize = 11.sp) },
                                    placeholder = { Text(stringResource(R.string.adb_port_auto_placeholder), fontSize = 11.sp) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Button(
                                    onClick = {
                                        errorMsg = ""
                                        successMsg = ""
                                        val port = connectionPort.toIntOrNull() ?: 0
                                        isWorking = true
                                        coroutineScope.launch {
                                            val result = AdbManager.connect(
                                                context = context,
                                                connectionPort = port,
                                                host = pairingHost.trim().ifEmpty { null }
                                            )
                                            isWorking = false
                                            result.onSuccess {
                                                successMsg = context.getString(R.string.adb_success_connected)
                                            }.onFailure {
                                                errorMsg = it.message ?: context.getString(R.string.adb_err_connect_failed)
                                            }
                                        }
                                    },
                                    enabled = !isWorking,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.adb_connect_btn), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Error / Success Feedback
                if (errorMsg.isNotEmpty()) {
                    Text(
                        errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                if (successMsg.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = EmeraldAccent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Lucide.Shield,
                                contentDescription = null,
                                tint = EmeraldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                successMsg,
                                color = EmeraldAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

            }
        }
    }
    }

@Composable
private fun AdbSetupStep(number: String, title: String, text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = number,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = text,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

// ─── ADB Limitations Info Dialog ────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbLimitationsDialog(
    onDismiss: () -> Unit,
    onConfirmEnable: (() -> Unit)? = null
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            )
        }
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            // Title Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Lucide.Zap,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = if (onConfirmEnable != null) stringResource(R.string.stealth_enable_title) else stringResource(R.string.home_stealth_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (onConfirmEnable != null) stringResource(R.string.stealth_review_desc) else stringResource(R.string.stealth_features_limitations),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Emerald banner: Fully Undetectable (High contrast in both light & dark)
            val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val undetectableAccent = if (isDarkTheme) Color(0xFF4ADE80) else Color(0xFF15803D)
            val undetectableBg = if (isDarkTheme) Color(0xFF14532D).copy(alpha = 0.35f) else Color(0xFFDCFCE7)
            val undetectableBorder = if (isDarkTheme) Color(0xFF22C55E).copy(alpha = 0.4f) else Color(0xFF86EFAC)
            val undetectableBody = if (isDarkTheme) Color(0xFFE2E8F0) else Color(0xFF166534)

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = undetectableBg,
                border = BorderStroke(1.dp, undetectableBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Lucide.Shield,
                        contentDescription = null,
                        tint = undetectableAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.fully_undetectable),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = undetectableAccent
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.stealth_rules_bypass),
                            fontSize = 11.sp,
                            color = undetectableBody,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Limitation items
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdbLimitationRow(
                    icon = Lucide.VolumeX,
                    label = stringResource(R.string.adb_rules_audio_title),
                    detail = stringResource(R.string.adb_rules_audio_desc),
                    isWarning = true
                )
                AdbLimitationRow(
                    icon = Lucide.TriangleAlert,
                    label = stringResource(R.string.stealth_rule_3min),
                    detail = stringResource(R.string.stealth_rule_3min_desc),
                    isWarning = true
                )
                AdbLimitationRow(
                    icon = Lucide.Usb,
                    label = stringResource(R.string.stealth_rule_wireless),
                    detail = stringResource(R.string.stealth_rule_wireless_desc),
                    isWarning = false
                )
            }

            // Action buttons
            if (onConfirmEnable != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onConfirmEnable,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text(stringResource(R.string.enable), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(stringResource(R.string.got_it), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
}

@Composable
private fun AdbLimitationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    detail: String,
    isWarning: Boolean
) {
    val iconColor = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val bgColor = if (isWarning)
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    else
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(16.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
fun OptionSelectionDialog(
    title: String,
    options: List<String>,
    selectedOption: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 440.dp),
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.5).sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                options.forEach { option ->
                    val isSelected = option == selectedOption
                    val backgroundColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            Color.Transparent
                        },
                        label = "bgColor"
                    )
                    val contentColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        label = "contentColor"
                    )

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) },
                        shape = RoundedCornerShape(12.dp),
                        color = backgroundColor
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(16.dp)
                                    .background(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = RoundedCornerShape(1.5.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                option,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    stringResource(R.string.cancel),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

