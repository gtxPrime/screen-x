package com.gxdevs.screenx.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Unified Audio Capture Helper — handles Mic, Internal Audio, and combined Mic+Internal.
 *
 * Architecture (all 3 modes use AudioRecord + MediaCodec AAC 192 kbps):
 *  - "Mic":       Hardware mic → HPF + soft-knee AGC limiter → AAC → M4A
 *  - "System":    AudioPlaybackCapture with dedicated reader thread + real-time silence fallback
 *                 (guarantees instant muxer start and seamless recording without stalls or gaps)
 *  - "MicSystem": Dedicated reader thread for system audio + hardware mic master clock +
 *                 VAD sidechain ducking + soft-knee mix → AAC → M4A
 *
 * Robustness guarantees:
 *  - Dynamic sample rate selection (prefers 48 kHz native HAL rate on modern devices, 44.1 kHz fallback).
 *  - Dedicated background thread for system audio capture ensures AudioPlaybackCapture never stalls mic.
 *  - Real-time silence injection in System-only mode guarantees immediate muxer start even if device is silent.
 *  - Pure sample-counted PTS (0 jitter, exact mathematically linear AAC timestamps, perfect A/V sync).
 *  - Proper teardown sequence releases AudioRecord before MediaProjection to avoid OEM audioserver leaks.
 */
