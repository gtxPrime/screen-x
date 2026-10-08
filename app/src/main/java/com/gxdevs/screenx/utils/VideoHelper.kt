package com.gxdevs.screenx.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecordedVideo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val dateAdded: Long,
    val resolution: String,
    val isVideo: Boolean = true
)

object VideoHelper {

    private val thumbnailCache = androidx.collection.LruCache<Uri, Bitmap>(64)

    suspend fun loadThumbnail(context: Context, uri: Uri, isVideo: Boolean = true): Bitmap? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        thumbnailCache.get(uri)?.let { return@withContext it }
        try {
            var bitmap: Bitmap? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    bitmap = context.contentResolver.loadThumbnail(uri, android.util.Size(320, 320), null)
                } catch (_: Exception) {}
            } else {
                try {
                    if (isVideo) {
                        val videoId = ContentUris.parseId(uri)
                        @Suppress("DEPRECATION")
                        bitmap = MediaStore.Video.Thumbnails.getThumbnail(
                            context.contentResolver,
                            videoId,
                            MediaStore.Video.Thumbnails.MINI_KIND,
                            null
                        )
                    } else {
                        val imageId = ContentUris.parseId(uri)
                        @Suppress("DEPRECATION")
                        bitmap = MediaStore.Images.Thumbnails.getThumbnail(
                            context.contentResolver,
                            imageId,
                            MediaStore.Images.Thumbnails.MINI_KIND,
                            null
                        )
                    }
                } catch (_: Exception) {}
            }

            // Reliable direct frame extraction fallback for newly recorded videos
            if (bitmap == null && isVideo) {
                try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    bitmap = retriever.getFrameAtTime(500000, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(0, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    retriever.release()
                } catch (_: Exception) {}
            }

            if (bitmap != null) {
                thumbnailCache.put(uri, bitmap)
            }
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun invalidateThumbnail(uri: Uri) {
        thumbnailCache.remove(uri)
    }

    @SuppressLint("Range")
    fun fetchVideos(context: Context): List<RecordedVideo> {
        val videos = mutableListOf<RecordedVideo>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        
        // Define columns to retrieve
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.RESOLUTION
        )

        // Query files inside Movies/ScreenX or files starting with ScreenX
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        } else {
            "${MediaStore.Video.Media.DATA} LIKE ? OR ${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?"
        }
        val selectionArgs = arrayOf("%Movies/ScreenX%", "ScreenX_%")

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id       = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                    val name     = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)) ?: continue
                    // DATA can be null on Android 10+ with scoped storage
                    val path     = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.DATA))
                    val size     = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE))
                    val duration = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED))
                    val resolution = cursor.getString(cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)) ?: "Unknown"

                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    // On Scoped Storage (API 29+), items returned by MediaStore are valid ContentUris.
                    // Direct java.io.File(path).exists() can fail or lag due to FUSE permissions.
                    val fileExists = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && path != null) {
                        try { File(path).exists() } catch (_: Exception) { true }
                    } else {
                        true
                    }
                    if (fileExists) {
                        videos.add(
                            RecordedVideo(
                                id         = id,
                                uri        = contentUri,
                                name       = name,
                                path       = path ?: "",
                                size       = size,
                                duration   = duration,
                                dateAdded  = dateAdded,
                                resolution = resolution,
                                isVideo    = true
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Also query Screenshots from Pictures/ScreenX
        try {
            val imgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val imgProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )
            val imgSelection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            } else {
                "${MediaStore.Images.Media.DATA} LIKE ? OR ${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
            }
            val imgSelectionArgs = arrayOf("%Pictures/ScreenX%", "ScreenX_Screenshot_%")

            context.contentResolver.query(
                imgUri,
                imgProjection,
                imgSelection,
                imgSelectionArgs,
                sortOrder
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id        = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val name      = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: continue
                    val path      = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA))
                    val size      = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE))
                    val dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))

                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    val fileExists = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && path != null) {
                        try { File(path).exists() } catch (_: Exception) { true }
                    } else {
                        true
                    }
                    if (fileExists) {
                        videos.add(
                            RecordedVideo(
                                id         = id,
                                uri        = contentUri,
                                name       = name,
                                path       = path ?: "",
                                size       = size,
                                duration   = 0L,
                                dateAdded  = dateAdded,
                                resolution = "Screenshot",
                                isVideo    = false
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        videos.sortByDescending { it.dateAdded }
        return videos
    }

    fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60)) % 24
        
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatDate(timestampS: Long): String {
        val date = Date(timestampS * 1000)
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        return sdf.format(date)
    }

    /**
     * Deletes a video or screenshot.
     */
    fun deleteVideo(context: Context, video: RecordedVideo, onRecoverableException: (Intent) -> Unit = {}): Boolean {
        return try {
            val rowsDeleted = context.contentResolver.delete(video.uri, null, null)
            if (rowsDeleted > 0) {
                invalidateThumbnail(video.uri)
                true
            } else {
                false
            }
        } catch (securityException: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = securityException as? RecoverableSecurityException
                    ?: throw securityException
                val intentSender = recoverableSecurityException.userAction.actionIntent.intentSender
                val intent = Intent().apply {
                    putExtra("intent_sender", intentSender)
                }
                onRecoverableException(intent)
                false
            } else {
                throw securityException
            }
        }
    }

    fun renameMedia(context: Context, item: RecordedVideo, newName: String): Boolean {
        var cleanName = newName.trim()
        if (item.isVideo) {
            if (!cleanName.endsWith(".mp4", ignoreCase = true)) cleanName += ".mp4"
        } else {
            if (!cleanName.endsWith(".png", ignoreCase = true) && !cleanName.endsWith(".jpg", ignoreCase = true)) {
                cleanName += ".png"
            }
        }
        val values = ContentValues().apply {
            put(if (item.isVideo) MediaStore.Video.Media.DISPLAY_NAME else MediaStore.Images.Media.DISPLAY_NAME, cleanName)
        }
        return try {
            val updated = context.contentResolver.update(item.uri, values, null, null)
            if (updated > 0) {
                invalidateThumbnail(item.uri)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun shareVideo(context: Context, video: RecordedVideo) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (video.isVideo) "video/mp4" else "image/png"
            putExtra(Intent.EXTRA_STREAM, video.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserTitle = if (video.isVideo) "Share Screen Recording" else "Share Screenshot"
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }
}
