package com.gxdevs.screenx.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gxdevs.screenx.utils.RecordedVideo
import com.gxdevs.screenx.utils.VideoHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    videos: List<RecordedVideo>,
    onBackClick: () -> Unit,
    onDeleteVideo: (RecordedVideo) -> Unit,
    onTrimVideoClick: (RecordedVideo) -> Unit,
    settingsManager: com.gxdevs.screenx.data.SettingsManager
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val isGridView by settingsManager.galleryGridViewFlow.collectAsState(initial = true)
    var selectedVideoForPlayback by remember { mutableStateOf<RecordedVideo?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Video Gallery",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            settingsManager.setGalleryGridView(!isGridView)
                        }
                    }) {
                        Icon(
                            imageVector = if (isGridView) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = "Toggle Layout"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .navigationBarsPadding()
        ) {
            if (videos.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No recorded videos yet",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(videos, key = { it.id }) { video ->
                            GalleryGridItem(
                                video = video,
                                onClick = { selectedVideoForPlayback = video },
                                onDelete = { onDeleteVideo(video) },
                                onTrim = { onTrimVideoClick(video) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(videos, key = { it.id }) { video ->
                            GalleryListItem(
                                video = video,
                                onClick = { selectedVideoForPlayback = video },
                                onDelete = { onDeleteVideo(video) },
                                onTrim = { onTrimVideoClick(video) }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedVideoForPlayback?.let { video ->
        VideoPlayerDialog(
            videoUri = video.uri,
            videoName = video.name,
            onDismiss = { selectedVideoForPlayback = null }
        )
    }
}

@Composable
fun GalleryGridItem(
    video: RecordedVideo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTrim: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    var expandedMenu by remember { mutableStateOf(false) }

    LaunchedEffect(video.uri) {
        thumbnail = loadVideoThumbnail(context, video.uri)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
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
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            // Duration Overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = VideoHelper.formatDuration(video.duration),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Title & Menu Overlay at the top
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color(0x44000000))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = video.name.substringAfterLast("ScreenX_").substringBefore(".mp4"),
                    color = Color.White,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Box {
                    IconButton(
                        onClick = { expandedMenu = true },
                        modifier = Modifier.size(28.dp)
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
                            text = { Text("Trim Video") },
                            onClick = {
                                expandedMenu = false
                                android.widget.Toast.makeText(context, "Trim feature coming soon!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) }
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
            }
        }
    }
}

@Composable
fun GalleryListItem(
    video: RecordedVideo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTrim: () -> Unit
) {
    val context = LocalContext.current
    var thumbnail by remember(video.uri) { mutableStateOf<Bitmap?>(null) }
    var expandedMenu by remember { mutableStateOf(false) }

    LaunchedEffect(video.uri) {
        thumbnail = loadVideoThumbnail(context, video.uri)
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                    .size(80.dp)
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
                        modifier = Modifier.size(32.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0x99000000), RoundedCornerShape(topStart = 6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = VideoHelper.formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
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
                        text = { Text("Trim Video") },
                        onClick = {
                            expandedMenu = false
                            android.widget.Toast.makeText(context, "Trim feature coming soon!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        leadingIcon = { Icon(Icons.Default.ContentCut, contentDescription = null) }
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
        }
    }
}
