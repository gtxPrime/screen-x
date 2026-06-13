package com.gxdevs.screenx.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.gxdevs.screenx.utils.RecordedVideo
import com.gxdevs.screenx.utils.VideoHelper
import com.gxdevs.screenx.utils.VideoTrimmer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    
    var selectedVideo by remember { mutableStateOf<RecordedVideo?>(initialVideo) }
    var selectedUri by remember { mutableStateOf<Uri?>(initialVideo?.uri) }
    var videoName by remember { mutableStateOf(initialVideo?.name ?: "") }
    var videoDurationMs by remember { mutableStateOf(initialVideo?.duration ?: 0L) }

    // External video picker
    val systemVideoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            selectedVideo = null
            videoName = "External_Video_${System.currentTimeMillis()}.mp4"
            // Query duration if possible
            coroutineScope.launch(Dispatchers.IO) {
                val duration = queryVideoDuration(context, uri)
                withContext(Dispatchers.Main) {
                    videoDurationMs = duration
                }
            }
        }
    }

    if (selectedUri == null) {
        // Media Selection Screen
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Select Video to Trim", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
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
                // Option to select from phone
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clickable { systemVideoPickerLauncher.launch("video/*") }
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Choose from Device",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
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

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "Recent Captures",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (videos.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recordings available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(videos) { video ->
                            RecentSelectCard(video = video, onClick = {
                                selectedVideo = video
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
        // Trimmer Editor Screen
        TrimmerEditor(
            videoUri = selectedUri!!,
            videoName = videoName,
            videoDurationMs = videoDurationMs,
            onBackClick = { selectedUri = null },
            onTrimSuccess = onTrimSuccess
        )
    }
}

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

    var startVal by remember { mutableStateOf(0.0f) }
    var endVal by remember { mutableStateOf(1.0f) }
    var isTrimming by remember { mutableStateOf(false) }
    var trimProgress by remember { mutableStateOf(0f) }
    var isCutMiddleMode by remember { mutableStateOf(false) }
    var currentPosMs by remember { mutableStateOf(0L) }

    val startMs = (startVal * videoDurationMs).toLong()
    val endMs = (endVal * videoDurationMs).toLong()

    val exoPlayer = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }

    // Update playback constraints based on start/end range
    LaunchedEffect(startMs, endMs, isCutMiddleMode) {
        if (!isCutMiddleMode) {
            exoPlayer.seekTo(startMs)
        } else {
            // Seek to loop beginning in cut middle mode
            exoPlayer.seekTo(0)
        }
    }

    // Periodically monitor playback position and loop within selected range
    LaunchedEffect(exoPlayer, isCutMiddleMode, startMs, endMs) {
        while (true) {
            delay(50)
            currentPosMs = exoPlayer.currentPosition
            if (exoPlayer.isPlaying) {
                if (!isCutMiddleMode) {
                    if (exoPlayer.currentPosition >= endMs || exoPlayer.currentPosition < startMs) {
                        exoPlayer.seekTo(startMs)
                    }
                } else {
                    // Loop around the cut (skipping the middle section)
                    val pos = exoPlayer.currentPosition
                    if (pos >= startMs && pos < endMs) {
                        exoPlayer.seekTo(endMs)
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isCutMiddleMode) "Cut Middle" else "Trim Video",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(
                        onClick = {
                            if (endMs <= startMs + 1000) {
                                Toast.makeText(context, "Range must be at least 1 second", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isTrimming = true
                            coroutineScope.launch(Dispatchers.IO) {
                                try {
                                    val tempFile = File(context.cacheDir, "trimmed_${System.currentTimeMillis()}.mp4")
                                    if (isCutMiddleMode) {
                                        VideoTrimmer.cutMiddle(
                                            context = context,
                                            sourceUri = videoUri,
                                            outputFile = tempFile,
                                            cutStartMs = startMs,
                                            cutEndMs = endMs,
                                            progressCallback = { progress ->
                                                trimProgress = progress
                                            }
                                        )
                                        saveVideoToMediaStore(context, tempFile, videoName, videoDurationMs - (endMs - startMs))
                                    } else {
                                        VideoTrimmer.trim(
                                            context = context,
                                            sourceUri = videoUri,
                                            outputFile = tempFile,
                                            startMs = startMs,
                                            endMs = endMs,
                                            progressCallback = { progress ->
                                                trimProgress = progress
                                            }
                                        )
                                        saveVideoToMediaStore(context, tempFile, videoName, endMs - startMs)
                                    }
                                    withContext(Dispatchers.Main) {
                                        isTrimming = false
                                        Toast.makeText(context, "Video processed successfully!", Toast.LENGTH_SHORT).show()
                                        onTrimSuccess()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    withContext(Dispatchers.Main) {
                                        isTrimming = false
                                        Toast.makeText(context, "Failed to edit video: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF121214)) // Cinematic Dark Background
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Player viewport
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .clip(RoundedCornerShape(24.dp))
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

                // Quick Play/Pause Center Overlay
                var isPlaying by remember { mutableStateOf(false) }
                LaunchedEffect(exoPlayer) {
                    isPlaying = exoPlayer.isPlaying
                    exoPlayer.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isPlaying = playing
                        }
                    })
                }

                // Controls row at the bottom of the Box
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 5000).coerceAtLeast(0)) }
                    ) {
                        Icon(imageVector = Icons.Default.Replay5, contentDescription = "-5s", tint = Color.White)
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = { if (isPlaying) exoPlayer.pause() else exoPlayer.play() },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 5000).coerceAtMost(videoDurationMs)) }
                    ) {
                        Icon(imageVector = Icons.Default.Forward5, contentDescription = "+5s", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Editor Mode Segmented Switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1B1B1F), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isCutMiddleMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { isCutMiddleMode = false }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Keep Selection",
                        color = if (!isCutMiddleMode) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCutMiddleMode) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { isCutMiddleMode = true }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Delete Selection (Middle)",
                        color = if (isCutMiddleMode) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Split and Playhead Info Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Playhead: ${formatTime(currentPosMs)}",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Button(
                    onClick = {
                        val playheadPct = currentPosMs.toFloat() / videoDurationMs.toFloat()
                        if (startVal == 0.0f && endVal == 1.0f) {
                            startVal = playheadPct.coerceIn(0.0f, 1.0f)
                            isCutMiddleMode = true
                        } else if (startVal > 0.0f && endVal == 1.0f) {
                            endVal = playheadPct.coerceIn(startVal + 0.01f, 1.0f)
                        } else {
                            if (Math.abs(playheadPct - startVal) < Math.abs(playheadPct - endVal)) {
                                startVal = playheadPct.coerceIn(0.0f, endVal - 0.01f)
                            } else {
                                endVal = playheadPct.coerceIn(startVal + 0.01f, 1.0f)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Split at Playhead", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Range Controls
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B1F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Display timestamps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(if (isCutMiddleMode) "Cut Start" else "Start Time", fontSize = 11.sp, color = Color.Gray)
                            Text(formatTime(startMs), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(if (isCutMiddleMode) "Cut End" else "End Time", fontSize = 11.sp, color = Color.Gray)
                            Text(formatTime(endMs), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (isCutMiddleMode) {
                            "Output Duration: ${formatTime(videoDurationMs - (endMs - startMs))}"
                        } else {
                            "Trimmed Duration: ${formatTime(endMs - startMs)}"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Range Slider
                    RangeSlider(
                        value = startVal..endVal,
                        onValueChange = { range ->
                            startVal = range.start
                            endVal = range.endInclusive
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Fine Tuning Controls
                    Text("Fine Tuning Adjustments", fontSize = 12.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Start Adjust", fontSize = 10.sp, color = Color.Gray)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { startVal = (startVal - 500f / videoDurationMs).coerceIn(0f, endVal - 0.01f) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("-0.5s", fontSize = 10.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { startVal = (startVal + 500f / videoDurationMs).coerceIn(0f, endVal - 0.01f) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+0.5s", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("End Adjust", fontSize = 10.sp, color = Color.Gray)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    onClick = { endVal = (endVal - 500f / videoDurationMs).coerceIn(startVal + 0.01f, 1f) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("-0.5s", fontSize = 10.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { endVal = (endVal + 500f / videoDurationMs).coerceIn(startVal + 0.01f, 1f) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("+0.5s", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Trimming Loader Overlay
    if (isTrimming) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Trimming Video...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Processing frame samples. Please keep ScreenX open.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun RecentSelectCard(video: RecordedVideo, onClick: () -> Unit) {
    val context = LocalContext.current
    var thumbnail by remember(video.uri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(video.uri) {
        thumbnail = loadVideoThumbnail(context, video.uri)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
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
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Duration: ${VideoHelper.formatDuration(video.duration)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hour = (ms / (1000 * 60 * 60)) % 24
    val milli = (ms % 1000) / 100

    return if (hour > 0) {
        String.format("%d:%02d:%02d.%d", hour, min, sec, milli)
    } else {
        String.format("%02d:%02d.%d", min, sec, milli)
    }
}

private fun queryVideoDuration(context: Context, uri: Uri): Long {
    var duration = 0L
    val projection = arrayOf(MediaStore.Video.Media.DURATION)
    try {
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    // Fallback: Use MediaMetadataRetriever
    if (duration <= 0L) {
        try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            duration = durationStr?.toLong() ?: 0L
            retriever.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return duration
}

private fun saveVideoToMediaStore(context: Context, tempFile: File, originalName: String, durationMs: Long) {
    val cleanName = originalName.substringBefore(".mp4")
    val trimmedName = "${cleanName}_trimmed_${System.currentTimeMillis()}.mp4"
    val contentValues = ContentValues().apply {
        put(MediaStore.Video.Media.DISPLAY_NAME, trimmedName)
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenX")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
    }

    val resolver = context.contentResolver
    val collectionUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val videoUri = resolver.insert(collectionUri, contentValues)

    if (videoUri != null) {
        try {
            resolver.openOutputStream(videoUri)?.use { outputStream ->
                tempFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            val updateValues = ContentValues().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                put(MediaStore.Video.Media.DURATION, durationMs)
                put(MediaStore.Video.Media.RESOLUTION, "Trimmed")
                put(MediaStore.Video.Media.SIZE, tempFile.length())
            }
            resolver.update(videoUri, updateValues, null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }
}
