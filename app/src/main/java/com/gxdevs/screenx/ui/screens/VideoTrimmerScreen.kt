package com.gxdevs.screenx.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.CirclePlay
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Crop
import com.composables.icons.lucide.Eraser
import com.composables.icons.lucide.FolderOpen
import com.composables.icons.lucide.Pause
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.RotateCw
import com.composables.icons.lucide.Save
import com.composables.icons.lucide.Scissors
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.composables.icons.lucide.Film
import com.gxdevs.screenx.utils.RecordedVideo
import com.gxdevs.screenx.utils.VideoHelper
import com.gxdevs.screenx.utils.VideoTrimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

// ─── Edit Mode Enum ──────────────────────────────────────────────────────────

enum class EditMode { TRIM, DELETE, SPLIT }

// ─── Screen root ─────────────────────────────────────────────────────────────

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoTrimmerScreen(
    initialVideo: RecordedVideo?,
    videos: List<RecordedVideo>,
    onBackClick: () -> Unit,
    onTrimSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val view = androidx.compose.ui.platform.LocalView.current
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = !((colorScheme.background.red * 0.299f + colorScheme.background.green * 0.587f + colorScheme.background.blue * 0.114f) > 0.5f)
    val activity = remember(context) { context.findActivity() }
    val window = activity?.window

    var selectedUri by remember { mutableStateOf<Uri?>(initialVideo?.uri) }
    var videoName by remember { mutableStateOf(initialVideo?.name ?: "") }
    var videoDurationMs by remember { mutableStateOf(initialVideo?.duration ?: 0L) }

    val handleBackPress = {
        if (selectedUri != null && initialVideo == null) {
            selectedUri = null
        } else {
            onBackClick()
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        handleBackPress()
    }



    val systemVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            videoName = "External_Video_${System.currentTimeMillis()}.mp4"
            coroutineScope.launch(Dispatchers.IO) {
                val dur = queryVideoDuration(context, uri)
                withContext(Dispatchers.Main) { videoDurationMs = dur }
            }
        }
    }

    if (selectedUri == null) {
        // ── Video picker ──────────────────────────────────────────────────────
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("Select Video to Edit", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Lucide.ArrowLeft, "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clickable { systemVideoPickerLauncher.launch("video/*") }
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Lucide.FolderOpen, null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Choose from Device",
                                    fontWeight = FontWeight.Bold, fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    "Browse files from internal storage",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text("Recent Captures", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))

                if (videos.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recordings available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(videos) { video ->
                            RecentSelectCard(video = video, onClick = {
                                selectedUri = video.uri
                                videoName = video.name
                                videoDurationMs = video.duration
                            })
                        }
                    }
                }
            }
        }
    } else {
        TrimmerEditor(
            videoUri = selectedUri!!,
            videoName = videoName,
            videoDurationMs = videoDurationMs,
            onBackClick = handleBackPress,
            onTrimSuccess = onTrimSuccess
        )
    }
}

// ─── Video Segment Model ──────────────────────────────────────────────────────

data class VideoSegment(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val isDeleted: Boolean = false
)

// ─── Editor ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimmerEditor(
    videoUri: Uri,
    videoName: String,
    videoDurationMs: Long,
    onBackClick: () -> Unit,
    onTrimSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ── State ─────────────────────────────────────────────────────────────────
    var segments by remember(videoUri) {
        mutableStateOf(
            listOf(
                VideoSegment(
                    id = java.util.UUID.randomUUID().toString(),
                    startMs = 0L,
                    endMs = videoDurationMs
                )
            )
        )
    }
    var selectedSegmentId by remember { mutableStateOf<String?>(null) }

    // Playhead fraction [0..1]
    var playFrac  by remember { mutableStateOf(0f) }

    // True while the user's finger is dragging on the timeline
    var isDragging by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekTimestamp by remember { mutableStateOf(0L) }
    var draggedSegmentId by remember { mutableStateOf<String?>(null) }

    var currentPosMs by remember { mutableStateOf(0L) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingProgress by remember { mutableStateOf(0f) }
    var processingLabel by remember { mutableStateOf("") }

    var showExitConfirmation by remember { mutableStateOf(false) }
    val handleExit = {
        val hasChanges = segments.size > 1 || (segments.firstOrNull()?.let { it.startMs > 0L || it.endMs < videoDurationMs } ?: false)
        if (hasChanges) {
            showExitConfirmation = true
        } else {
            onBackClick()
        }
    }
    androidx.activity.compose.BackHandler(enabled = !isProcessing) {
        handleExit()
    }

    // ── ExoPlayer ────────────────────────────────────────────────────────────
    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(exoPlayer) {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        })
    }

    // Position tracking + skipping deleted segments in preview
    LaunchedEffect(exoPlayer, segments) {
        while (true) {
            delay(33)
            val pos = exoPlayer.currentPosition
            
            if (!isDragging && !isSeeking) {
                currentPosMs = pos
                playFrac = if (videoDurationMs > 0)
                    (pos.toFloat() / videoDurationMs).coerceIn(0f, 1f) else 0f
            } else if (isSeeking) {
                if (abs(pos - currentPosMs) < 150L || System.currentTimeMillis() - seekTimestamp > 1000L) {
                    isSeeking = false
                }
            }

            val state = exoPlayer.playbackState
            if (state == Player.STATE_ENDED && !isSeeking) {
                val firstActive = segments.firstOrNull { !it.isDeleted }
                val targetMs = firstActive?.startMs ?: 0L
                isSeeking = true
                seekTimestamp = System.currentTimeMillis()
                exoPlayer.seekTo(targetMs)
                if (!isDragging) {
                    currentPosMs = targetMs
                    playFrac = if (videoDurationMs > 0) (targetMs.toFloat() / videoDurationMs).coerceIn(0f, 1f) else 0f
                }
                exoPlayer.pause()
            }

            if (exoPlayer.isPlaying && !isSeeking) {
                val currentSegment = segments.firstOrNull { pos in it.startMs until it.endMs }
                if (currentSegment != null && currentSegment.isDeleted) {
                    val nextActive = segments.firstOrNull { it.startMs >= currentSegment.endMs && !it.isDeleted }
                    if (nextActive != null) {
                        isSeeking = true
                        seekTimestamp = System.currentTimeMillis()
                        exoPlayer.seekTo(nextActive.startMs)
                    } else {
                        val firstActive = segments.firstOrNull { !it.isDeleted }
                        if (firstActive != null) {
                            isSeeking = true
                            seekTimestamp = System.currentTimeMillis()
                            exoPlayer.seekTo(firstActive.startMs)
                        } else {
                            exoPlayer.pause()
                        }
                    }
                } else if (pos >= videoDurationMs) {
                    val firstActive = segments.firstOrNull { !it.isDeleted }
                    if (firstActive != null) {
                        isSeeking = true
                        seekTimestamp = System.currentTimeMillis()
                        exoPlayer.seekTo(firstActive.startMs)
                    }
                }
            }
        }
    }


    // Helper functions for editing
    fun splitAtPlayhead() {
        val splitMs = (playFrac * videoDurationMs).toLong()
        val targetSeg = segments.firstOrNull { splitMs in (it.startMs + 300L)..(it.endMs - 300L) }
        if (targetSeg != null) {
            val idx = segments.indexOf(targetSeg)
            val seg1 = VideoSegment(
                id = java.util.UUID.randomUUID().toString(),
                startMs = targetSeg.startMs,
                endMs = splitMs,
                isDeleted = targetSeg.isDeleted
            )
            val seg2 = VideoSegment(
                id = java.util.UUID.randomUUID().toString(),
                startMs = splitMs,
                endMs = targetSeg.endMs,
                isDeleted = targetSeg.isDeleted
            )
            val mutable = segments.toMutableList()
            mutable.removeAt(idx)
            mutable.add(idx, seg2)
            mutable.add(idx, seg1)
            segments = mutable
            selectedSegmentId = seg2.id
            Toast.makeText(context, "Video split at ${formatTime(splitMs)}", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Cannot split too close to segment boundaries", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleDeleteSelected() {
        val activeId = selectedSegmentId ?: segments.firstOrNull { currentPosMs in it.startMs until it.endMs }?.id
        if (activeId != null) {
            segments = segments.map {
                if (it.id == activeId) {
                    it.copy(isDeleted = !it.isDeleted)
                } else {
                    it
                }
            }
        } else {
            Toast.makeText(context, "Please select a segment first", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Thumbnails ────────────────────────────────────────────────────────────
    val thumbnailCount = 12
    var thumbnails by remember(videoUri) { mutableStateOf<List<Bitmap?>>(List(thumbnailCount) { null }) }
    LaunchedEffect(videoUri, videoDurationMs) {
        if (videoDurationMs <= 0) return@LaunchedEffect
        val old = thumbnails
        thumbnails = withContext(Dispatchers.IO) {
            old.forEach { it?.recycle() }
            loadFrameThumbnails(context, videoUri, videoDurationMs, thumbnailCount)
        }
    }

    DisposableEffect(videoUri) {
        onDispose {
            exoPlayer.release()
            thumbnails.forEach { it?.recycle() }
        }
    }

    var timelineWidthPx by remember { mutableStateOf(0f) }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Video", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = handleExit, enabled = !isProcessing) {
                        Icon(Lucide.ArrowLeft, "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            val activeSegments = segments.filter { !it.isDeleted }.map { Pair(it.startMs, it.endMs) }
                            if (activeSegments.isEmpty()) {
                                Toast.makeText(context, "Cannot save with all segments deleted", Toast.LENGTH_SHORT).show()
                            } else {
                                executeExport(context, coroutineScope, videoUri, videoName, activeSegments, { isProcessing = it }, { processingProgress = it }, { processingLabel = it }, onTrimSuccess)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        enabled = !isProcessing
                    ) {
                        Icon(Lucide.Save, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Video")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .background(MaterialTheme.colorScheme.background),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Video player ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Playback controls overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                            )
                        )
                        .padding(bottom = 12.dp, top = 24.dp, start = 14.dp, end = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatTime(currentPosMs),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { exoPlayer.seekTo((currentPosMs - 5000).coerceAtLeast(0)) }
                        ) {
                            Icon(Lucide.RotateCcw, "-5s", tint = Color.White)
                        }
                        IconButton(
                            onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                            modifier = Modifier
                                .size(52.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(
                                if (isPlaying) Lucide.Pause else Lucide.Play,
                                "Play/Pause", tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        IconButton(
                            onClick = { exoPlayer.seekTo((currentPosMs + 5000).coerceAtMost(videoDurationMs)) }
                        ) {
                            Icon(Lucide.RotateCw, "+5s", tint = Color.White)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            formatTime(videoDurationMs),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    playbackSpeed = when (playbackSpeed) {
                                        0.5f -> 1.0f
                                        1.0f -> 1.25f
                                        1.25f -> 1.5f
                                        1.5f -> 2.0f
                                        else -> 0.5f
                                    }
                                }
                        ) {
                            Text(
                                text = "${playbackSpeed}x",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            // ── Segment list horizontal view ─────────────────────────────
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                items(segments) { seg ->
                    val isSelected = seg.id == (selectedSegmentId ?: segments.firstOrNull { currentPosMs in it.startMs until it.endMs }?.id)
                    val isDeleted = seg.isDeleted

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isSelected && isDeleted -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            isDeleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        border = if (isSelected) BorderStroke(2.dp, if (isDeleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .clickable {
                                selectedSegmentId = seg.id
                                exoPlayer.seekTo(seg.startMs)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDeleted) Lucide.Eraser else Lucide.Film,
                                contentDescription = null,
                                tint = if (isDeleted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${formatTime(seg.startMs)} - ${formatTime(seg.endMs)}",
                                color = if (isDeleted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (isDeleted) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "DELETED",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            }

            // ── Timeline ──────────────────────────────────────────────────
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

                        // Segment Status info
                        val activeId = selectedSegmentId ?: segments.firstOrNull { currentPosMs in it.startMs until it.endMs }?.id
                        val activeSeg = segments.firstOrNull { it.id == activeId }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (activeSeg != null) {
                                InfoChip("Selected Start", formatTime(activeSeg.startMs), MaterialTheme.colorScheme.primary)
                                InfoChip("Selected Duration", formatTime(activeSeg.endMs - activeSeg.startMs), MaterialTheme.colorScheme.tertiary)
                                InfoChip("Selected End", formatTime(activeSeg.endMs), MaterialTheme.colorScheme.secondary)
                            } else {
                                InfoChip("Start", "00:00.0", Color.Gray)
                                InfoChip("Duration", formatTime(videoDurationMs), Color.Gray)
                                InfoChip("End", formatTime(videoDurationMs), Color.Gray)
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── Frame strip + handles ─────────────────────────────
                        val errorColor = MaterialTheme.colorScheme.error
                        val primaryColor = MaterialTheme.colorScheme.primary

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                                .onGloballyPositioned { coords ->
                                    timelineWidthPx = coords.size.width.toFloat()
                                }
                        ) {
                            // Frame thumbnails
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                thumbnails.forEach { bmp ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                }
                            }

                            val currentActiveIdForHighlight = selectedSegmentId ?: segments.firstOrNull { currentPosMs in it.startMs until it.endMs }?.id
                            val currentActiveSegForHighlight = segments.firstOrNull { it.id == currentActiveIdForHighlight }
                            val highlightColor = if (currentActiveSegForHighlight?.isDeleted == true) errorColor else primaryColor

                            // Dimmed overlay for deleted segments & split vertical lines
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val W = size.width
                                val H = size.height
                                
                                segments.forEach { seg ->
                                    val sX = (seg.startMs.toFloat() / videoDurationMs) * W
                                    val eX = (seg.endMs.toFloat() / videoDurationMs) * W
                                    
                                    // Dim deleted segments
                                    if (seg.isDeleted) {
                                        drawRect(
                                            color = errorColor.copy(alpha = 0.65f),
                                            topLeft = Offset(sX, 0f),
                                            size = GeoSize(eX - sX, H)
                                        )
                                    }

                                    // Draw split boundaries
                                    if (seg.startMs > 0L) {
                                        drawLine(
                                            color = Color.Black.copy(alpha = 0.8f),
                                            start = Offset(sX, 0f),
                                            end = Offset(sX, H),
                                            strokeWidth = 4f
                                        )
                                    }
                                }

                                // Selected segment border highlight
                                if (currentActiveSegForHighlight != null) {
                                    val sX = (currentActiveSegForHighlight.startMs.toFloat() / videoDurationMs) * W
                                    val eX = (currentActiveSegForHighlight.endMs.toFloat() / videoDurationMs) * W
                                    drawRect(
                                        color = highlightColor,
                                        topLeft = Offset(sX, 0f),
                                        size = GeoSize(eX - sX, H),
                                        style = Stroke(width = 4f)
                                    )
                                }
                            }

                            // Render handles for the selected/active segment
                            val currentActiveIdForHandles = draggedSegmentId ?: selectedSegmentId ?: segments.firstOrNull { currentPosMs in it.startMs until it.endMs }?.id
                            val currentActiveSegForHandles = segments.firstOrNull { it.id == currentActiveIdForHandles }
                            if (currentActiveSegForHandles != null) {
                                val hColor = if (currentActiveSegForHandles.isDeleted) errorColor else primaryColor
                                val startFracOfSeg = currentActiveSegForHandles.startMs.toFloat() / videoDurationMs
                                val endFracOfSeg = currentActiveSegForHandles.endMs.toFloat() / videoDurationMs
                                
                                val halfHandleWidthPx = with(density) { 14.dp.toPx() }
                                val maxOffsetPx = (timelineWidthPx - with(density) { 28.dp.toPx() }).coerceAtLeast(0f)
                                
                                // Left handle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = with(density) { (startFracOfSeg * timelineWidthPx - halfHandleWidthPx).coerceIn(0f, maxOffsetPx).toDp() })
                                        .width(28.dp)
                                        .fillMaxHeight()
                                        .background(hColor.copy(alpha = 0.9f), RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("◁", color = Color.White, fontSize = 14.sp)
                                }
                                // Right handle
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .offset(x = with(density) { (endFracOfSeg * timelineWidthPx - halfHandleWidthPx).coerceIn(0f, maxOffsetPx).toDp() })
                                        .width(28.dp)
                                        .fillMaxHeight()
                                        .background(hColor.copy(alpha = 0.9f), RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("▷", color = Color.White, fontSize = 14.sp)
                                }
                            }

                            // Playhead line
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val x = playFrac * size.width
                                drawLine(
                                    color = Color.White,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = 3f
                                )
                                drawCircle(color = Color.White, radius = 6f, center = Offset(x, 0f))
                            }

                            val handleTouchPx = with(density) { 36.dp.toPx() }
                            // 0 = scrub, 1 = start handle, 2 = end handle
                            var dragTarget by remember { mutableStateOf(0) }
                            var lastSeekMs by remember { mutableLongStateOf(0L) }

                            // Drag / Tap target
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(videoUri) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                if (timelineWidthPx <= 0f) return@detectDragGestures
                                                exoPlayer.pause()
                                                isDragging = true
                                                val currentActiveId = selectedSegmentId ?: segments.firstOrNull { currentPosMs in it.startMs until it.endMs }?.id
                                                draggedSegmentId = currentActiveId
                                                val currentActiveSeg = segments.firstOrNull { it.id == currentActiveId }
                                                if (currentActiveSeg != null) {
                                                    val sX = (currentActiveSeg.startMs.toFloat() / videoDurationMs) * timelineWidthPx
                                                    val eX = (currentActiveSeg.endMs.toFloat() / videoDurationMs) * timelineWidthPx
                                                    val distStart = abs(offset.x - sX)
                                                    val distEnd = abs(offset.x - eX)
                                                    dragTarget = if (distStart <= handleTouchPx || distEnd <= handleTouchPx) {
                                                        if (distStart < distEnd) 1 else 2
                                                    } else {
                                                        0
                                                    }
                                                } else {
                                                    dragTarget = 0
                                                }
                                            },
                                            onDragEnd = { 
                                                isDragging = false 
                                                isSeeking = true
                                                seekTimestamp = System.currentTimeMillis()
                                                draggedSegmentId = null
                                                exoPlayer.seekTo(currentPosMs)
                                            },
                                            onDragCancel = { 
                                                isDragging = false 
                                                isSeeking = true
                                                seekTimestamp = System.currentTimeMillis()
                                                draggedSegmentId = null
                                                exoPlayer.seekTo(currentPosMs)
                                            },
                                            onDrag = { change, _ ->
                                                if (timelineWidthPx <= 0f) return@detectDragGestures
                                                val x = change.position.x.coerceIn(0f, timelineWidthPx)
                                                val frac = x / timelineWidthPx
                                                val newMs = (frac * videoDurationMs).toLong()
                                                
                                                val currentActiveId = draggedSegmentId ?: selectedSegmentId ?: segments.firstOrNull { currentPosMs in it.startMs until it.endMs }?.id
                                                val targetSegIdx = segments.indexOfFirst { it.id == currentActiveId }
                                                val now = System.currentTimeMillis()
                                                
                                                if (targetSegIdx != -1 && (dragTarget == 1 || dragTarget == 2)) {
                                                    val targetSeg = segments[targetSegIdx]
                                                    val updatedList = segments.toMutableList()
                                                    if (dragTarget == 1) {
                                                        val minVal = if (targetSegIdx > 0) segments[targetSegIdx - 1].startMs + 300L else 0L
                                                        val maxVal = targetSeg.endMs - 300L
                                                        val finalStartMs = newMs.coerceIn(minVal.coerceAtMost(maxVal), maxVal)
                                                        updatedList[targetSegIdx] = targetSeg.copy(startMs = finalStartMs)
                                                        if (targetSegIdx > 0) {
                                                            updatedList[targetSegIdx - 1] = updatedList[targetSegIdx - 1].copy(endMs = finalStartMs)
                                                        }
                                                        if (now - lastSeekMs > 60L) {
                                                            lastSeekMs = now
                                                            exoPlayer.seekTo(finalStartMs)
                                                        }
                                                        playFrac = (finalStartMs.toFloat() / videoDurationMs).coerceIn(0f, 1f)
                                                        currentPosMs = finalStartMs
                                                    } else {
                                                        val minVal = targetSeg.startMs + 300L
                                                        val maxVal = if (targetSegIdx < segments.size - 1) segments[targetSegIdx + 1].endMs - 300L else videoDurationMs
                                                        val finalEndMs = newMs.coerceIn(minVal.coerceAtMost(maxVal), maxVal)
                                                        updatedList[targetSegIdx] = targetSeg.copy(endMs = finalEndMs)
                                                        if (targetSegIdx < segments.size - 1) {
                                                            updatedList[targetSegIdx + 1] = updatedList[targetSegIdx + 1].copy(startMs = finalEndMs)
                                                        }
                                                        if (now - lastSeekMs > 60L) {
                                                            lastSeekMs = now
                                                            exoPlayer.seekTo(finalEndMs)
                                                        }
                                                        playFrac = (finalEndMs.toFloat() / videoDurationMs).coerceIn(0f, 1f)
                                                        currentPosMs = finalEndMs
                                                    }
                                                    segments = updatedList
                                                } else {
                                                    playFrac = frac.coerceIn(0f, 1f)
                                                    currentPosMs = newMs
                                                    if (now - lastSeekMs > 60L) {
                                                        lastSeekMs = now
                                                        exoPlayer.seekTo(newMs)
                                                    }
                                                }
                                                change.consume()
                                            }
                                        )
                                    }
                                    .pointerInput(videoUri) {
                                        detectTapGestures { offset ->
                                            if (timelineWidthPx <= 0f) return@detectTapGestures
                                            val frac = (offset.x / timelineWidthPx).coerceIn(0f, 1f)
                                            val clickMs = (frac * videoDurationMs).toLong()
                                            val clickedSeg = segments.firstOrNull { clickMs in it.startMs until it.endMs }
                                            if (clickedSeg != null) {
                                                selectedSegmentId = clickedSeg.id
                                            }
                                            isSeeking = true
                                            seekTimestamp = System.currentTimeMillis()
                                            playFrac = frac
                                            currentPosMs = clickMs
                                            exoPlayer.seekTo(clickMs)
                                        }
                                    }
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        // Time ruler ticks
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { f ->
                                Text(
                                    formatTimeShort((f * videoDurationMs).toLong()),
                                    color = Color.Gray,
                                    fontSize = 9.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // ── Fine adjustment controls ───────────────────────────
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Tap timeline to select parts, then split or delete/restore them",
                                color = Color.Gray, fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { splitAtPlayhead() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Lucide.Scissors, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Split here", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                val isSelectedDeleted = segments.firstOrNull { it.id == activeId }?.isDeleted == true
                                Button(
                                    onClick = { toggleDeleteSelected() },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelectedDeleted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSelectedDeleted) Lucide.RotateCcw else Lucide.Eraser,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isSelectedDeleted) "Restore part" else "Delete part",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }

    // ── Processing overlay ────────────────────────────────────────────────────
    if (isProcessing) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = processingLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(52.dp),
                        strokeWidth = 4.dp
                    )
                    Spacer(Modifier.height(20.dp))
                    LinearProgressIndicator(
                        progress = { processingProgress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.DarkGray,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${(processingProgress * 100).toInt()}%",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Please keep ScreenX open",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = {
                Text(
                    text = "Discard changes?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.5).sp
                )
            },
            text = {
                Text(
                    text = "You have unsaved edits. Are you sure you want to discard them and exit?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmation = false
                        onBackClick()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(
                        "Discard",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitConfirmation = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        "Keep Editing",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

private fun executeExport(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    sourceUri: Uri,
    videoName: String,
    activeSegments: List<Pair<Long, Long>>,
    setProcessing: (Boolean) -> Unit,
    setProgress: (Float) -> Unit,
    setLabel: (String) -> Unit,
    onSuccess: () -> Unit
) {
    setProcessing(true)
    setLabel("Exporting Video…")
    val progressCb = makeProgressCallback(scope, setProgress)
    scope.launch(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = File(context.cacheDir, "export_${System.currentTimeMillis()}.mp4")
            VideoTrimmer.exportSegments(
                context = context,
                sourceUri = sourceUri,
                outputFile = tempFile,
                segments = activeSegments,
                progressCallback = progressCb
            )
            val totalDurationMs = activeSegments.sumOf { it.second - it.first }
            saveVideoToMediaStore(context, tempFile, videoName, totalDurationMs)
            tempFile = null
            withContext(Dispatchers.Main) {
                setProcessing(false)
                Toast.makeText(context, "Video edited & saved successfully!", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile?.delete()
            withContext(Dispatchers.Main) {
                setProcessing(false)
                Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// ─── Small composable helpers ─────────────────────────────────────────────────

@Composable
private fun InfoChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun FineAdjustColumn(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallAdjustBtn("-0.1s", onMinus)
            SmallAdjustBtn("+0.1s", onPlus)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SmallAdjustBtn("-1s") {
                repeat(10) { onMinus() }
            }
            SmallAdjustBtn("+1s") {
                repeat(10) { onPlus() }
            }
        }
    }
}

@Composable
private fun SmallAdjustBtn(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.height(28.dp)
    ) {
        Text(text, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ─── Video editing operations ─────────────────────────────────────────────────

/** Throttled progress helper — only dispatches when delta > 1% to avoid coroutine floods. */
private fun makeProgressCallback(
    scope: kotlinx.coroutines.CoroutineScope,
    setProgress: (Float) -> Unit
): (Float) -> Unit {
    var lastReported = -1f
    return { p ->
        if (p - lastReported >= 0.01f || p >= 1f) {
            lastReported = p
            scope.launch(Dispatchers.Main) { setProgress(p) }
        }
    }
}

private fun executeTrim(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    sourceUri: Uri,
    videoName: String,
    startMs: Long,
    endMs: Long,
    setProcessing: (Boolean) -> Unit,
    setProgress: (Float) -> Unit,
    setLabel: (String) -> Unit,
    onSuccess: () -> Unit
) {
    if (endMs <= startMs + 500L) {
        Toast.makeText(context, "Selection must be at least 0.5 seconds", Toast.LENGTH_SHORT).show()
        return
    }
    setProcessing(true)
    setLabel("Trimming Video…")
    val progressCb = makeProgressCallback(scope, setProgress)
    scope.launch(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = File(context.cacheDir, "trim_${System.currentTimeMillis()}.mp4")
            VideoTrimmer.trim(
                context = context,
                sourceUri = sourceUri,
                outputFile = tempFile,
                startMs = startMs,
                endMs = endMs,
                progressCallback = progressCb
            )
            saveVideoToMediaStore(context, tempFile, videoName, endMs - startMs)
            tempFile = null  // saveVideoToMediaStore deletes it
            withContext(Dispatchers.Main) {
                setProcessing(false)
                Toast.makeText(context, "Trimmed video saved!", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile?.delete()
            withContext(Dispatchers.Main) {
                setProcessing(false)
                Toast.makeText(context, "Trim failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun executeDelete(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    sourceUri: Uri,
    videoName: String,
    totalDurationMs: Long,
    cutStartMs: Long,
    cutEndMs: Long,
    setProcessing: (Boolean) -> Unit,
    setProgress: (Float) -> Unit,
    setLabel: (String) -> Unit,
    onSuccess: () -> Unit
) {
    if (cutEndMs <= cutStartMs + 500L) {
        Toast.makeText(context, "Delete segment must be at least 0.5 seconds", Toast.LENGTH_SHORT).show()
        return
    }
    setProcessing(true)
    setLabel("Deleting Segment…")
    val progressCb = makeProgressCallback(scope, setProgress)
    scope.launch(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = File(context.cacheDir, "delete_${System.currentTimeMillis()}.mp4")
            VideoTrimmer.cutMiddle(
                context = context,
                sourceUri = sourceUri,
                outputFile = tempFile,
                cutStartMs = cutStartMs,
                cutEndMs = cutEndMs,
                progressCallback = progressCb
            )
            val outDuration = (totalDurationMs - (cutEndMs - cutStartMs)).coerceAtLeast(0L)
            saveVideoToMediaStore(context, tempFile, videoName, outDuration)
            tempFile = null
            withContext(Dispatchers.Main) {
                setProcessing(false)
                Toast.makeText(context, "Segment deleted & saved!", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile?.delete()
            withContext(Dispatchers.Main) {
                setProcessing(false)
                Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

private fun executeSplit(
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    sourceUri: Uri,
    videoName: String,
    totalDurationMs: Long,
    splitMs: Long,
    setProcessing: (Boolean) -> Unit,
    setProgress: (Float) -> Unit,
    setLabel: (String) -> Unit,
    onSuccess: () -> Unit
) {
    if (splitMs < 500L) {
        Toast.makeText(context, "Split point must be at least 0.5s from start", Toast.LENGTH_SHORT).show()
        return
    }
    // Guard: Part B would be empty if split is at the very end
    if (totalDurationMs > 0 && splitMs >= totalDurationMs - 500L) {
        Toast.makeText(context, "Split point must be at least 0.5s from end", Toast.LENGTH_SHORT).show()
        return
    }
    setProcessing(true)
    setLabel("Splitting Video…")
    val progressCb = makeProgressCallback(scope, setProgress)
    scope.launch(Dispatchers.IO) {
        val ts   = System.currentTimeMillis()
        val outA = File(context.cacheDir, "splitA_$ts.mp4")
        val outB = File(context.cacheDir, "splitB_${ts + 1}.mp4")
        try {
            VideoTrimmer.split(
                context = context,
                sourceUri = sourceUri,
                outputA = outA,
                outputB = outB,
                splitMs = splitMs,
                progressCallback = progressCb
            )
            val cleanName  = videoName.substringBefore(".mp4")
            val partBDurMs = (totalDurationMs - splitMs).coerceAtLeast(0L)
            saveVideoToMediaStore(context, outA, "${cleanName}_part1.mp4", splitMs)
            outA.takeIf { it.exists() }?.delete()  // deleted by saveVideoToMediaStore; just in case
            saveVideoToMediaStore(context, outB, "${cleanName}_part2.mp4", partBDurMs)
            outB.takeIf { it.exists() }?.delete()
            withContext(Dispatchers.Main) {
                setProcessing(false)
                Toast.makeText(context, "Split into Part 1 & Part 2 — saved!", Toast.LENGTH_SHORT).show()
                onSuccess()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Clean up temp files on failure
            outA.takeIf { it.exists() }?.delete()
            outB.takeIf { it.exists() }?.delete()
            withContext(Dispatchers.Main) {
                setProcessing(false)
                Toast.makeText(context, "Split failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

// ─── Utility functions ────────────────────────────────────────────────────────

private suspend fun loadFrameThumbnails(
    context: Context,
    uri: Uri,
    durationMs: Long,
    count: Int
): List<Bitmap?> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        (0 until count).map { i ->
            val timeUs = (i.toLong() * durationMs / count) * 1000L
            try {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) { null }
        }
    } catch (_: Exception) {
        List(count) { null }
    } finally {
        retriever.release()
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00.0"
    val milli = (ms % 1000) / 100
    val sec   = (ms / 1000) % 60
    val min   = (ms / 60000) % 60
    val hour  = ms / 3600000
    return if (hour > 0)
        String.format("%d:%02d:%02d.%d", hour, min, sec, milli)
    else
        String.format("%02d:%02d.%d", min, sec, milli)
}

private fun formatTimeShort(ms: Long): String {
    if (ms < 0) return "0s"
    val sec = ms / 1000
    val min = sec / 60
    return if (min > 0) "${min}m${sec % 60}s" else "${sec}s"
}

private fun queryVideoDuration(context: Context, uri: Uri): Long {
    var duration = 0L
    try {
        context.contentResolver.query(
            uri, arrayOf(MediaStore.Video.Media.DURATION), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
            }
        }
    } catch (_: Exception) {}
    if (duration <= 0L) {
        try {
            val r = MediaMetadataRetriever()
            r.setDataSource(context, uri)
            duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            r.release()
        } catch (_: Exception) {}
    }
    return duration
}

private fun saveVideoToMediaStore(
    context: Context,
    tempFile: File,
    originalName: String,
    durationMs: Long
) {
    val cleanName = originalName.substringBefore(".mp4")
    val outName = "${cleanName}_${System.currentTimeMillis()}.mp4"
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, outName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenX")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
    }
    val resolver = context.contentResolver
    val videoUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return
    try {
        resolver.openOutputStream(videoUri)?.use { out ->
            tempFile.inputStream().use { it.copyTo(out) }
        }
        val updateValues = ContentValues().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.Video.Media.IS_PENDING, 0)
            if (durationMs != Long.MAX_VALUE) put(MediaStore.Video.Media.DURATION, durationMs)
            put(MediaStore.Video.Media.SIZE, tempFile.length())
        }
        resolver.update(videoUri, updateValues, null, null)
        val refreshIntent = android.content.Intent("com.gxdevs.screenx.action.RECORDING_SAVED").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(refreshIntent)
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        tempFile.delete()
    }
}

// ─── Recent videos card ───────────────────────────────────────────────────────

@Composable
fun RecentSelectCard(video: RecordedVideo, onClick: () -> Unit) {
    val context = LocalContext.current
    var thumbnail by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(video.uri) {
        thumbnail = VideoHelper.loadThumbnail(context, video.uri, video.isVideo)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail!!.asImageBitmap(), null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Lucide.CirclePlay, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(video.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Duration: ${VideoHelper.formatDuration(video.duration)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Lucide.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
