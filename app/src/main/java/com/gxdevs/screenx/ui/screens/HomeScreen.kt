package com.gxdevs.screenx.ui.screens

import android.content.Intent
import android.content.ContentUris
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.gxdevs.screenx.ui.theme.EmeraldAccent
import android.util.Size
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Camera
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    settingsManager: SettingsManager,
    onScreenshotClick: () -> Unit,
    onViewAllClick: () -> Unit,
    onTrimVideoClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedVideoForPlayback by remember { mutableStateOf<RecordedVideo?>(null) }

    // Settings Flows
    val resolution by settingsManager.resolutionFlow.collectAsState(initial = "1080p")
    val fps by settingsManager.fpsFlow.collectAsState(initial = 30)
    val bitrate by settingsManager.bitrateFlow.collectAsState(initial = 8000000)
    val audioSource by settingsManager.audioSourceFlow.collectAsState(initial = "Mic")
    val orientation by settingsManager.orientationFlow.collectAsState(initial = "Auto")
    val safeStorageStop by settingsManager.safeStorageStopFlow.collectAsState(initial = true)
    val adbEnabled by settingsManager.adbEnabledFlow.collectAsState(initial = false)
    val adbPaired by settingsManager.adbPairedFlow.collectAsState(initial = false)

    // Option Dialog Flags
    var showAudioDialog by remember { mutableStateOf(false) }
    var showAdbLimitationsDialog by remember { mutableStateOf(false) }
    var showAdbPairingDialog by remember { mutableStateOf(false) }

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
            if (total > 0) (((total - free) * 100) / total).toInt() else 24
        } catch (_: Exception) {
            24
        }
    }

    // Check internal audio status
    val isInternalAudioSelected = audioSource == "System" || audioSource == "MicSystem"

    // Configuration / Orientation detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // Landscape Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    Text(
                        text = if (isRecordingActive) "Recording Screen..." else "Ready to Record",
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
                            .background(if (isRecordingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            .bouncyClickable { onStartRecordingClick() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    width = 8.dp,
                                    color = if (isRecordingActive) Color.White else MaterialTheme.colorScheme.onPrimary,
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
                                "System" -> "Device Audio"
                                "MicSystem" -> "Mic + Device"
                                "None" -> "No Audio"
                                else -> "Microphone"
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
                            label = orientation,
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
                        Image(
                            painter = painterResource(id = R.drawable.logo_no_bg),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
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
                                    "System" -> "Internal Audio"
                                    "MicSystem" -> "Mic + Internal"
                                    "None" -> "No Audio"
                                    else -> "Microphone"
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
                                text = "Screenshot",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Recent Recordings Title
                Text(
                    text = "Recent Recordings",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                // List of Recent Videos (Vertical scrollable column for landscape)
                if (videos.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recordings yet",
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
                                onClick = { selectedVideoForPlayback = video },
                                onShare = { VideoHelper.shareVideo(context, video) },
                                onDelete = { onDeleteVideo(video) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Portrait Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                    Image(
                        painter = painterResource(id = R.drawable.logo_no_bg),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
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
                        .height(216.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Standard MediaProjection Record Card (Full Left Height)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRecordingActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isRecordingActive) MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bouncyClickable { onStartRecordingClick() }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Record target icon at the top
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isRecordingActive) MaterialTheme.colorScheme.onError.copy(alpha = 0.2f)
                                        else MaterialTheme.colorScheme.primaryContainer
                                    )
                            ) {
                                Icon(
                                    imageVector = Lucide.CircleDot,
                                    contentDescription = null,
                                    tint = if (isRecordingActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = if (isRecordingActive) "Recording" else "Record",
                                    color = if (isRecordingActive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 24.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRecordingActive) "Tap to stop" else "Tap to start",
                                    color = if (isRecordingActive) MaterialTheme.colorScheme.onError.copy(alpha = 0.75f)
                                           else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }

                    // Right Column (Storage + Audio Source)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Storage Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .bouncyClickable { onSettingsClick() }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (freeSpaceGB < 2) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Lucide.HardDrive,
                                            contentDescription = null,
                                            tint = if (freeSpaceGB < 2) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    
                                    // Badge: Indicates Safe Stop protection
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = if (safeStorageStop) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (safeStorageStop) "Safe Stop" else "$usedPercent% Used",
                                            color = if (safeStorageStop) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                
                                Column {
                                    Text(
                                        text = "$freeSpaceGB GB",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 18.sp
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "Available Space",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Audio Source Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .bouncyClickable { showAudioDialog = true }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = when (audioSource) {
                                            "System", "MicSystem" -> Lucide.Volume2
                                            "None" -> Lucide.VolumeX
                                            else -> Lucide.Mic
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Column {
                                    Text(
                                        text = when (audioSource) {
                                            "System" -> "Internal Audio"
                                            "MicSystem" -> "Mic + Internal"
                                            "None" -> "No Audio"
                                            else -> "Microphone"
                                        },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        lineHeight = 18.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                    Text(
                                        text = "Audio Source",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp,
                                        lineHeight = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Row 2: Resolution & Orientation side-by-side
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Resolution Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Lucide.Video,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "RESOLUTION",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 11.sp,
                                    letterSpacing = 0.4.sp
                                )
                                Text(
                                    text = "$resolution / ${fps}fps",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 16.sp,
                                    letterSpacing = (-0.4).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Orientation Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
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
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = when (orientation) {
                                        "Auto" -> Lucide.RotateCcw
                                        "Portrait" -> Lucide.Smartphone
                                        else -> Lucide.Tablet
                                    },
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "ORIENTATION",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 11.sp,
                                    letterSpacing = 0.4.sp
                                )
                                Text(
                                    text = if (orientation == "Auto") "Auto Rotate" else "$orientation",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 16.sp,
                                    letterSpacing = (-0.4).sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Row 3: Full Width ADB Record Card (only when ADB switch is enabled)
                if (adbEnabled) {
                    val isAdbRecordingActual by com.gxdevs.screenx.service.AdbRecordService.isRecordingFlow.collectAsState()
                    var isAdbStarting by remember { mutableStateOf(false) }
                    LaunchedEffect(isAdbRecordingActual) {
                        isAdbStarting = false
                    }
                    val isAdbRecording = isAdbRecordingActual || isAdbStarting
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isAdbRecording)
                                MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                            else
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .bouncyClickable {
                                if (!isAdbRecordingActual) {
                                    if (!adbPaired) {
                                        android.widget.Toast.makeText(
                                            context,
                                            "Stealth Recording is not paired. Please complete setup first.",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        showAdbPairingDialog = true
                                        return@bouncyClickable
                                    }
                                    isAdbStarting = true
                                }
                                onAdbRecordClick()
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isAdbRecording)
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                    )
                            ) {
                                Icon(
                                    imageVector = Lucide.Zap,
                                    contentDescription = null,
                                    tint = if (isAdbRecording)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.secondary,
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
                                        text = if (isAdbRecording) "Stealth Recording Active" else "Stealth Recording",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isAdbRecording)
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                        else if (adbPaired)
                                            EmeraldAccent.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = if (isAdbRecording) "REC"
                                            else if (adbPaired) "Ready"
                                            else "Pair Required",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isAdbRecording)
                                                MaterialTheme.colorScheme.error
                                            else if (adbPaired)
                                                EmeraldAccent
                                            else
                                                MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isAdbRecording) "Tap to stop recording session"
                                    else "Undetectable capture • Bypasses app detection",
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
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
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
                        text = "Gallery",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "View All",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable {
                            onViewAllClick()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (videos.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Recorded videos will appear here",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(end = 10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(videos, key = { it.id }) { video ->
                            RecentThumbnailItem(
                                video = video,
                                onClick = { selectedVideoForPlayback = video },
                                onDelete = { onDeleteVideo(video) }
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
                    text = "Quick Tools",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                // Trim Video Premium Card (sole quick tool)
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .bouncyClickable {
                            onTrimVideoClick()
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
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = Lucide.Scissors,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    text = "Trim Video",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 18.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Edit your recent captures",
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
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                imageVector = Lucide.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

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

    if (showAdbLimitationsDialog) {
        AdbLimitationsDialog(onDismiss = { showAdbLimitationsDialog = false })
    }

    if (showAdbPairingDialog) {
        AdbPairingDialog(
            settingsManager = settingsManager,
            onDismiss = { showAdbPairingDialog = false }
        )
    }

    // Playback Dialog
    selectedVideoForPlayback?.let { video ->
        VideoPlayerDialog(
            videoUri = video.uri,
            videoName = video.name,
            onDismiss = { selectedVideoForPlayback = null }
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
                .background(MaterialTheme.colorScheme.surfaceVariant) // Beige cream background
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
        thumbnail = loadVideoThumbnail(context, video.uri)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                        imageVector = Lucide.CirclePlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0x99000000), RoundedCornerShape(topStart = 6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        VideoHelper.formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    video.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                val dateStr = VideoHelper.formatDate(video.dateAdded)
                val sizeStr = VideoHelper.formatSize(video.size)
                Text(
                    text = "$dateStr • $sizeStr",
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
                        text = { Text("Play") },
                        onClick = {
                            expandedMenu = false
                            onClick()
                        },
                        leadingIcon = { Icon(Lucide.Play, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            expandedMenu = false
                            onShare()
                        },
                        leadingIcon = { Icon(Lucide.Share2, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            expandedMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Lucide.Trash2, contentDescription = null, tint = Color.Red) }
                    )
                }
            }
        }
    }
}

@Composable
fun OrionSectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp)
    )
}

@Composable
fun OrionStackedGroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier.fillMaxWidth(),
        content = {
            Column(content = content)
        }
    )
}

@Composable
fun OrionSettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 18.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    )
}

@Composable
fun OrionSettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
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
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

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

@Composable
fun OrionSettingsValueItem(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
    showChevron: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.widthIn(max = 130.dp)
            ) {
                Text(
                    text = value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            if (showChevron) {
                Icon(
                    imageVector = Lucide.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
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
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(iconBackground),
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
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
        }

        if (badge != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun BottomSheetSectionHeader(title: String) {
    OrionSectionHeader(title = title)
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
    val context = LocalContext.current
    
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = videoName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
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
                    "Close",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun RecentThumbnailItem(
    video: RecordedVideo,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var expandedMenu by remember { mutableStateOf(false) }
    var thumbnail by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(video.uri) {
        thumbnail = loadVideoThumbnail(context, video.uri)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .width(140.dp)
            .height(100.dp)
            .bouncyClickable { onClick() }
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
                        imageVector = Lucide.CirclePlay,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Duration badge at bottom-start
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = VideoHelper.formatDuration(video.duration),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Options menu button at top-end
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
            ) {
                IconButton(
                    onClick = { expandedMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Lucide.EllipsisVertical,
                        contentDescription = "Options",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
                DropdownMenu(
                    expanded = expandedMenu,
                    onDismissRequest = { expandedMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        onClick = {
                            expandedMenu = false
                            onClick()
                        },
                        leadingIcon = { Icon(Lucide.Play, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            expandedMenu = false
                            VideoHelper.shareVideo(context, video)
                        },
                        leadingIcon = { Icon(Lucide.Share2, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            expandedMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Lucide.Trash2, contentDescription = null, tint = Color.Red) }
                    )
                }
            }

            // Minimal text gradient or shadow at bottom for name
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .background(Color(0x99000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = video.name.substringAfterLast("ScreenX_").substringBefore(".mp4").take(8) + "...",
                    color = Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── ADB Pairing Setup Dialog ────────────────────────────────────────────────

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

    Dialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (AdbManager.isConnected)
                                    EmeraldAccent.copy(alpha = 0.18f)
                                else
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            )
                    ) {
                        Icon(
                            imageVector = if (AdbManager.isConnected) Lucide.Shield else Lucide.Usb,
                            contentDescription = null,
                            tint = if (AdbManager.isConnected) EmeraldAccent else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Stealth Recording Setup",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.4).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (AdbManager.isConnected) "Status: Connected & Ready"
                            else if (isPaired) "Status: Paired (Needs Connect)"
                            else "One-time wireless pairing",
                            fontSize = 12.sp,
                            color = if (AdbManager.isConnected) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tab Switcher: Notification vs Manual
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedTab == 0) MaterialTheme.colorScheme.surface else Color.Transparent,
                        tonalElevation = if (selectedTab == 0) 3.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 0 }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Quick Notification",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedTab == 1) MaterialTheme.colorScheme.surface else Color.Transparent,
                        tonalElevation = if (selectedTab == 1) 3.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedTab = 1 }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Manual / Port Entry",
                                fontSize = 12.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Tab 0: Quick Notification Pairing
                if (selectedTab == 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            "Pair easily using Android's notification reply:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        AdbSetupStep(
                            number = "1",
                            text = "Tap 'Start Pairing Helper' below. ScreenX opens Developer Options."
                        )
                        AdbSetupStep(
                            number = "2",
                            text = "Enable Wireless Debugging, then tap 'Pair device with pairing code'."
                        )
                        AdbSetupStep(
                            number = "3",
                            text = "Pull down your notification bar, tap Reply on ScreenX's notification, type the 6-digit code, and tap Send."
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Lucide.Zap, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Pairing Helper", fontWeight = FontWeight.Bold)
                        }

                        if (serviceStarted) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
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
                                            "Listening for pairing dialog…",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Tap 'Pair device with pairing code' in Wireless debugging",
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
                                        Text("Stop", fontSize = 12.sp)
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
                            Text("Open Developer Settings", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
                                    "Step 1: Pair with Device",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "From Wireless Debugging → 'Pair device with pairing code':\n(Tip: Keep dialog open via Split-Screen so the port stays active)",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )

                                OutlinedTextField(
                                    value = pairingHost,
                                    onValueChange = { v -> pairingHost = v },
                                    label = { Text("Device IP Address", fontSize = 11.sp) },
                                    placeholder = { Text(detectedIp, fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = pairingPort,
                                        onValueChange = { v -> pairingPort = v.filter(Char::isDigit).take(5) },
                                        label = { Text("Pairing Port", fontSize = 11.sp) },
                                        placeholder = { Text("e.g. 33625", fontSize = 11.sp) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    OutlinedTextField(
                                        value = pairingCode,
                                        onValueChange = { v -> pairingCode = v.filter(Char::isDigit).take(6) },
                                        label = { Text("6-digit Code", fontSize = 11.sp) },
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
                                            errorMsg = "Enter a valid 5-digit pairing port"; return@Button
                                        }
                                        if (pairingCode.trim().length != 6) {
                                            errorMsg = "Enter the 6-digit pairing code"; return@Button
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
                                                successMsg = "Pairing successful! Now connect below."
                                                // Also attempt auto-connect immediately
                                                AdbManager.connect(context, 0, pairingHost.trim().ifEmpty { null })
                                            }.onFailure {
                                                errorMsg = it.message ?: "Pairing failed"
                                            }
                                        }
                                    },
                                    enabled = !isWorking,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Pair Device", fontWeight = FontWeight.Bold)
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
                                    "Step 2: Connect to Port",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Main port shown on Wireless Debugging (e.g. 41927) or leave blank for Auto:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                OutlinedTextField(
                                    value = connectionPort,
                                    onValueChange = { v -> connectionPort = v.filter(Char::isDigit).take(5) },
                                    label = { Text("Connection Port (Optional)", fontSize = 11.sp) },
                                    placeholder = { Text("e.g. 41927 or blank for auto", fontSize = 11.sp) },
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
                                                successMsg = "Connected! Stealth Recording is ready."
                                            }.onFailure {
                                                errorMsg = it.message ?: "Connection failed"
                                            }
                                        }
                                    },
                                    enabled = !isWorking,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Connect", fontWeight = FontWeight.Bold)
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

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { if (!isWorking) onDismiss() }
                    ) {
                        Text(if (AdbManager.isConnected) "Done" else "Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun AdbSetupStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        ) {
            Text(number, fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        Text(
            text = text,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ─── ADB Limitations Info Dialog ────────────────────────────────────────────

@Composable
fun AdbLimitationsDialog(
    onDismiss: () -> Unit,
    onConfirmEnable: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.93f)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Lucide.Zap,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (onConfirmEnable != null) "Enable Stealth Recording" else "Stealth Recording",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (onConfirmEnable != null) "Please review important details" else "How it works & limitations",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Stealth badge
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = EmeraldAccent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Lucide.Shield,
                            contentDescription = null,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Fully Undetectable",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = EmeraldAccent
                            )
                            Text(
                                text = "Apps like Snapchat, Instagram, and banking apps cannot detect Stealth Recording — it bypasses their screenshot/screen-record detection entirely.",
                                fontSize = 12.sp,
                                color = EmeraldAccent.copy(alpha = 0.9f),
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                // Limitation items
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdbLimitationRow(
                        icon = Lucide.VolumeX,
                        label = "No Audio Capture",
                        detail = "Stealth Recording cannot capture microphone or system audio. Use standard recording mode if audio is needed.",
                        isWarning = true
                    )
                    AdbLimitationRow(
                        icon = Lucide.TriangleAlert,
                        label = "3-Minute Segment Limit",
                        detail = "Android's built-in screenrecord has a hard 3-minute maximum per session. ScreenX auto-restarts in segments to work around this. We're actively working on extending this limit — coming soon!",
                        isWarning = true
                    )
                    AdbLimitationRow(
                        icon = Lucide.Usb,
                        label = "One-Time Wireless Pairing",
                        detail = "Stealth Recording requires pairing ScreenX with itself once via Android's Wireless Debugging (Android 11+). After that, it works silently forever.",
                        isWarning = false
                    )
                    AdbLimitationRow(
                        icon = Lucide.Smartphone,
                        label = "Android 11+ Required",
                        detail = "Stealth Recording pairing (loopback) is only available on Android 11 (API 30) and above. Older devices fall back to standard recording automatically.",
                        isWarning = false
                    )
                }

                // Action buttons
                if (onConfirmEnable != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                "Cancel",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onConfirmEnable,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(
                                "I Understand, Enable",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                } else {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            "Got it",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge
                        )
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
                modifier = Modifier.fillMaxWidth()
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
                    "Cancel",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

