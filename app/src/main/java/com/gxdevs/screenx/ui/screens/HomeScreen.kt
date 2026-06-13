package com.gxdevs.screenx.ui.screens

import android.content.Intent
import android.content.ContentUris
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Duo
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.gxdevs.screenx.data.SettingsManager
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
    onDeleteVideo: (RecordedVideo) -> Unit,
    isRecordingActive: Boolean,
    settingsManager: SettingsManager,
    onScreenshotClick: () -> Unit,
    onViewAllClick: () -> Unit,
    onTrimVideoClick: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var selectedVideoForPlayback by remember { mutableStateOf<RecordedVideo?>(null) }
    
    // Bottom Sheet State
    val sheetState = rememberModalBottomSheetState()
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Settings Flows
    val resolution by settingsManager.resolutionFlow.collectAsState(initial = "1080p")
    val fps by settingsManager.fpsFlow.collectAsState(initial = 30)
    val bitrate by settingsManager.bitrateFlow.collectAsState(initial = 8000000)
    val audioSource by settingsManager.audioSourceFlow.collectAsState(initial = "Mic")
    val countdown by settingsManager.countdownFlow.collectAsState(initial = 3)
    val showFloating by settingsManager.showFloatingFlow.collectAsState(initial = true)
    val hideDuringRecord by settingsManager.hideDuringRecordFlow.collectAsState(initial = false)
    val adaptiveTheme by settingsManager.adaptiveThemeFlow.collectAsState(initial = false)
    val shakeToStop by settingsManager.shakeToStopFlow.collectAsState(initial = false)
    val orientation by settingsManager.orientationFlow.collectAsState(initial = "Auto")
    val floatingShowMode by settingsManager.floatingShowModeFlow.collectAsState(initial = "Only when recording")

    // Option Dialog Flags (inside bottom sheet & main card)
    var showResDialog by remember { mutableStateOf(false) }
    var showFpsDialog by remember { mutableStateOf(false) }
    var showBitrateDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showCountdownDialog by remember { mutableStateOf(false) }
    var showFloatingShowModeDialog by remember { mutableStateOf(false) }

    // Check internal audio status
    val isInternalAudioEnabled = audioSource == "System" || audioSource == "MicSystem"

    // Configuration / Orientation detection
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        // Landscape Layout
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
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
                            .background(MaterialTheme.colorScheme.primary)
                            .bouncyClickable { onStartRecordingClick() }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.Transparent)
                                .border(
                                    width = 8.dp,
                                    color = Color.White,
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
                            icon = Icons.Default.Videocam,
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
                                "Mic" -> Icons.Default.Mic
                                "System" -> Icons.AutoMirrored.Filled.VolumeUp
                                "MicSystem" -> Icons.Default.Hearing
                                else -> Icons.Default.MicOff
                            },
                            label = when (audioSource) {
                                "Mic" -> "Microphone"
                                "System" -> "Device Audio"
                                "MicSystem" -> "Mic + Device"
                                else -> "Muted"
                            },
                            isActive = audioSource != "None",
                            onClick = { showAudioDialog = true }
                        )

                        // Toggle 3: Orientation
                        StatusToggleItem(
                            icon = when (orientation) {
                                "Auto" -> Icons.Default.ScreenRotation
                                "Portrait" -> Icons.Default.StayCurrentPortrait
                                else -> Icons.Default.StayCurrentLandscape
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
                        Icon(
                            imageVector = Icons.Default.Duo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
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
                        onClick = { showSettingsSheet = true }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
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
                    val internalAudioBg = if (isInternalAudioEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                    val internalAudioText = if (isInternalAudioEnabled) Color.White else MaterialTheme.colorScheme.onSurface
                    
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = internalAudioBg,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .bouncyClickable {
                                coroutineScope.launch {
                                    val nextSource = when (audioSource) {
                                        "Mic" -> "MicSystem"
                                        "MicSystem" -> "Mic"
                                        "System" -> "None"
                                        "None" -> "System"
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
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = null,
                                tint = internalAudioText,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Internal Audio",
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
                                imageVector = Icons.Default.Image,
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
                .statusBarsPadding()
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Duo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        "ScreenX",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp
                    )
                }
                BouncyIconButton(
                    onClick = { showSettingsSheet = true }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Open Settings",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Bento Grid Layout
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Tall Record Card + Right Column (Storage & Audio)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tall Record Card (2x height)
                    Card(
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .bouncyClickable { onStartRecordingClick() }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Record target icon at the top
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RadioButtonChecked,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Column {
                                Text(
                                    text = if (isRecordingActive) "Recording" else "Record",
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRecordingActive) "Tap to stop" else "Tap to start",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Right Column (Storage + Audio Source)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Storage Card
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

                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
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
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Storage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    
                                    // Badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "$usedPercent% Used",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                
                                Column {
                                    Text(
                                        text = "$freeSpaceGB GB",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Available Space",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Audio Source Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .bouncyClickable { showAudioDialog = true }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                                ) {
                                    Icon(
                                        imageVector = when (audioSource) {
                                            "Mic" -> Icons.Default.Mic
                                            "System" -> Icons.AutoMirrored.Filled.VolumeUp
                                            "MicSystem" -> Icons.Default.Hearing
                                            else -> Icons.Default.MicOff
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                Column {
                                    Text(
                                        text = when (audioSource) {
                                            "Mic" -> "Microphone"
                                            "System" -> "Device Audio"
                                            "MicSystem" -> "Device + Mic"
                                            else -> "Muted"
                                        },
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Audio Source",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 10.sp
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
                        .height(68.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Resolution Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
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
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "RESOLUTION",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$resolution / ${fps}fps",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
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
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (orientation) {
                                    "Auto" -> Icons.Default.ScreenRotation
                                    "Portrait" -> Icons.Default.StayCurrentPortrait
                                    else -> Icons.Default.StayCurrentLandscape
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "ORIENTATION",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (orientation == "Auto") "Auto Rotation" else "$orientation Mode",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Gallery / Recent Recordings Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gallery",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "View All",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            onViewAllClick()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (videos.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Recorded videos will appear here",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(end = 12.dp),
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

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Tools Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "Quick Tools",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                // Trim Video Premium Card (sole quick tool)
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1B1B1D)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .bouncyClickable {
                            Toast.makeText(context, "Trim feature coming soon!", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCut,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Trim Video",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Edit your recent captures",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    // Modal Settings Bottom Sheet (styled beige/teal)
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background, // E4DDD3
            dragHandle = null
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                // Bottom sheet header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Settings",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        BouncyIconButton(
                            onClick = {
                                coroutineScope.launch { sheetState.hide() }.invokeOnCompletion {
                                    if (!sheetState.isVisible) showSettingsSheet = false
                                }
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // Block 1: Video Parameters
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            BottomSheetMenuItem(
                                title = "Resolution",
                                value = resolution,
                                onClick = { showResDialog = true }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            BottomSheetMenuItem(
                                title = "Bitrate",
                                value = "${bitrate / 1000000} Mbps",
                                onClick = { showBitrateDialog = true }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            BottomSheetMenuItem(
                                title = "Frame Rate",
                                value = "$fps FPS",
                                onClick = { showFpsDialog = true }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // Block 2: Audio & Storage
                item {
                    BottomSheetSectionHeader("AUDIO & STORAGE")
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            val isAudioOn = audioSource != "None"
                            BottomSheetMenuItem(
                                title = "Record Audio",
                                value = if (isAudioOn) "On" else "Off",
                                onClick = {
                                    coroutineScope.launch {
                                        settingsManager.setAudioSource(if (isAudioOn) "None" else "Mic")
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            BottomSheetMenuItem(
                                title = "Audio Source",
                                value = when (audioSource) {
                                    "None" -> "Mute"
                                    "Mic" -> "Microphone"
                                    "System" -> "Internal"
                                    "MicSystem" -> "Internal + Mic"
                                    else -> audioSource
                                },
                                onClick = { showAudioDialog = true }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            BottomSheetMenuItem(
                                title = "Save Location",
                                value = "Internal Storage",
                                onClick = {
                                    Toast.makeText(context, "Location locked to standard Movies/ScreenX", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // Block 3: Control Options
                item {
                    BottomSheetSectionHeader("CONTROL OPTIONS")
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            BottomSheetMenuItem(
                                title = "Hide Record Window",
                                value = if (hideDuringRecord) "On" else "Off",
                                onClick = {
                                    coroutineScope.launch { settingsManager.setHideDuringRecord(!hideDuringRecord) }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            BottomSheetMenuItem(
                                title = "Shake to Stop",
                                value = if (shakeToStop) "On" else "Off",
                                onClick = {
                                    coroutineScope.launch { settingsManager.setShakeToStop(!shakeToStop) }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            BottomSheetMenuItem(
                                title = "Countdown",
                                value = if (countdown == 0) "Off" else "${countdown}s",
                                onClick = { showCountdownDialog = true }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                            val floatingBallSummary = when {
                                !showFloating -> "Hidden all the time"
                                floatingShowMode.startsWith("All the time") -> "All the time (shortcut)"
                                else -> "Only while recording"
                            }
                            BottomSheetMenuItem(
                                title = "Floating Control Ball",
                                value = floatingBallSummary,
                                onClick = { showFloatingShowModeDialog = true }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }

                // Block 4: Theme Configuration
                item {
                    BottomSheetSectionHeader("THEME SELECTION")
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BottomSheetMenuItem(
                            title = "Adaptive Theme (Wallpaper)",
                            value = if (adaptiveTheme) "On" else "Off",
                            onClick = {
                                coroutineScope.launch { settingsManager.setAdaptiveTheme(!adaptiveTheme) }
                            }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    // Dialog sheets inside Bottom Sheet
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

    if (showAudioDialog) {
        val mapping = mapOf("None" to "Mute", "Mic" to "Microphone", "System" to "Internal", "MicSystem" to "Internal + Mic")
        val options = mapping.values.toList()
        val selectedOption = mapping[audioSource] ?: "Microphone"
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
                            settingsManager.setShowFloating(true)
                            settingsManager.setFloatingShowMode("All the time")
                            if (!com.gxdevs.screenx.service.ScreenRecordService.isRecording) {
                                intent.action = com.gxdevs.screenx.service.ScreenRecordService.ACTION_START_FLOATING_ONLY
                                context.startForegroundService(intent)
                            }
                        }
                    }
                }
                showFloatingShowModeDialog = false
            }
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
                        imageVector = Icons.Default.PlayCircle,
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
                        imageVector = Icons.Default.MoreVert,
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
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            expandedMenu = false
                            onShare()
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            expandedMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red) }
                    )
                }
            }
        }
    }
}

@Composable
fun BottomSheetSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = MaterialTheme.colorScheme.primary, // Teal
        modifier = Modifier.padding(start = 16.dp, top = 8.dp)
    )
}

@Composable
fun BottomSheetMenuItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1.1f)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary, // Teal value
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.9f)
        )
    }
}

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Black,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(460.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
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
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color(0x66000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close player",
                            tint = Color.White
                        )
                    }
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        videoName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
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
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        imageVector = Icons.Default.PlayCircle,
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
                        imageVector = Icons.Default.MoreVert,
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
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Share") },
                        onClick = {
                            expandedMenu = false
                            VideoHelper.shareVideo(context, video)
                        },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            expandedMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = Color.Red) }
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

