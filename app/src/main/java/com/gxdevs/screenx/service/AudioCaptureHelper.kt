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
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Thread-safe, non-blocking PCM byte ring buffer for decoupled audio producer-consumer streaming.
 * Provides clock-drift compensation, jitter absorption, and zero-dropout silence padding.
 */
class AudioRingBuffer(val capacity: Int) {
    private val buffer = ByteArray(capacity)
    private var writeHead = 0
    private var readHead = 0
    private var availableBytes = 0
    private val lock = Any()

    fun write(data: ByteArray, offset: Int, length: Int): Int {
        if (length <= 0) return 0
        synchronized(lock) {
            var actualOffset = offset
            var actualLength = length

            // If incoming data exceeds capacity, keep only the latest bytes
            if (actualLength > capacity) {
                actualOffset += (actualLength - capacity)
                actualLength = capacity
            }

            // Discard oldest unread data if capacity would overflow
            val freeSpace = capacity - availableBytes
            if (actualLength > freeSpace) {
                val dropBytes = actualLength - freeSpace
                readHead = (readHead + dropBytes) % capacity
                availableBytes -= dropBytes
            }

            val firstChunk = minOf(actualLength, capacity - writeHead)
            System.arraycopy(data, actualOffset, buffer, writeHead, firstChunk)
            val secondChunk = actualLength - firstChunk
            if (secondChunk > 0) {
                System.arraycopy(data, actualOffset + firstChunk, buffer, 0, secondChunk)
            }
            writeHead = (writeHead + actualLength) % capacity
            availableBytes += actualLength
            return actualLength
        }
    }

    /**
     * Reads up to [length] bytes into [dest] at [destOffset].
     * If fewer bytes are available, reads what is available and fills the remainder with zeros (silence padding)
     * so that the mixer thread never starves or halts.
     */
    fun readWithSilencePadding(dest: ByteArray, destOffset: Int, length: Int): Int {
        if (length <= 0) return 0
        synchronized(lock) {
            val toRead = minOf(length, availableBytes)
            if (toRead > 0) {
                val firstChunk = minOf(toRead, capacity - readHead)
                System.arraycopy(buffer, readHead, dest, destOffset, firstChunk)
                val secondChunk = toRead - firstChunk
                if (secondChunk > 0) {
                    System.arraycopy(buffer, 0, dest, destOffset + firstChunk, secondChunk)
                }
                readHead = (readHead + toRead) % capacity
                availableBytes -= toRead
            }
            if (toRead < length) {
                dest.fill(0, destOffset + toRead, destOffset + length)
            }
            return toRead
        }
    }

    fun available(): Int = synchronized(lock) { availableBytes }

    fun clear() = synchronized(lock) {
        writeHead = 0
        readHead = 0
        availableBytes = 0
    }
}

/**
 * Decoupled Multi-Threaded Audio Capture Engine with Hardware Audio Enhancement & Sidechain Auto-Ducking.
 *
 * Architecture:
 * - Dedicated System Audio Capture Thread: Continuous blocking reads into lock-free RingBuffer.
 * - Dedicated Mic Audio Capture Thread: Continuous blocking reads into lock-free RingBuffer.
 * - Dedicated DSP Mixer & MediaCodec Encoder Thread: Precise 21.3ms cadence pulling from both buffers.
 *
 * Audio Quality & Noise Suppression:
 * - 48,000 Hz Native System Alignment: Resolves Android 10 / ColorOS r_submix rejection ("could not find input mix for attr").
 * - Studio-Grade Mic Source: Uses AudioSource.MIC with CAMCORDER fallback to avoid VoIP mode & hardware AEC mutes.
 * - Real-Time Sidechain Auto-Ducking: Smoothly lowers game audio by -12 dB (to 15%) when speech is detected.
 * - Vocal Presence & High-Pass Filter: Cuts sub-120Hz handling noise and boosts speech formants.
 * - Calibrated Gain Staging: Configurable digital mic preamp with tanh soft-knee saturation limiter.
 */
