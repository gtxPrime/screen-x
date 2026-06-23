package com.gxdevs.screenx.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.gxdevs.screenx.MainActivity
import com.gxdevs.screenx.data.SettingsManager
import com.gxdevs.screenx.utils.VideoHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer

@Suppress("DEPRECATION")
class ScreenRecordService : LifecycleService() {

    companion object {
        const val CHANNEL_ID = "ScreenX_Recording_Channel"
        const val NOTIFICATION_ID = 888

        // Intent Actions
        const val ACTION_START = "com.gxdevs.screenx.action.START"
        const val ACTION_PAUSE = "com.gxdevs.screenx.action.PAUSE"
        const val ACTION_RESUME = "com.gxdevs.screenx.action.RESUME"
        const val ACTION_STOP = "com.gxdevs.screenx.action.STOP"
        const val ACTION_EXIT = "com.gxdevs.screenx.action.EXIT"
        
        // Screenshot Action
        const val ACTION_SCREENSHOT = "com.gxdevs.screenx.action.SCREENSHOT"
        const val ACTION_START_FLOATING_ONLY = "com.gxdevs.screenx.action.START_FLOATING_ONLY"

        // Intent Extras
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        var isRecording = false
            private set
        var isPaused = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var projectionManager: MediaProjectionManager
    private lateinit var settingsManager: SettingsManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var audioCaptureHelper: AudioCaptureHelper? = null

    private var tempVideoFile: File? = null
    private var tempAudioFile: File? = null
    private var startTimeMs: Long = 0
    private var recordingDurationMs: Long = 0
    private var targetResolution = "1080p"

    private var floatingControlOverlay: FloatingControlOverlay? = null
    private var brushDrawingOverlay: BrushDrawingOverlay? = null
    private var countdownOverlay: CountdownOverlay? = null

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastUpdate: Long = 0
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val curTime = System.currentTimeMillis()
                if ((curTime - lastUpdate) > 100) {
                    val diffTime = curTime - lastUpdate
                    lastUpdate = curTime

                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val speed = Math.abs(x + y + z - lastX - lastY - lastZ) / diffTime * 10000

                    if (speed > 800) {
                        lifecycleScope.launch {
                            val shakeToStop = settingsManager.shakeToStopFlow.first()
                            if (shakeToStop && isRecording) {
                                Toast.makeText(this@ScreenRecordService, "Shake detected! Stopping...", Toast.LENGTH_SHORT).show()
                                stopRecording()
                            }
                        }
                    }
                    lastX = x
                    lastY = y
                    lastZ = z
                }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        settingsManager = SettingsManager(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        val action = intent?.action
        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultData != null) {
                    startRecordingSequence(resultCode, resultData)
                } else {
                    stopSelf()
                }
            }
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            ACTION_SCREENSHOT -> takeScreenshot()
            ACTION_EXIT -> exitService()
            ACTION_START_FLOATING_ONLY -> {
                val notification = createNotification("Floating controls active")
                startForeground(NOTIFICATION_ID, notification)
                showFloatingControls()
            }
        }
        
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ScreenX Recording Status"
            val descriptionText = "Displays status and controls for active screen recording"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(status: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMainIntent = PendingIntent.getActivity(
            this, 0, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Actions
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply { this.action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(this, 10, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val pauseIntent = Intent(this, ScreenRecordService::class.java).apply { this.action = ACTION_PAUSE }
        val pendingPause = PendingIntent.getService(this, 11, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val resumeIntent = Intent(this, ScreenRecordService::class.java).apply { this.action = ACTION_RESUME }
        val pendingResume = PendingIntent.getService(this, 12, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val exitIntent = Intent(this, ScreenRecordService::class.java).apply { this.action = ACTION_EXIT }
        val pendingExit = PendingIntent.getService(this, 13, exitIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ScreenX Recorder")
            .setContentText(status)
            .setSmallIcon(com.gxdevs.screenx.R.drawable.ic_notification)
            .setContentIntent(pendingMainIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (status == "Recording") {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pendingPause)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
        } else if (status == "Paused") {
            builder.addAction(android.R.drawable.ic_media_play, "Resume", pendingResume)
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
        }
        builder.addAction(android.R.drawable.ic_menu_delete, "Exit", pendingExit)

        return builder.build()
    }

    private fun updateNotification(status: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(status))
    }

    private fun startRecordingSequence(resultCode: Int, resultData: Intent) {
        // Start Foreground immediately for Android 14 requirements
        val initialNotification = createNotification("Starting countdown...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                initialNotification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        lifecycleScope.launch {
            val countdownSecs = settingsManager.countdownFlow.first()
            if (countdownSecs > 0) {
                // Show countdown overlay
                countdownOverlay = CountdownOverlay(this@ScreenRecordService)
                countdownOverlay?.show {
                    // When countdown finishes
                    startRecording(resultCode, resultData)
                    countdownOverlay?.dismiss()
                    countdownOverlay = null
                }
            } else {
                startRecording(resultCode, resultData)
            }
        }
    }

    private fun startRecording(resultCode: Int, resultData: Intent) {
        try {
            isRecording = true
            isPaused = false
            startTimeMs = System.currentTimeMillis()
            updateNotification("Recording")

            // Load settings
            lifecycleScope.launch {
                val fps = settingsManager.fpsFlow.first()
                val bitrate = settingsManager.bitrateFlow.first()
                val audioSource = settingsManager.audioSourceFlow.first()
                targetResolution = settingsManager.resolutionFlow.first()
                val showFloating = settingsManager.showFloatingFlow.first()
                val orientationSetting = settingsManager.orientationFlow.first()

                setupMediaRecorder(fps, bitrate, audioSource, orientationSetting)

                // Get projection
                mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
                
                // Set callbacks
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopRecording()
                    }
                }, Handler(Looper.getMainLooper()))

                // Start audio capturing helper if it's System or MicSystem
                if (audioSource == "System" || audioSource == "MicSystem") {
                    tempAudioFile = File(cacheDir, "temp_audio.m4a")
                    if (tempAudioFile?.exists() == true) {
                        tempAudioFile?.delete()
                    }
                    audioCaptureHelper = AudioCaptureHelper(
                        context = this@ScreenRecordService,
                        mediaProjection = mediaProjection,
                        audioSource = audioSource,
                        outputFile = tempAudioFile!!
                    )
                    audioCaptureHelper?.start()
                } else {
                    tempAudioFile = null
                    audioCaptureHelper = null
                }

                // Metrics
                val metrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(metrics)
                val screenWidth = metrics.widthPixels
                val screenHeight = metrics.heightPixels
                
                val dimensions = getTargetDimensions(targetResolution, screenWidth, screenHeight, orientationSetting)
                val recordWidth = dimensions.first
                val recordHeight = dimensions.second

                // Virtual Display
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "ScreenXDisplay",
                    recordWidth,
                    recordHeight,
                    metrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    mediaRecorder?.surface,
                    null,
                    null
                )

                // Start recording
                mediaRecorder?.start()

                // Show floating controls if set
                if (showFloating) {
                    showFloatingControls()
                }

                // Register shake-to-stop detector
                val shakeToStop = settingsManager.shakeToStopFlow.first()
                if (shakeToStop) {
                    sensorManager?.registerListener(
                        sensorListener,
                        accelerometer,
                        SensorManager.SENSOR_DELAY_NORMAL
                    )
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            isRecording = false
            stopSelf()
        }
    }

    private fun setupMediaRecorder(fps: Int, bitrate: Int, audioSource: String, orientationSetting: String) {
        // Create temp output file
        tempVideoFile = File(cacheDir, "temp_recording.mp4")
        if (tempVideoFile?.exists() == true) {
            tempVideoFile?.delete()
        }

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        mediaRecorder?.apply {
            val hasAudio = audioSource == "Mic"
            if (hasAudio) {
                setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            
            // Set paths
            setOutputFile(tempVideoFile?.absolutePath)

            // Setup dimensions
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val dimensions = getTargetDimensions(targetResolution, metrics.widthPixels, metrics.heightPixels, orientationSetting)
            setVideoSize(dimensions.first, dimensions.second)

            // Setup Encoders
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (hasAudio) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
            }

            setVideoEncodingBitRate(bitrate)
            setVideoFrameRate(fps)
            
            prepare()
        }
    }

    private fun getTargetDimensions(resolutionSetting: String, screenWidth: Int, screenHeight: Int, orientationSetting: String): Pair<Int, Int> {
        if (resolutionSetting == "Original") {
            // Must be even
            val w = if (screenWidth % 2 == 0) screenWidth else screenWidth - 1
            val h = if (screenHeight % 2 == 0) screenHeight else screenHeight - 1
            return Pair(w, h)
        }

        val isLandscape = when (orientationSetting) {
            "Portrait" -> false
            "Landscape" -> true
            else -> screenWidth > screenHeight
        }
        val longSide = when (resolutionSetting) {
            "4K" -> 3840
            "1440p" -> 2560
            "1080p" -> 1920
            "720p" -> 1280
            "480p" -> 854
            else -> 1280
        }

        val targetW: Int
        val targetH: Int
        if (isLandscape) {
            targetW = longSide
            val aspectRatio = minOf(screenWidth, screenHeight).toFloat() / maxOf(screenWidth, screenHeight).toFloat()
            targetH = (longSide * aspectRatio).toInt()
        } else {
            targetH = longSide
            val aspectRatio = minOf(screenWidth, screenHeight).toFloat() / maxOf(screenWidth, screenHeight).toFloat()
            targetW = (longSide * aspectRatio).toInt()
        }

        val finalW = if (targetW % 2 == 0) targetW else targetW - 1
        val finalH = if (targetH % 2 == 0) targetH else targetH - 1
        return Pair(finalW, finalH)
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && !isPaused) {
            try {
                mediaRecorder?.pause()
                isPaused = true
                recordingDurationMs += System.currentTimeMillis() - startTimeMs
                updateNotification("Paused")
                floatingControlOverlay?.updateState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isRecording && isPaused) {
            try {
                mediaRecorder?.resume()
                isPaused = false
                startTimeMs = System.currentTimeMillis()
                updateNotification("Recording")
                floatingControlOverlay?.updateState()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false
        isPaused = false
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (_: Exception) {}
        
        // Remove overlays
        dismissFloatingControls()
        dismissBrushOverlay()

        if (!isPaused) {
            recordingDurationMs += System.currentTimeMillis() - startTimeMs
        }

        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaRecorder = null

        try {
            audioCaptureHelper?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioCaptureHelper = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null

        // Remux video and audio files if tempAudioFile exists
        val fileToSave = if (tempAudioFile != null && tempAudioFile!!.exists() && tempAudioFile!!.length() > 0) {
            val mergedFile = File(cacheDir, "merged_recording.mp4")
            if (mergedFile.exists()) {
                mergedFile.delete()
            }
            try {
                mergeVideoAndAudio(tempVideoFile!!, tempAudioFile!!, mergedFile)
                // Cleanup temp files
                try { tempAudioFile?.delete() } catch (e: Exception) {}
                try { tempVideoFile?.delete() } catch (e: Exception) {}
                mergedFile
            } catch (e: Exception) {
                e.printStackTrace()
                tempVideoFile // Fallback to video-only if remuxing fails
            }
        } else {
            tempVideoFile
        }

        // Save recorded file to MediaStore
        fileToSave?.let { tempFile ->
            if (tempFile.exists() && tempFile.length() > 0) {
                saveVideoToMediaStore(tempFile, targetResolution, recordingDurationMs)
                Toast.makeText(this, "Screen recording saved successfully!", Toast.LENGTH_SHORT).show()
            }
        }
        
        lifecycleScope.launch {
            val showFloating = settingsManager.showFloatingFlow.first()
            val mode = settingsManager.floatingShowModeFlow.first()
            if (showFloating && mode.startsWith("All the time")) {
                val notification = createNotification("Floating controls active")
                startForeground(NOTIFICATION_ID, notification)
                showFloatingControls()
            } else {
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private fun mergeVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        val extractorVideo = MediaExtractor()
        extractorVideo.setDataSource(videoFile.absolutePath)
        
        val extractorAudio = MediaExtractor()
        extractorAudio.setDataSource(audioFile.absolutePath)
        
        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        
        // Select video track
        var videoTrackIndex = -1
        for (i in 0 until extractorVideo.trackCount) {
            val format = extractorVideo.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                extractorVideo.selectTrack(i)
                videoTrackIndex = muxer.addTrack(format)
                break
            }
        }
        
        // Select audio track
        var audioTrackIndex = -1
        for (i in 0 until extractorAudio.trackCount) {
            val format = extractorAudio.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                extractorAudio.selectTrack(i)
                audioTrackIndex = muxer.addTrack(format)
                break
            }
        }
        
        muxer.start()
        
        val bufferSize = 1024 * 1024
        val buffer = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()
        
        // Copy video track
        if (videoTrackIndex != -1) {
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractorVideo.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractorVideo.sampleTime
                bufferInfo.flags = extractorVideo.sampleFlags
                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo)
                extractorVideo.advance()
            }
        }
        
        // Copy audio track
        if (audioTrackIndex != -1) {
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractorAudio.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) {
                    break
                }
                bufferInfo.presentationTimeUs = extractorAudio.sampleTime
                bufferInfo.flags = extractorAudio.sampleFlags
                muxer.writeSampleData(audioTrackIndex, buffer, bufferInfo)
                extractorAudio.advance()
            }
        }
        
        try {
            muxer.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            muxer.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        extractorVideo.release()
        extractorAudio.release()
    }

    private fun saveVideoToMediaStore(tempFile: File, resolution: String, durationMs: Long) {
        val fileName = "ScreenX_${System.currentTimeMillis()}.mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/ScreenX")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val resolver = contentResolver
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
                    put(MediaStore.Video.Media.RESOLUTION, resolution)
                    put(MediaStore.Video.Media.SIZE, tempFile.length())
                }
                resolver.update(videoUri, updateValues, null, null)
                sendBroadcast(Intent("com.gxdevs.screenx.action.RECORDING_SAVED"))
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
    }

    private fun takeScreenshot() {
        val projection = mediaProjection ?: return
        val vDisplay = virtualDisplay ?: return

        // Hide overlays before capture
        val wasFloating = floatingControlOverlay != null
        floatingControlOverlay?.hideView()

        Handler(Looper.getMainLooper()).postDelayed({
            val point = android.graphics.Point()
            vDisplay.display.getRealSize(point)
            val width = point.x
            val height = point.y

            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val originalSurface = mediaRecorder?.surface

            imageReader.setOnImageAvailableListener({ reader ->
                var image: Image? = null
                try {
                    image = reader.acquireLatestImage()
                    if (image != null) {
                        val planes     = image.planes
                        val buffer     = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride  = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        saveScreenshotToMediaStore(finalBitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    image?.close()
                    imageReader.close()
                    // Restore original surface
                    if (originalSurface != null && originalSurface.isValid) {
                        try {
                            vDisplay.surface = originalSurface
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    if (wasFloating) floatingControlOverlay?.showView()
                }
            }, Handler(Looper.getMainLooper()))

            // Swap surface to screenshot reader
            try {
                vDisplay.surface = imageReader.surface
            } catch (e: Exception) {
                e.printStackTrace()
                imageReader.close()
                if (wasFloating) floatingControlOverlay?.showView()
            }
        }, 200)
    }


    private fun saveScreenshotToMediaStore(bitmap: Bitmap) {
        val fileName = "ScreenX_Screenshot_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ScreenX")
            }
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
        }

        val resolver = contentResolver
        val collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val imageUri = resolver.insert(collectionUri, contentValues)

        if (imageUri != null) {
            try {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showFloatingControls() {
        if (floatingControlOverlay == null) {
            floatingControlOverlay = FloatingControlOverlay(this)
            floatingControlOverlay?.show(
                onStop = { stopRecording() },
                onPauseToggle = {
                    if (isPaused) resumeRecording() else pauseRecording()
                },
                onBrushToggle = {
                    toggleBrushOverlay()
                },
                onScreenshot = { takeScreenshot() }
            )
        }
    }

    private fun dismissFloatingControls() {
        floatingControlOverlay?.dismiss()
        floatingControlOverlay = null
    }

    private fun toggleBrushOverlay() {
        if (brushDrawingOverlay == null) {
            brushDrawingOverlay = BrushDrawingOverlay(this)
            brushDrawingOverlay?.show {
                dismissBrushOverlay()
            }
            floatingControlOverlay?.bringToFront()
        } else {
            dismissBrushOverlay()
        }
    }

    private fun dismissBrushOverlay() {
        brushDrawingOverlay?.dismiss()
        brushDrawingOverlay = null
    }

    private fun exitService() {
        stopRecording()
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        try {
            sensorManager?.unregisterListener(sensorListener)
        } catch (_: Exception) {}
        dismissFloatingControls()
        dismissBrushOverlay()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