class AudioCaptureHelper(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    private val audioSource: String, // "System" | "Mic" | "MicSystem"
    private val outputFile: File,
    private val voicePriorityEnabled: Boolean = true,
    private val micGainMultiplier: Float = 3.5f,
    private val internalAudioVolumeRatio: Float = 1.0f,
    private val vocalClarityEnabled: Boolean = true
) {
    companion object {
        private const val TAG = "AudioCaptureHelper"
        private const val CHUNK_BYTES = 4096       // 1024 stereo PCM-16 samples = 1 AAC frame
        private const val JOIN_TIMEOUT_MS = 5000L  // Give EOS flush time to complete
        private const val MAX_SYSTEM_QUEUE_SIZE = 15 // ~320 ms max buffer to prevent latency drift
    }

    // Primary AudioRecord: internal audio (System/MicSystem) OR mic (Mic-only)
    private var audioRecord: AudioRecord? = null
    // Secondary AudioRecord: mic (MicSystem only)
    private var audioRecordSecondary: AudioRecord? = null
    // True when the mic record (primary or secondary) is MONO and needs stereo upmix
    private var isMicMono = false
    private val audioRecordLock = Any()

    // Hardware audio effects — only attached in Mic-only mode.
    // Attaching them while AudioPlaybackCapture is active causes AudioFlinger EINVAL (-22)
    // on Samsung/OneUI and many other OEM HALs.
    private var noiseSuppressorPrimary: NoiseSuppressor? = null
    private var echoCancelerPrimary: AcousticEchoCanceler? = null
    private var autoGainPrimary: AutomaticGainControl? = null

    // Codec / container
    private var audioEncoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val muxerLock = Any()

    // Recording state
    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isMicMuted = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var systemCaptureThread: Thread? = null
    private val systemAudioQueue = LinkedBlockingQueue<ByteArray>(20)

    // Audio format configuration
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bitRate = 192000  // 192 kbps — studio quality AAC

    // Resolve native sample rate: modern Android devices use 48000 Hz natively.
    private val sampleRate: Int = run {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val prop = am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
        val preferred = if (prop == 48000 || prop == 44100) prop else 48000
        val testBuf = AudioRecord.getMinBufferSize(preferred, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        if (testBuf > 0) preferred else 44100
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  start()
    // ─────────────────────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun start() {
        if (!isRecording.compareAndSet(false, true)) return
        systemAudioQueue.clear()

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(CHUNK_BYTES * 4)

        when (audioSource) {

            // ── MIC-ONLY ──────────────────────────────────────────────────────────
            "Mic" -> {
                val stereoMic = tryCreateStereoMic(bufferSize)
                if (stereoMic != null) {
                    audioRecord = stereoMic
                    isMicMono = false
                    setupMicAudioEffects(stereoMic.audioSessionId)
                } else {
                    val monoMic = createMicFallbackRecord(bufferSize)
                    if (monoMic == null) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(com.gxdevs.screenx.R.string.toast_mic_init_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        Log.e(TAG, "Mic-only: all AudioRecord sources failed. Aborting.")
                        isRecording.set(false)
                        return
                    }
                    audioRecord = monoMic
                    setupMicAudioEffects(monoMic.audioSessionId)
                }
                audioRecordSecondary = null
                isMicMuted.set(false)
            }

            // ── INTERNAL / DUAL ───────────────────────────────────────────────────
            "System", "MicSystem" -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || mediaProjection == null) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(
                            context,
                            context.getString(com.gxdevs.screenx.R.string.toast_internal_audio_android10),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    Log.e(TAG, "AudioPlaybackCapture not available (API ${Build.VERSION.SDK_INT} / projection=$mediaProjection).")
                    isRecording.set(false)
                    return
                }

                val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                var primaryRec: AudioRecord? = null
                try {
                    primaryRec = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(captureConfig)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(audioFormat)
                                .setSampleRate(sampleRate)
                                .setChannelMask(channelConfig)
                                .build()
                        )
                        .setBufferSizeInBytes(bufferSize)
                        .build()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioPlaybackCapture build at $sampleRate Hz failed", e)
                }

                if (primaryRec == null || primaryRec.state != AudioRecord.STATE_INITIALIZED) {
                    val altRate = if (sampleRate == 48000) 44100 else 48000
                    val altBufferSize = AudioRecord.getMinBufferSize(altRate, channelConfig, audioFormat).coerceAtLeast(CHUNK_BYTES * 4)
                    try {
                        primaryRec?.release()
                        primaryRec = AudioRecord.Builder()
                            .setAudioPlaybackCaptureConfig(captureConfig)
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(audioFormat)
                                    .setSampleRate(altRate)
                                    .setChannelMask(channelConfig)
                                    .build()
                            )
                            .setBufferSizeInBytes(altBufferSize)
                            .build()
                        if (primaryRec.state == AudioRecord.STATE_INITIALIZED) {
                            Log.d(TAG, "AudioPlaybackCapture initialized with fallback rate $altRate Hz")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "AudioPlaybackCapture fallback build at $altRate Hz failed", e)
                    }
                }
                audioRecord = primaryRec

                if (audioSource == "MicSystem") {
                    val secondary = tryCreateStereoMic(bufferSize)?.also { isMicMono = false }
                        ?: createMicFallbackRecord(bufferSize)
                    if (secondary == null) {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(
                                context,
                                context.getString(com.gxdevs.screenx.R.string.toast_mic_unavailable_internal_only),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        Log.w(TAG, "MicSystem: mic sources failed. Continuing with internal audio only.")
                    }
                    audioRecordSecondary = secondary
                    isMicMuted.set(secondary == null)
                } else {
                    audioRecordSecondary = null
                    isMicMuted.set(true)
                }
            }

            else -> {
                Log.e(TAG, "Unknown audioSource: $audioSource")
                isRecording.set(false)
                return
            }
        }

        // ── Codec + muxer setup ──────────────────────────────────────────────────
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 2).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        }
        try {
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            Log.e(TAG, "Audio encoder/muxer creation failed", e)
            isRecording.set(false)
            return
        }

        // ── Start dedicated System Audio Capture Thread if capturing internal audio ──
        if (audioSource == "System" || audioSource == "MicSystem") {
            startSystemCaptureThread()
        }

        // ── Start Main Audio Processing & Encoding Thread ─────────────────────────
        audioThread = thread(name = "AudioEncoderThread") {
            try {
                val primary = audioRecord
                if (primary != null && primary.state != AudioRecord.STATE_INITIALIZED) {
                    throw IOException("Primary AudioRecord not initialized (state=${primary.state})")
                }
                synchronized(audioRecordLock) {
                    val sec = audioRecordSecondary
                    if (sec != null && sec.state != AudioRecord.STATE_INITIALIZED) {
                        Log.w(TAG, "Secondary AudioRecord failed state check. Continuing single audio.")
                        try { sec.release() } catch (_: Exception) {}
                        audioRecordSecondary = null
                    }
                }

                primary?.startRecording()
                synchronized(audioRecordLock) { audioRecordSecondary?.startRecording() }

                if (primary != null && primary.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw IOException("Primary AudioRecord failed to start (state=${primary.recordingState})")
                }
                drainAudio()
            } catch (e: Exception) {
                Log.e(TAG, "Fatal audio thread error", e)
            } finally {
                releaseResources()
            }
        }
    }

    /**
     * Dedicated background worker for AudioPlaybackCapture.
     * Reads full chunks in blocking mode and feeds them to [systemAudioQueue].
     * This decouples system audio from the mic thread and prevents any blocking.
     */
    /**
     * Dedicated background worker for AudioPlaybackCapture.
     * Reads full chunks via readFully and feeds them to [systemAudioQueue].
     * This decouples system audio from the mic thread and prevents any blocking or partial-frame gaps.
     */
    private fun startSystemCaptureThread() {
        systemCaptureThread = thread(name = "SystemAudioCaptureThread") {
            val buf = ByteArray(CHUNK_BYTES)
            try {
                while (isRecording.get()) {
                    if (isPaused.get()) {
                        try { Thread.sleep(20) } catch (_: InterruptedException) {}
                        continue
                    }
                    val rec = audioRecord
                    if (rec == null || rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        try { Thread.sleep(20) } catch (_: InterruptedException) {}
                        continue
                    }

                    val r = rec.readFully(buf, CHUNK_BYTES)
                    if (r == CHUNK_BYTES) {
                        val chunk = buf.copyOf()
                        while (systemAudioQueue.size >= MAX_SYSTEM_QUEUE_SIZE) {
                            systemAudioQueue.poll() // Drop oldest chunk to prevent latency build-up
                        }
                        systemAudioQueue.offer(chunk)
                    } else if (r < 0) {
                        Log.w(TAG, "systemCaptureThread read error: $r")
                        try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "systemCaptureThread error", e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  drainAudio() — unified capture + DSP + encode loop
    // ─────────────────────────────────────────────────────────────────────────────

    private fun drainAudio() {
        val encoder = audioEncoder ?: return

        // Linear volume scaling for internal audio
        val sysTaper = internalAudioVolumeRatio.coerceIn(0.01f, 1.0f)

        val pcmBuffer = ByteBuffer.allocateDirect(CHUNK_BYTES)
        val primaryBytes = ByteArray(CHUNK_BYTES)
        val secondaryBytes = ByteArray(CHUNK_BYTES)
        val monoBytes = ByteArray(CHUNK_BYTES / 2) // For mono→stereo upmix

        // Sample-counted PTS: guarantees completely jitter-free, linear timestamps
        var totalSamplesWritten = 0L

        // Cache headset state and update periodically
        var cachedHeadsetConnected = isHeadsetConnected()
        var headsetCheckCounter = 0

        // High-pass filter: 120 Hz cutoff
        val hpAlpha = (1.0f / (1.0f + 2.0f * PI.toFloat() * 120f / sampleRate.toFloat())).coerceIn(0.95f, 0.99f)
        var hpPrevXL = 0f; var hpPrevYL = 0f
        var hpPrevXR = 0f; var hpPrevYR = 0f
        var wasPaused = false

        // Sidechain VAD-ducking state (MicSystem only)
        var duckingFactor = 1.0f
        val targetDuckedGain = 0.20f    // Attenuate game audio to 20% when speech detected
        val speechHoldFramesMax = 20    // ~440 ms hold bridges inter-word gaps
        var speechHoldRemaining = 0

        val recBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            .coerceAtLeast(CHUNK_BYTES * 4)

        try {
            while (isRecording.get()) {

                // ── Pause handling (resets HPF history & queue to prevent resume click/pop/stutter) ─
                if (isPaused.get()) {
                    wasPaused = true
                    try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    continue
                } else if (wasPaused) {
                    wasPaused = false
                    systemAudioQueue.clear()
                    hpPrevXL = 0f; hpPrevYL = 0f
                    hpPrevXR = 0f; hpPrevYR = 0f
                }

                var bytesReady = 0
                val isDual = audioSource == "MicSystem" && audioRecordSecondary != null

                // ════════════════════════════════════════════════════════════════
                //  DUAL MODE — MicSystem (internal audio + mic)
                // ════════════════════════════════════════════════════════════════
                if (isDual) {
                    val sec = audioRecordSecondary
                    primaryBytes.fill(0)
                    secondaryBytes.fill(0)

                    if (sec != null) {
                        // Mic is the master pacing clock (blocking read, exactly 1 AAC frame duration)
                        if (isMicMono) {
                            val r = sec.readFully(monoBytes, CHUNK_BYTES / 2)
                            when {
                                r > 0 -> {
                                    if (r < CHUNK_BYTES / 2) monoBytes.fill(0, r, CHUNK_BYTES / 2)
                                    // Upmix mono → stereo (duplicate L to R)
                                    for (i in 0 until r / 2) {
                                        secondaryBytes[i * 4]     = monoBytes[i * 2]
                                        secondaryBytes[i * 4 + 1] = monoBytes[i * 2 + 1]
                                        secondaryBytes[i * 4 + 2] = monoBytes[i * 2]
                                        secondaryBytes[i * 4 + 3] = monoBytes[i * 2 + 1]
                                    }
                                }
                                r < 0 -> {
                                    Log.w(TAG, "Mic (mono) read error=$r — recovering…")
                                    recoverSecondaryAudioRecord(recBufSize)
                                    secondaryBytes.fill(0)
                                }
                                else -> secondaryBytes.fill(0)
                            }
                        } else {
                            val r = sec.readFully(secondaryBytes, CHUNK_BYTES)
                            when {
                                r > 0 -> if (r < CHUNK_BYTES) secondaryBytes.fill(0, r, CHUNK_BYTES)
                                r < 0 -> {
                                    Log.w(TAG, "Mic (stereo) read error=$r — recovering…")
                                    recoverSecondaryAudioRecord(recBufSize)
                                    secondaryBytes.fill(0)
                                }
                                else -> secondaryBytes.fill(0)
                            }
                        }

                        if (isMicMuted.get()) {
                            secondaryBytes.fill(0)
                            hpPrevXL = 0f; hpPrevYL = 0f
                            hpPrevXR = 0f; hpPrevYR = 0f
                        } else if (vocalClarityEnabled) {
                            // High-pass filter on mic channel
                            for (i in 0 until CHUNK_BYTES - 3 step 4) {
                                val lRaw = secondaryBytes.readShortLE(i).toFloat()
                                val yL = hpAlpha * (hpPrevYL + lRaw - hpPrevXL)
                                hpPrevXL = lRaw; hpPrevYL = yL
                                secondaryBytes.writeShortLE(i, yL.toInt().coerceIn(-32768, 32767))

                                val rRaw = secondaryBytes.readShortLE(i + 2).toFloat()
                                val yR = hpAlpha * (hpPrevYR + rRaw - hpPrevXR)
                                hpPrevXR = rRaw; hpPrevYR = yR
                                secondaryBytes.writeShortLE(i + 2, yR.toInt().coerceIn(-32768, 32767))
                            }
                        }
                    } else {
                        try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    }

                    // Retrieve system audio chunk from dedicated capture queue
                    val sysChunk = systemAudioQueue.poll()
                    if (sysChunk != null) {
                        val len = minOf(CHUNK_BYTES, sysChunk.size)
                        System.arraycopy(sysChunk, 0, primaryBytes, 0, len)
                        if (len < CHUNK_BYTES) primaryBytes.fill(0, len, CHUNK_BYTES)
                    } else {
                        primaryBytes.fill(0) // Game currently silent or paused
                    }

                    // Compute RMS for VAD ducking
                    val nSamples = CHUNK_BYTES / 2
                    var sysSumSq = 0.0; var micSumSq = 0.0; var maxMicSample = 0
                    for (i in 0 until CHUNK_BYTES - 1 step 2) {
                        val sv = primaryBytes.readShortLE(i).toShort()
                        sysSumSq += sv.toLong() * sv.toLong()
                        val mv = secondaryBytes.readShortLE(i).toShort()
                        micSumSq += mv.toLong() * mv.toLong()
                        val absM = abs(mv.toInt())
                        if (absM > maxMicSample) maxMicSample = absM
                    }
                    val sysRms = if (nSamples > 0) sqrt(sysSumSq / nSamples).toFloat() else 0f
                    val micRms = if (nSamples > 0) sqrt(micSumSq / nSamples).toFloat() else 0f

                    // Adaptive VAD ducking with smooth per-sample linear ramp
                    val prevDuck = duckingFactor
                    if (!isMicMuted.get() && voicePriorityEnabled) {
                        if (++headsetCheckCounter >= 45) {
                            headsetCheckCounter = 0
                            cachedHeadsetConnected = isHeadsetConnected()
                        }
                        val threshold = if (!cachedHeadsetConnected) maxOf(140f, sysRms * 0.40f) else 90f
                        if (micRms >= threshold) speechHoldRemaining = speechHoldFramesMax
                        else if (speechHoldRemaining > 0) speechHoldRemaining--
                        val targetDuck = if (speechHoldRemaining > 0) targetDuckedGain else 1.0f
                        val interpRate = if (targetDuck < duckingFactor) 0.40f else 0.12f
                        duckingFactor += (targetDuck - duckingFactor) * interpRate
                    } else {
                        duckingFactor = 1.0f
                    }

                    // Peak-limiter AGC for mic
                    val targetPeak = 18000f
                    val effectiveMicGain = when {
                        isMicMuted.get() -> 0f
                        maxMicSample > 0 && maxMicSample * micGainMultiplier > targetPeak ->
                            (targetPeak / maxMicSample.toFloat()).coerceIn(0.7f, micGainMultiplier)
                        else -> micGainMultiplier
                    }

                    // Mix both channels with analog-smooth ducking ramp and tanh soft-knee saturation
                    val duckStep = (duckingFactor - prevDuck) / nSamples.toFloat()
                    for (i in 0 until CHUNK_BYTES - 1 step 2) {
                        val sampleIdx = i / 2
                        val smoothDuck = prevDuck + duckStep * sampleIdx
                        val effectiveSysGain = sysTaper * smoothDuck
                        val s = primaryBytes.readShortLE(i).toFloat() * effectiveSysGain
                        val m = secondaryBytes.readShortLE(i).toFloat() * effectiveMicGain
                        val mixed = softKneeClamp((s + m).toDouble())
                        primaryBytes.writeShortLE(i, mixed)
                    }

                    pcmBuffer.clear()
                    pcmBuffer.put(primaryBytes, 0, CHUNK_BYTES)
                    pcmBuffer.position(0)
                    bytesReady = CHUNK_BYTES

                } else if (audioSource == "System" || (audioSource == "MicSystem" && audioRecordSecondary == null)) {
                    // ════════════════════════════════════════════════════════════
                    //  SYSTEM ONLY — Polls queue with timeout; injects silence if idle
                    //  (Also handles MicSystem fallback if secondary mic failed to initialize)
                    // ════════════════════════════════════════════════════════════
                    val pollTimeoutMs = (CHUNK_BYTES * 1000L / (sampleRate * 4L)).coerceAtLeast(18L)
                    val sysChunk = systemAudioQueue.poll(pollTimeoutMs, TimeUnit.MILLISECONDS)

                    if (sysChunk != null) {
                        val len = minOf(CHUNK_BYTES, sysChunk.size)
                        System.arraycopy(sysChunk, 0, primaryBytes, 0, len)
                        if (len < CHUNK_BYTES) primaryBytes.fill(0, len, CHUNK_BYTES)

                        if (sysTaper < 1.0f) {
                            for (i in 0 until CHUNK_BYTES - 1 step 2) {
                                val sv = (primaryBytes.readShortLE(i).toFloat() * sysTaper).toInt().coerceIn(-32768, 32767)
                                primaryBytes.writeShortLE(i, sv)
                            }
                        }
                    } else {
                        // Device is silent: inject clean PCM silence at exact timing pace.
                        // This guarantees the AAC encoder immediately receives frames,
                        // output format changes, and muxer starts without delays or stalls!
                        primaryBytes.fill(0)
                    }

                    pcmBuffer.clear()
                    pcmBuffer.put(primaryBytes, 0, CHUNK_BYTES)
                    pcmBuffer.position(0)
                    bytesReady = CHUNK_BYTES

                } else {
                    // ════════════════════════════════════════════════════════════
                    //  MIC ONLY — Direct read from hardware mic
                    // ════════════════════════════════════════════════════════════
                    val rec = audioRecord ?: break
                    pcmBuffer.clear()

                    val r: Int
                    if (isMicMono) {
                        val halfChunk = CHUNK_BYTES / 2
                        val rr = rec.readFully(monoBytes, halfChunk)
                        if (rr <= 0) continue
                        if (rr < halfChunk) monoBytes.fill(0, rr, halfChunk)
                        for (i in 0 until rr / 2) {
                            primaryBytes[i * 4]     = monoBytes[i * 2]
                            primaryBytes[i * 4 + 1] = monoBytes[i * 2 + 1]
                            primaryBytes[i * 4 + 2] = monoBytes[i * 2]
                            primaryBytes[i * 4 + 3] = monoBytes[i * 2 + 1]
                        }
                        r = rr * 2
                    } else {
                        r = rec.readFully(primaryBytes, CHUNK_BYTES)
                        if (r <= 0) continue
                    }

                    if (isMicMuted.get()) {
                        primaryBytes.fill(0, 0, r)
                        pcmBuffer.put(primaryBytes, 0, r)
                        pcmBuffer.position(0)
                        bytesReady = r
                    } else {
                        // Vocal clarity HPF
                        if (vocalClarityEnabled) {
                            for (i in 0 until r - 3 step 4) {
                                val lRaw = primaryBytes.readShortLE(i).toFloat()
                                val yL = hpAlpha * (hpPrevYL + lRaw - hpPrevXL)
                                hpPrevXL = lRaw; hpPrevYL = yL
                                primaryBytes.writeShortLE(i, yL.toInt().coerceIn(-32768, 32767))

                                val rRaw = primaryBytes.readShortLE(i + 2).toFloat()
                                val yR = hpAlpha * (hpPrevYR + rRaw - hpPrevXR)
                                hpPrevXR = rRaw; hpPrevYR = yR
                                primaryBytes.writeShortLE(i + 2, yR.toInt().coerceIn(-32768, 32767))
                            }
                        }

                        // Soft-knee gain
                        if (micGainMultiplier != 1.0f) {
                            var peakSample = 0
                            for (i in 0 until r - 1 step 2) {
                                val a = abs(primaryBytes.readShortLE(i).toInt())
                                if (a > peakSample) peakSample = a
                            }
                            val effectiveGain = if (peakSample > 0 && peakSample * micGainMultiplier > 18000f)
                                (18000f / peakSample.toFloat()).coerceIn(0.7f, micGainMultiplier)
                            else micGainMultiplier

                            for (i in 0 until r - 1 step 2) {
                                val amplified = primaryBytes.readShortLE(i).toFloat() * effectiveGain
                                primaryBytes.writeShortLE(i, softKneeClamp(amplified.toDouble()))
                            }
                        }

                        pcmBuffer.put(primaryBytes, 0, r)
                        pcmBuffer.position(0)
                        bytesReady = r
                    }
                }

                // ── Feed PCM to encoder ──────────────────────────────────────────
                if (bytesReady > 0) {
                    var inputIndex = encoder.dequeueInputBuffer(10000L)
                    if (inputIndex < 0) {
                        // Queue might be backed up with output frames; drain output and retry once
                        drainEncoderOutput(isEosFlush = false)
                        inputIndex = encoder.dequeueInputBuffer(10000L)
                    }
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val toCopy = minOf(bytesReady, inputBuffer.remaining())
                            pcmBuffer.limit(toCopy)
                            inputBuffer.put(pcmBuffer)

                            // Pure sample-counted PTS: mathematically linear, zero jitter
                            val ptsUs = (totalSamplesWritten * 1_000_000L) / sampleRate
                            totalSamplesWritten += (toCopy / 4)
                            encoder.queueInputBuffer(inputIndex, 0, toCopy, ptsUs, 0)
                        }
                    }
                }

                // Drain output buffers from encoder into muxer
                drainEncoderOutput(isEosFlush = false)
            }

            // Signal EOS and flush remaining frames to muxer (retry up to 10 times if buffer is busy)
            for (attempt in 0 until 10) {
                val eosIdx = encoder.dequeueInputBuffer(10000L)
                if (eosIdx >= 0) {
                    val eosPts = (totalSamplesWritten * 1_000_000L) / sampleRate
                    encoder.queueInputBuffer(eosIdx, 0, 0, eosPts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                drainEncoderOutput(isEosFlush = false)
            }
            drainEncoderOutput(isEosFlush = true)

        } catch (e: Exception) {
            Log.e(TAG, "drainAudio error", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  DSP helpers
    // ─────────────────────────────────────────────────────────────────────────────

    /** Tanh soft-knee saturation: linear within ±24000, smooth symmetric saturation toward ±32767. */
    private fun softKneeClamp(value: Double): Int {
        val knee = 24000.0
        val maxAmp = 32767.0
        val headroom = maxAmp - knee
        val absVal = abs(value)
        val clampedAbs = if (absVal > knee) {
            knee + headroom * tanh((absVal - knee) / headroom)
        } else {
            absVal
        }
        val result = if (value < 0.0) -clampedAbs else clampedAbs
        return result.toInt().coerceIn(-32768, 32767)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  AudioRecord creation
    // ─────────────────────────────────────────────────────────────────────────────

    /** Try creating a stereo MIC AudioRecord. Returns null on any failure. */
    @SuppressLint("MissingPermission")
    private fun tryCreateStereoMic(bufferSize: Int): AudioRecord? = try {
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
        if (rec.state == AudioRecord.STATE_INITIALIZED) {
            Log.d(TAG, "Stereo MIC AudioRecord initialized at $sampleRate Hz.")
            rec
        } else {
            rec.release()
            null
        }
    } catch (e: Exception) { Log.w(TAG, "Stereo MIC failed: ${e.message}"); null }

    /**
     * Fallback chain for mic: VOICE_COMMUNICATION mono → MIC mono → CAMCORDER mono.
     * Sets [isMicMono] = true so the drain loop knows to upmix mono→stereo.
     */
    @SuppressLint("MissingPermission")
    private fun createMicFallbackRecord(bufferSize: Int): AudioRecord? {
        val monoBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat)
            .coerceAtLeast(CHUNK_BYTES * 2)

        val sources = listOf(
            MediaRecorder.AudioSource.MIC                 to "MIC",
            MediaRecorder.AudioSource.CAMCORDER           to "CAMCORDER",
            MediaRecorder.AudioSource.VOICE_RECOGNITION   to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.VOICE_COMMUNICATION to "VOICE_COMMUNICATION"
        )
        for ((src, name) in sources) {
            try {
                val rec = AudioRecord(src, sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat, monoBuf)
                if (rec.state == AudioRecord.STATE_INITIALIZED) {
                    isMicMono = true
                    Log.d(TAG, "Mic fallback: $name mono ✓ ($sampleRate Hz)")
                    return rec
                }
                rec.release()
            } catch (e: Exception) {
                Log.w(TAG, "Mic fallback $name failed: ${e.message}")
            }
        }
        Log.e(TAG, "All mic fallback sources exhausted.")
        return null
    }

    /**
     * Attach hardware NS, AEC, and AGC to a mic audio session.
     * Only safe for Mic-only mode — do NOT call while AudioPlaybackCapture is active.
     */
    private fun setupMicAudioEffects(sessionId: Int) {
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressorPrimary = NoiseSuppressor.create(sessionId)?.also { it.enabled = true }
                Log.d(TAG, "NoiseSuppressor attached (session $sessionId)")
            }
        } catch (e: Exception) { Log.w(TAG, "NoiseSuppressor failed", e) }

        if (!isHeadsetConnected()) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    echoCancelerPrimary = AcousticEchoCanceler.create(sessionId)?.also { it.enabled = true }
                    Log.d(TAG, "AcousticEchoCanceler attached (session $sessionId)")
                }
            } catch (e: Exception) { Log.w(TAG, "AcousticEchoCanceler failed", e) }
        } else {
            Log.d(TAG, "Headset connected — skipping AEC to preserve vocal dynamics.")
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                autoGainPrimary = AutomaticGainControl.create(sessionId)?.also { it.enabled = true }
                Log.d(TAG, "AutomaticGainControl attached (session $sessionId)")
            }
        } catch (e: Exception) { Log.w(TAG, "AutomaticGainControl failed", e) }
    }

    private fun isHeadsetConnected(): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { d ->
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET   ||
                d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP  ||
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO   ||
                d.type == AudioDeviceInfo.TYPE_USB_HEADSET     ||
                d.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }
        } else {
            @Suppress("DEPRECATION")
            am.isWiredHeadsetOn || am.isBluetoothA2dpOn || am.isBluetoothScoOn
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Secondary mic recovery (MicSystem only)
    // ─────────────────────────────────────────────────────────────────────────────

    private fun recoverSecondaryAudioRecord(bufferSize: Int) {
        synchronized(audioRecordLock) {
            try { audioRecordSecondary?.stop() }   catch (_: Exception) {}
            try { audioRecordSecondary?.release() } catch (_: Exception) {}
            audioRecordSecondary = null

            try {
                Thread.sleep(40)
                val newRec = tryCreateStereoMic(bufferSize)?.also { isMicMono = false }
                    ?: createMicFallbackRecord(bufferSize)
                if (newRec != null && newRec.state == AudioRecord.STATE_INITIALIZED) {
                    newRec.startRecording()
                    audioRecordSecondary = newRec
                    Log.d(TAG, "Secondary AudioRecord recovered and restarted.")
                } else {
                    Log.e(TAG, "Secondary AudioRecord recovery failed — continuing internal-only.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during secondary recovery", e)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────────────────────────────────────────

    fun setMicMuted(muted: Boolean) { isMicMuted.set(muted) }
    fun isMicMuted(): Boolean = isMicMuted.get()
    fun hasMic(): Boolean = (audioSource == "Mic" || (audioSource == "MicSystem" && audioRecordSecondary != null))

    fun pause()  { isPaused.set(true) }
    fun resume() { isPaused.set(false) }

    fun stop() {
        if (!isRecording.compareAndSet(true, false)) return

        // Proactively stop AudioRecord instances so any blocking read() returns immediately
        try { audioRecord?.stop() } catch (_: Exception) {}
        synchronized(audioRecordLock) {
            try { audioRecordSecondary?.stop() } catch (_: Exception) {}
        }

        try {
            systemCaptureThread?.join(500L)
        } catch (_: InterruptedException) {}
        systemCaptureThread = null

        try {
            audioThread?.join(JOIN_TIMEOUT_MS)
        } catch (_: InterruptedException) {}
        audioThread = null

        releaseResources()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Encoder output drain + muxer write
    // ─────────────────────────────────────────────────────────────────────────────

    private fun drainEncoderOutput(isEosFlush: Boolean = false) {
        val encoder = audioEncoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        val timeoutUs = if (isEosFlush) 10000L else 2000L
        var eosReceived = false
        var attempts = 0
        val maxAttempts = if (isEosFlush) 25 else 1

        while (attempts < maxAttempts && !eosReceived) {
            var hadData = false
            var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            while (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                hadData = true
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    synchronized(muxerLock) {
                        if (muxerStarted) return@synchronized
                        try {
                            muxer?.let { m ->
                                audioTrackIndex = m.addTrack(encoder.outputFormat)
                                m.start()
                                muxerStarted = true
                                Log.d(TAG, "Audio muxer started successfully.")
                            }
                        } catch (e: Exception) { Log.e(TAG, "Muxer start failed", e) }
                    }
                } else {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    val outBuf = encoder.getOutputBuffer(outputIndex)
                    if (muxerStarted && bufferInfo.size > 0 && audioTrackIndex != -1 && outBuf != null) {
                        synchronized(muxerLock) {
                            try {
                                outBuf.position(bufferInfo.offset)
                                outBuf.limit(bufferInfo.offset + bufferInfo.size)
                                muxer?.writeSampleData(audioTrackIndex, outBuf, bufferInfo)
                            }
                            catch (e: Exception) { Log.e(TAG, "writeSampleData failed", e) }
                        }
                    }
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        eosReceived = true
                    }
                    encoder.releaseOutputBuffer(outputIndex, false)
                }
                if (eosReceived) break
                outputIndex = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
            }
            if (isEosFlush && !eosReceived && !hadData) {
                attempts++
            } else if (!isEosFlush) {
                break
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    //  Resource cleanup
    // ─────────────────────────────────────────────────────────────────────────────

    private fun releaseResources() {
        systemAudioQueue.clear()

        try { noiseSuppressorPrimary?.release(); noiseSuppressorPrimary = null } catch (_: Exception) {}
        try { echoCancelerPrimary?.release();   echoCancelerPrimary = null }   catch (_: Exception) {}
        try { autoGainPrimary?.release();       autoGainPrimary = null }       catch (_: Exception) {}

        try { audioEncoder?.stop();   audioEncoder?.release() } catch (_: Exception) {}
        audioEncoder = null

        try { audioRecord?.stop();          audioRecord?.release() }          catch (_: Exception) {}
        try { audioRecordSecondary?.stop(); audioRecordSecondary?.release() } catch (_: Exception) {}
        audioRecord = null
        audioRecordSecondary = null

        synchronized(muxerLock) {
            if (muxer != null) {
                try { if (muxerStarted) muxer?.stop() } catch (_: Exception) {}
                try { muxer?.release() }                catch (_: Exception) {}
                muxer = null
                muxerStarted = false
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ByteArray PCM-16 little-endian helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun ByteArray.readShortLE(offset: Int): Short =
    ((this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)).toShort()

private fun ByteArray.writeShortLE(offset: Int, value: Int) {
    this[offset]     = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

/**
 * Reads until [targetBytes] are accumulated or an error occurs.
 * Prevents partial-buffer fragmentation and dropped-sample artifacts.
 */
private fun AudioRecord.readFully(buffer: ByteArray, targetBytes: Int): Int {
    var totalRead = 0
    while (totalRead < targetBytes) {
        val r = this.read(buffer, totalRead, targetBytes - totalRead)
        if (r > 0) {
            totalRead += r
        } else {
            return if (totalRead > 0) totalRead else r
        }
    }
    return totalRead
}