class AudioCaptureHelper(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    private val audioSource: String, // "System" | "Mic" | "MicSystem"
    private val outputFile: File,
    private val voicePriorityEnabled: Boolean = true,
    private val micGainMultiplier: Float = 3.5f,
    private val internalAudioVolumeRatio: Float = 0.35f,
    private val vocalClarityEnabled: Boolean = true
) {
    companion object {
        private const val TAG = "AudioCaptureHelper"
        private const val CHUNK_BYTES = 4096 // 1024 stereo 16-bit samples = 21.33ms @ 48kHz (exact AAC-LC frame)
        private const val RING_BUFFER_CAPACITY = 384000 // ~2 seconds of 48kHz stereo 16-bit audio
    }

    // Dynamic Sample Rate (defaults to 48000 Hz for standard video containers and Qualcomm/Android HALs)
    val sampleRate: Int = run {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val prop = am?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
        val parsed = prop?.toIntOrNull()
        if (parsed != null && (parsed == 48000 || parsed == 44100)) {
            parsed
        } else {
            48000
        }
    }

    private val bitRate = 192000 // 192 kbps for crystal-clear stereo audio
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    private var audioRecord: AudioRecord? = null
    private var audioRecordSecondary: AudioRecord? = null
    private var isSecMono = false

    // Decoupled Ring Buffers
    private val sysRingBuffer = AudioRingBuffer(RING_BUFFER_CAPACITY)
    private val micRingBuffer = AudioRingBuffer(RING_BUFFER_CAPACITY)

    // Hardware Audio Effects (used only for Mic-only mode; dual mode uses software DSP to prevent status -22)
    private var noiseSuppressorPrimary: NoiseSuppressor? = null
    private var autoGainPrimary: AutomaticGainControl? = null

    private var audioEncoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val muxerLock = Any()

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isMicMuted = AtomicBoolean(false)

    // Dedicated Worker Threads
    private var sysCaptureThread: Thread? = null
    private var micCaptureThread: Thread? = null
    private var mixerThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (!isRecording.compareAndSet(false, true)) return

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(CHUNK_BYTES * 4)

        val isSystem = audioSource == "System" || audioSource == "MicSystem"
        if (isSystem) {
            if (mediaProjection != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val config = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                var primaryRecord: AudioRecord? = null

                // Attempt 1: Target native sample rate (48000 Hz)
                try {
                    primaryRecord = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
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

                // Attempt 2: Fallback sample rate if primary failed initialization
                if (primaryRecord == null || primaryRecord.state != AudioRecord.STATE_INITIALIZED) {
                    val altRate = if (sampleRate == 48000) 44100 else 48000
                    val altBufferSize = AudioRecord.getMinBufferSize(altRate, channelConfig, audioFormat).coerceAtLeast(CHUNK_BYTES * 4)
                    try {
                        primaryRecord?.release()
                        primaryRecord = AudioRecord.Builder()
                            .setAudioPlaybackCaptureConfig(config)
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setEncoding(audioFormat)
                                    .setSampleRate(altRate)
                                    .setChannelMask(channelConfig)
                                    .build()
                            )
                            .setBufferSizeInBytes(altBufferSize)
                            .build()
                        if (primaryRecord.state == AudioRecord.STATE_INITIALIZED) {
                            Log.d(TAG, "AudioPlaybackCapture initialized with fallback rate $altRate Hz")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "AudioPlaybackCapture fallback build at $altRate Hz failed", e)
                    }
                }

                audioRecord = primaryRecord
            }

            val defaultMute = (audioSource == "System")
            isMicMuted.set(defaultMute)
            if (!defaultMute) {
                audioRecordSecondary = createMicAudioRecord(bufferSize)
            } else {
                audioRecordSecondary = null
            }
        } else {
            // Mic only
            val micRecord = createMicAudioRecord(bufferSize)
                ?: AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    bufferSize
                )
            audioRecord = micRecord
            if (micRecord.state == AudioRecord.STATE_INITIALIZED) {
                setupMicAudioEffects(micRecord.audioSessionId)
            }
            isMicMuted.set(false)
        }

        // Initialize MediaCodec AAC Encoder & MediaMuxer
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

        sysRingBuffer.clear()
        micRingBuffer.clear()

        // 1. Launch System Audio Capture Worker Thread
        if (isSystem && audioRecord != null && audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            sysCaptureThread = thread(name = "ScreenX-SysAudioCapture") {
                runSystemAudioCapture()
            }
        }

        // 2. Launch Mic Audio Capture Worker Thread
        val secRecord = if (isSystem) audioRecordSecondary else audioRecord
        if (secRecord != null && secRecord.state == AudioRecord.STATE_INITIALIZED) {
            micCaptureThread = thread(name = "ScreenX-MicAudioCapture") {
                runMicAudioCapture(secRecord)
            }
        }

        // 3. Launch DSP Mixer & Encoder Thread
        mixerThread = thread(name = "ScreenX-AudioMixerEncoder") {
            runAudioMixerAndEncoder()
        }
    }

    /**
     * Dedicated worker thread for system playback capture (AudioPlaybackCapture).
     * Reads continuously and pushes PCM chunks directly into sysRingBuffer.
     */
    private fun runSystemAudioCapture() {
        val rec = audioRecord ?: return
        try {
            rec.startRecording()
            Log.d(TAG, "System AudioRecord started recording")
            val readBuf = ByteArray(CHUNK_BYTES)

            while (isRecording.get()) {
                if (isPaused.get()) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    continue
                }

                val r = rec.read(readBuf, 0, CHUNK_BYTES)
                if (r > 0) {
                    sysRingBuffer.write(readBuf, 0, r)
                } else if (r < 0) {
                    Log.w(TAG, "System AudioRecord native read returned error: $r")
                    try { Thread.sleep(10) } catch (_: InterruptedException) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in SystemAudioCapture worker", e)
        } finally {
            try { rec.stop() } catch (_: Exception) {}
        }
    }

    /**
     * Dedicated worker thread for microphone input.
     * Reads continuously and pushes stereo PCM chunks directly into micRingBuffer.
     */
    private fun runMicAudioCapture(secRecord: AudioRecord) {
        try {
            secRecord.startRecording()
            Log.d(TAG, "Mic AudioRecord started recording (isSecMono=$isSecMono)")
            val rawBuf = ByteArray(CHUNK_BYTES)
            val stereoBuf = ByteArray(CHUNK_BYTES)

            while (isRecording.get()) {
                if (isPaused.get()) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    continue
                }

                if (isSecMono) {
                    val r = secRecord.read(rawBuf, 0, CHUNK_BYTES / 2)
                    if (r > 0) {
                        if (isMicMuted.get()) {
                            stereoBuf.fill(0)
                            micRingBuffer.write(stereoBuf, 0, r * 2)
                        } else {
                            val sampleCount = r / 2
                            var outIdx = 0
                            for (i in 0 until sampleCount) {
                                val low = rawBuf[i * 2]
                                val high = rawBuf[i * 2 + 1]
                                // Duplicate mono to stereo Left & Right
                                stereoBuf[outIdx++] = low
                                stereoBuf[outIdx++] = high
                                stereoBuf[outIdx++] = low
                                stereoBuf[outIdx++] = high
                            }
                            micRingBuffer.write(stereoBuf, 0, outIdx)
                        }
                    } else if (r < 0) {
                        Log.w(TAG, "Mic AudioRecord mono read returned error: $r")
                        try { Thread.sleep(10) } catch (_: InterruptedException) {}
                    }
                } else {
                    val r = secRecord.read(rawBuf, 0, CHUNK_BYTES)
                    if (r > 0) {
                        if (isMicMuted.get()) {
                            rawBuf.fill(0)
                        }
                        micRingBuffer.write(rawBuf, 0, r)
                    } else if (r < 0) {
                        Log.w(TAG, "Mic AudioRecord stereo read returned error: $r")
                        try { Thread.sleep(10) } catch (_: InterruptedException) {}
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in MicAudioCapture worker", e)
        } finally {
            try { secRecord.stop() } catch (_: Exception) {}
        }
    }

    /**
     * High-Precision Audio Mixer & MediaCodec Encoder Thread.
     * Pulls synchronized frames from ring buffers, applies software DSP, and feeds AAC MediaCodec.
     */
    private fun runAudioMixerAndEncoder() {
        val encoder = audioEncoder ?: return
        val isDual = audioSource == "MicSystem" && audioRecordSecondary != null
        val isSysOnly = audioSource == "System"

        val pcmBuffer = ByteBuffer.allocateDirect(CHUNK_BYTES)
        val primaryBytes = ByteArray(CHUNK_BYTES)
        val secondaryBytes = ByteArray(CHUNK_BYTES)

        var totalSamples = 0L
        var frameCount = 0L

        // High-Pass Filter state (120 Hz cutoff @ 48kHz, alpha = 0.9845f) to cut phone rumble
        val hpAlpha = if (sampleRate == 48000) 0.9845f else 0.9832f
        var hpPrevXLeft = 0f
        var hpPrevYLeft = 0f
        var hpPrevXRight = 0f
        var hpPrevYRight = 0f

        // Real-Time Voice Activity Detection & Sidechain Ducking
        var currentDuckingFactor = 1.0f
        val targetDuckedGain = 0.15f // Attenuate game audio down to 15% (-12 dB) when user speaks
        var speechHoldFramesRemaining = 0
        val speechHoldFramesMax = 22 // ~470ms hold bridges natural pauses between words

        // Audio taper (square-law) for human perception of loudness:
        val sysTaper = (internalAudioVolumeRatio * internalAudioVolumeRatio).coerceIn(0.001f, 1.0f)

        // Accurate pacing clock (~21.33ms per 1024 stereo frames @ 48kHz)
        val frameDurationNs = ((CHUNK_BYTES / 4).toLong() * 1000000000L) / sampleRate
        var nextFrameTimeNs = System.nanoTime()

        try {
            while (isRecording.get()) {
                if (isPaused.get()) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    nextFrameTimeNs = System.nanoTime()
                    continue
                }

                // Pacing clock to maintain real-time cadence
                val nowNs = System.nanoTime()
                val waitNs = nextFrameTimeNs - nowNs
                if (waitNs > 1000000L) { // > 1ms
                    val waitMs = waitNs / 1000000L
                    try { Thread.sleep(waitMs) } catch (_: InterruptedException) {}
                }
                nextFrameTimeNs += frameDurationNs
                if (System.nanoTime() - nextFrameTimeNs > frameDurationNs * 5) {
                    nextFrameTimeNs = System.nanoTime()
                }

                if (isDual) {
                    // DUAL AUDIO MODE: Pull 1024 samples from each ring buffer
                    micRingBuffer.readWithSilencePadding(secondaryBytes, 0, CHUNK_BYTES)
                    sysRingBuffer.readWithSilencePadding(primaryBytes, 0, CHUNK_BYTES)

                    // Vocal Clarity: 120Hz high-pass filter cuts physical phone vibration and rumble
                    if (!isMicMuted.get() && vocalClarityEnabled) {
                        for (i in 0 until CHUNK_BYTES - 1 step 4) {
                            val lRaw = ((secondaryBytes[i].toInt() and 0xFF) or ((secondaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort().toFloat()
                            val yL = hpAlpha * (hpPrevYLeft + lRaw - hpPrevXLeft)
                            hpPrevXLeft = lRaw
                            hpPrevYLeft = yL
                            val lOut = yL.toInt().coerceIn(-32768, 32767).toShort()
                            secondaryBytes[i] = (lOut.toInt() and 0xFF).toByte()
                            secondaryBytes[i + 1] = ((lOut.toInt() shr 8) and 0xFF).toByte()

                            if (i + 3 < CHUNK_BYTES) {
                                val rRaw = ((secondaryBytes[i + 2].toInt() and 0xFF) or ((secondaryBytes[i + 3].toInt() and 0xFF) shl 8)).toShort().toFloat()
                                val yR = hpAlpha * (hpPrevYRight + rRaw - hpPrevXRight)
                                hpPrevXRight = rRaw
                                hpPrevYRight = yR
                                val rOut = yR.toInt().coerceIn(-32768, 32767).toShort()
                                secondaryBytes[i + 2] = (rOut.toInt() and 0xFF).toByte()
                                secondaryBytes[i + 3] = ((rOut.toInt() shr 8) and 0xFF).toByte()
                            }
                        }
                    }

                    // Compute System Audio RMS
                    var sysSumSquares = 0.0
                    val sampleCount = CHUNK_BYTES / 2
                    for (i in 0 until CHUNK_BYTES - 1 step 2) {
                        val s = ((primaryBytes[i].toInt() and 0xFF) or ((primaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                        sysSumSquares += (s.toLong() * s.toLong())
                    }
                    val sysRms = if (sampleCount > 0) Math.sqrt(sysSumSquares / sampleCount).toFloat() else 0f

                    // Compute Microphone RMS & Peak Sample
                    var micSumSquares = 0.0
                    var maxMicSample = 0
                    for (i in 0 until CHUNK_BYTES - 1 step 2) {
                        val mVal = ((secondaryBytes[i].toInt() and 0xFF) or ((secondaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                        micSumSquares += (mVal.toLong() * mVal.toLong())
                        val absM = Math.abs(mVal.toInt())
                        if (absM > maxMicSample) maxMicSample = absM
                    }
                    val lastMicRms = if (sampleCount > 0) Math.sqrt(micSumSquares / sampleCount).toFloat() else 0f

                    // Adaptive Voice Activity Detection & Sidechain Ducking
                    if (!isMicMuted.get() && voicePriorityEnabled) {
                        val dynamicSpeechThreshold = if (!isHeadsetConnected()) {
                            maxOf(140f, sysRms * 0.40f)
                        } else {
                            90f
                        }

                        if (lastMicRms >= dynamicSpeechThreshold) {
                            speechHoldFramesRemaining = speechHoldFramesMax
                        } else if (speechHoldFramesRemaining > 0) {
                            speechHoldFramesRemaining--
                        }

                        val targetDuck = if (speechHoldFramesRemaining > 0) targetDuckedGain else 1.0f
                        val interpRate = if (targetDuck < currentDuckingFactor) 0.40f else 0.12f
                        currentDuckingFactor += (targetDuck - currentDuckingFactor) * interpRate
                    } else {
                        currentDuckingFactor = 1.0f
                    }

                    // Intelligent Mic AGC Limiter
                    val targetPeak = 18000f
                    val effectiveMicGain = if (isMicMuted.get()) {
                        0f
                    } else if (maxMicSample > 0 && maxMicSample * micGainMultiplier > targetPeak) {
                        (targetPeak / maxMicSample.toFloat()).coerceIn(0.7f, micGainMultiplier)
                    } else {
                        micGainMultiplier
                    }

                    val effectiveSysGain = sysTaper * currentDuckingFactor

                    // Soft-Knee Saturation Limiter Mix
                    for (i in 0 until CHUNK_BYTES - 1 step 2) {
                        val sRaw = ((primaryBytes[i].toInt() and 0xFF) or ((primaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                        val mRaw = ((secondaryBytes[i].toInt() and 0xFF) or ((secondaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()

                        val s = sRaw.toFloat() * effectiveSysGain
                        val m = mRaw.toFloat() * effectiveMicGain

                        val sum = (s + m).toDouble()
                        val mixed = if (sum > 24000.0) {
                            (24000.0 + (32767.0 - 24000.0) * Math.tanh((sum - 24000.0) / (32767.0 - 24000.0))).toInt()
                        } else if (sum < -24000.0) {
                            (-24000.0 + (-32768.0 - -24000.0) * Math.tanh((sum - -24000.0) / (-32768.0 - -24000.0))).toInt()
                        } else {
                            sum.toInt()
                        }.coerceIn(-32768, 32767)

                        primaryBytes[i] = (mixed and 0xFF).toByte()
                        primaryBytes[i + 1] = ((mixed shr 8) and 0xFF).toByte()
                    }

                    pcmBuffer.clear()
                    pcmBuffer.put(primaryBytes, 0, CHUNK_BYTES)
                    pcmBuffer.position(0)

                    frameCount++
                    if (frameCount % 100 == 0L) {
                        Log.d(TAG, "DualAudio Status: micRms=%.1f, sysRms=%.1f, duck=%.2f, effSys=%.4f, effMic=%.2f, peakMic=%d"
                            .format(lastMicRms, sysRms, currentDuckingFactor, effectiveSysGain, effectiveMicGain, maxMicSample))
                    }
                } else if (isSysOnly) {
                    // System audio only
                    sysRingBuffer.readWithSilencePadding(primaryBytes, 0, CHUNK_BYTES)
                    val effectiveSysGain = sysTaper
                    if (effectiveSysGain < 1.0f) {
                        for (i in 0 until CHUNK_BYTES - 1 step 2) {
                            val sRaw = ((primaryBytes[i].toInt() and 0xFF) or ((primaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                            val s = (sRaw.toFloat() * effectiveSysGain).toInt().coerceIn(-32768, 32767).toShort()
                            primaryBytes[i] = (s.toInt() and 0xFF).toByte()
                            primaryBytes[i + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                        }
                    }
                    pcmBuffer.clear()
                    pcmBuffer.put(primaryBytes, 0, CHUNK_BYTES)
                    pcmBuffer.position(0)
                } else {
                    // Mic only
                    micRingBuffer.readWithSilencePadding(secondaryBytes, 0, CHUNK_BYTES)
                    if (micGainMultiplier != 1.0f) {
                        for (i in 0 until CHUNK_BYTES - 1 step 2) {
                            val mRaw = ((secondaryBytes[i].toInt() and 0xFF) or ((secondaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                            val m = (mRaw.toFloat() * micGainMultiplier).toInt().coerceIn(-32768, 32767).toShort()
                            secondaryBytes[i] = (m.toInt() and 0xFF).toByte()
                            secondaryBytes[i + 1] = ((m.toInt() shr 8) and 0xFF).toByte()
                        }
                    }
                    pcmBuffer.clear()
                    pcmBuffer.put(secondaryBytes, 0, CHUNK_BYTES)
                    pcmBuffer.position(0)
                }

                // Feed MediaCodec input buffer
                val inputIndex = encoder.dequeueInputBuffer(10000L)
                if (inputIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        inputBuffer.clear()
                        val bytesToCopy = minOf(CHUNK_BYTES, inputBuffer.remaining())
                        pcmBuffer.limit(bytesToCopy)
                        inputBuffer.put(pcmBuffer)

                        val pts = (totalSamples * 1000000L) / sampleRate
                        encoder.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0)
                        totalSamples += (bytesToCopy / 4)
                    }
                }

                drainAudioOutput()
            }

            // Flush end of stream
            val eosIndex = encoder.dequeueInputBuffer(10000L)
            if (eosIndex >= 0) {
                encoder.queueInputBuffer(eosIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainAudioOutput()

        } catch (e: Exception) {
            Log.e(TAG, "Audio mixer thread error", e)
        } finally {
            releaseResources()
        }
    }

    private fun isHeadsetConnected(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                device.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                device.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.isWiredHeadsetOn || audioManager.isBluetoothA2dpOn || audioManager.isBluetoothScoOn
        }
    }

    private fun setupMicAudioEffects(sessionId: Int) {
        try {
            if (NoiseSuppressor.isAvailable()) {
                val ns = NoiseSuppressor.create(sessionId)
                ns?.enabled = true
                noiseSuppressorPrimary = ns
                Log.d(TAG, "NoiseSuppressor enabled on mic session $sessionId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable NoiseSuppressor", e)
        }

        try {
            if (AutomaticGainControl.isAvailable()) {
                val agc = AutomaticGainControl.create(sessionId)
                agc?.enabled = true
                autoGainPrimary = agc
                Log.d(TAG, "AutomaticGainControl enabled on mic session $sessionId")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable AutomaticGainControl", e)
        }
    }

    /**
     * Initializes secondary mic AudioRecord using studio input sources:
     * 1. AudioSource.MIC (stereo)
     * 2. AudioSource.MIC (mono)
     * 3. AudioSource.CAMCORDER (mono)
     * 4. AudioSource.VOICE_RECOGNITION (mono)
     * 5. AudioSource.VOICE_COMMUNICATION (mono, last resort fallback)
     */
    @SuppressLint("MissingPermission")
    private fun createMicAudioRecord(bufferSize: Int): AudioRecord? {
        // 1. Try MIC stereo
        try {
            val stereoBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(CHUNK_BYTES * 4)
            val secCandidate = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                stereoBuf
            )
            if (secCandidate.state == AudioRecord.STATE_INITIALIZED) {
                isSecMono = false
                Log.d(TAG, "Secondary AudioRecord (MIC stereo) initialized successfully at $sampleRate Hz")
                return secCandidate
            }
            secCandidate.release()
        } catch (e: Exception) {
            Log.w(TAG, "MIC stereo init failed", e)
        }

        val monoBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat).coerceAtLeast(CHUNK_BYTES * 2)

        // 2. Try MIC mono
        try {
            val micCandidate = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                audioFormat,
                monoBuf
            )
            if (micCandidate.state == AudioRecord.STATE_INITIALIZED) {
                isSecMono = true
                Log.d(TAG, "Secondary AudioRecord (MIC mono fallback) initialized successfully at $sampleRate Hz")
                return micCandidate
            }
            micCandidate.release()
        } catch (e: Exception) {
            Log.w(TAG, "MIC mono init failed", e)
        }

        // 3. Fallback to CAMCORDER (mono)
        try {
            val camCandidate = AudioRecord(
                MediaRecorder.AudioSource.CAMCORDER,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                audioFormat,
                monoBuf
            )
            if (camCandidate.state == AudioRecord.STATE_INITIALIZED) {
                isSecMono = true
                Log.d(TAG, "Secondary AudioRecord (CAMCORDER mono fallback) initialized successfully")
                return camCandidate
            }
            camCandidate.release()
        } catch (e: Exception) {
            Log.e(TAG, "CAMCORDER mono init failed", e)
        }

        // 4. Fallback to VOICE_RECOGNITION (mono)
        try {
            val vrCandidate = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                audioFormat,
                monoBuf
            )
            if (vrCandidate.state == AudioRecord.STATE_INITIALIZED) {
                isSecMono = true
                Log.d(TAG, "Secondary AudioRecord (VOICE_RECOGNITION mono fallback) initialized successfully")
                return vrCandidate
            }
            vrCandidate.release()
        } catch (e: Exception) {
            Log.e(TAG, "VOICE_RECOGNITION mono init failed", e)
        }

        // 5. Last resort: VOICE_COMMUNICATION (mono)
        try {
            val vcCandidate = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                audioFormat,
                monoBuf
            )
            if (vcCandidate.state == AudioRecord.STATE_INITIALIZED) {
                isSecMono = true
                Log.d(TAG, "Secondary AudioRecord (VOICE_COMMUNICATION mono fallback) initialized")
                return vcCandidate
            }
            vcCandidate.release()
        } catch (e: Exception) {
            Log.e(TAG, "VOICE_COMMUNICATION fallback failed", e)
        }

        return null
    }

    fun setMicMuted(muted: Boolean) {
        isMicMuted.set(muted)
    }

    fun isMicMuted(): Boolean = isMicMuted.get()

    private fun drainAudioOutput() {
        val encoder = audioEncoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()
        var outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
        while (outputIndex >= 0 || outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(muxerLock) {
                    if (muxerStarted) return
                    val format = encoder.outputFormat
                    try {
                        muxer?.let { m ->
                            audioTrackIndex = m.addTrack(format)
                            m.start()
                            muxerStarted = true
                            Log.d(TAG, "Audio muxer started successfully")
                        }
                    } catch (ignored: Exception) {}
                }
            } else {
                val outputBuffer = encoder.getOutputBuffer(outputIndex)
                if (muxerStarted && bufferInfo.size > 0 && audioTrackIndex != -1 && outputBuffer != null) {
                    synchronized(muxerLock) {
                        try {
                            muxer?.writeSampleData(audioTrackIndex, outputBuffer, bufferInfo)
                        } catch (ignored: Exception) {}
                    }
                }
                encoder.releaseOutputBuffer(outputIndex, false)
            }
            outputIndex = encoder.dequeueOutputBuffer(bufferInfo, 0L)
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun stop() {
        if (!isRecording.compareAndSet(true, false)) return

        try {
            sysCaptureThread?.join(600)
        } catch (_: InterruptedException) {}
        sysCaptureThread = null

        try {
            micCaptureThread?.join(600)
        } catch (_: InterruptedException) {}
        micCaptureThread = null

        try {
            mixerThread?.join(1000)
        } catch (_: InterruptedException) {}
        mixerThread = null

        releaseResources()
    }

    private fun releaseResources() {
        try {
            noiseSuppressorPrimary?.release()
            noiseSuppressorPrimary = null
        } catch (_: Exception) {}
        try {
            autoGainPrimary?.release()
            autoGainPrimary = null
        } catch (_: Exception) {}

        try {
            audioEncoder?.stop()
            audioEncoder?.release()
        } catch (_: Exception) {}
        audioEncoder = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            audioRecordSecondary?.stop()
            audioRecordSecondary?.release()
        } catch (_: Exception) {}
        audioRecordSecondary = null

        synchronized(muxerLock) {
            if (muxer != null) {
                try {
                    if (muxerStarted) muxer?.stop()
                } catch (_: Exception) {}
                try {
                    muxer?.release()
                } catch (_: Exception) {}
                muxer = null
                muxerStarted = false
            }
        }
    }
}
