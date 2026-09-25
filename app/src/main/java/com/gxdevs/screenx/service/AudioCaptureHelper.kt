package com.gxdevs.screenx.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Synchronized Audio Capture Helper.
 *
 * Eliminates all buffer-drift echo and voice-blocking:
 * - When recording Dual Audio (Mic + System), the hardware Microphone acts as the
 *   master real-time clock (blocking 23.2ms read), while System Audio uses READ_NON_BLOCKING.
 * - This guarantees that System Audio never blocks the Microphone, and both streams
 *   remain locked to the exact same 23.2ms time window with ZERO buffer drift.
 * - Dynamically scales system audio based on the phone's actual media volume rocker.
 */
class AudioCaptureHelper(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    private val audioSource: String, // "System" | "Mic" | "MicSystem"
    private val outputFile: File
) {
    companion object {
        private const val TAG = "AudioCaptureHelper"
        private const val CHUNK_BYTES = 4096 // 1024 stereo 16-bit samples = 23.2ms @ 44.1kHz
    }

    private var audioRecord: AudioRecord? = null
    private var audioRecordSecondary: AudioRecord? = null
    private var isSecMono = false
    private val audioRecordLock = Any()

    private var audioEncoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val muxerLock = Any()

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isMicMuted = AtomicBoolean(false)
    private var audioThread: Thread? = null

    private val sampleRate = 44100
    private val bitRate = 128000
    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

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

                audioRecord = AudioRecord.Builder()
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
            }

            val defaultMute = (audioSource == "System")
            isMicMuted.set(defaultMute)
            if (!defaultMute) {
                // Secondary AudioRecord for MIC (try stereo, fallback to mono if hardware requires)
                try {
                    val secCandidate = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        channelConfig,
                        audioFormat,
                        bufferSize
                    )
                    if (secCandidate.state == AudioRecord.STATE_INITIALIZED) {
                        audioRecordSecondary = secCandidate
                        isSecMono = false
                        Log.d(TAG, "Secondary AudioRecord (MIC stereo) initialized")
                    } else {
                        secCandidate.release()
                        val monoBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat) * 4
                        val monoCandidate = AudioRecord(
                            MediaRecorder.AudioSource.MIC,
                            sampleRate,
                            AudioFormat.CHANNEL_IN_MONO,
                            audioFormat,
                            monoBuf
                        )
                        if (monoCandidate.state == AudioRecord.STATE_INITIALIZED) {
                            audioRecordSecondary = monoCandidate
                            isSecMono = true
                            Log.d(TAG, "Secondary AudioRecord (MIC mono fallback) initialized")
                        } else {
                            monoCandidate.release()
                            audioRecordSecondary = null
                            Log.e(TAG, "Secondary AudioRecord failed to initialize")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error initializing secondary AudioRecord", e)
                    audioRecordSecondary = null
                }
            } else {
                audioRecordSecondary = null
            }
        } else {
            // Mic only
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
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

        // Start capture thread
        audioThread = thread(name = "AudioEncoderThread") {
            try {
                if (audioEncoder != null) {
                    val primary = audioRecord
                    if (primary != null && primary.state != AudioRecord.STATE_INITIALIZED) {
                        throw IOException("Primary AudioRecord failed to initialize")
                    }

                    synchronized(audioRecordLock) {
                        val sec = audioRecordSecondary
                        if (sec != null && sec.state != AudioRecord.STATE_INITIALIZED) {
                            Log.w(TAG, "Secondary AudioRecord failed. Falling back to single audio.")
                            sec.release()
                            audioRecordSecondary = null
                        }
                    }

                    primary?.startRecording()
                    synchronized(audioRecordLock) {
                        audioRecordSecondary?.startRecording()
                    }

                    if (primary != null && primary.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        throw IOException("Primary AudioRecord failed to start recording")
                    }

                    drainAudio()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal audio thread error", e)
            } finally {
                releaseResources()
            }
        }
    }

    /**
     * Unified lockstep drainAudio loop.
     * Uses mic as pacing clock and READ_NON_BLOCKING on system audio.
     */
    private fun drainAudio() {
        val encoder = audioEncoder ?: return
        val primary = audioRecord
        val isDual = audioSource == "MicSystem" && audioRecordSecondary != null

        val pcmBuffer = ByteBuffer.allocateDirect(CHUNK_BYTES)
        val secBuffer = ByteBuffer.allocateDirect(CHUNK_BYTES)
        val primaryBytes = ByteArray(CHUNK_BYTES)
        val secondaryBytes = ByteArray(CHUNK_BYTES)
        val rawMonoBytes = if (isSecMono) ByteArray(CHUNK_BYTES / 2) else null

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val maxMusicVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        var lastVolCheck = 0L
        var sysVolumeRatio = 1.0f

        var totalSamples = 0L

        try {
            while (isRecording.get()) {
                if (isPaused.get()) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    continue
                }

                // Check volume scale every 250ms
                val now = System.currentTimeMillis()
                if (now - lastVolCheck > 250) {
                    lastVolCheck = now
                    val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: maxMusicVol
                    sysVolumeRatio = if (maxMusicVol > 0) {
                        (curVol.toFloat() / maxMusicVol.toFloat()).coerceIn(0.0f, 1.0f)
                    } else {
                        1.0f
                    }
                }

                var readSys = 0
                var readSec = 0

                if (isDual) {
                    // DUAL AUDIO MODE:
                    // 1. Microphone is the real-time hardware clock (blocking read = exactly 23.2ms pacing)
                    val sec = audioRecordSecondary
                    if (sec != null && !isMicMuted.get()) {
                        if (isSecMono) {
                            val r = sec.read(rawMonoBytes!!, 0, CHUNK_BYTES / 2)
                            if (r > 0) {
                                val samples = r / 2
                                var outIdx = 0
                                for (i in 0 until samples) {
                                    val low = rawMonoBytes[i * 2]
                                    val high = rawMonoBytes[i * 2 + 1]
                                    secondaryBytes[outIdx++] = low
                                    secondaryBytes[outIdx++] = high
                                    secondaryBytes[outIdx++] = low
                                    secondaryBytes[outIdx++] = high
                                }
                                readSec = outIdx
                            }
                        } else {
                            secBuffer.clear()
                            val r = sec.read(secBuffer, CHUNK_BYTES)
                            if (r > 0) {
                                secBuffer.get(secondaryBytes, 0, r)
                                readSec = r
                            }
                        }
                    }

                    // 2. System Audio: NON-BLOCKING read for this exact same 23.2ms time window
                    if (primary != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        pcmBuffer.clear()
                        val r = primary.read(pcmBuffer, CHUNK_BYTES, AudioRecord.READ_NON_BLOCKING)
                        if (r > 0) {
                            pcmBuffer.get(primaryBytes, 0, r)
                            readSys = r
                        }
                    }

                    // 3. Mix in lockstep (zero buffer delay, zero acoustic echo)
                    if (readSys > 0 && readSec > 0) {
                        val minLen = minOf(readSys, readSec)
                        for (i in 0 until minLen - 1 step 2) {
                            val sRaw = (primaryBytes[i].toInt() and 0xFF or (primaryBytes[i + 1].toInt() shl 8)).toShort()
                            val s = (sRaw.toFloat() * sysVolumeRatio).toInt().toShort()
                            val m = (secondaryBytes[i].toInt() and 0xFF or (secondaryBytes[i + 1].toInt() shl 8)).toShort()

                            var mixed = s.toInt() + m.toInt()
                            if (mixed > 32767) mixed = 32767
                            if (mixed < -32768) mixed = -32768

                            primaryBytes[i] = (mixed and 0xFF).toByte()
                            primaryBytes[i + 1] = ((mixed shr 8) and 0xFF).toByte()
                        }
                        pcmBuffer.clear()
                        pcmBuffer.put(primaryBytes, 0, readSys)
                        pcmBuffer.position(0)
                    } else if (readSec > 0) {
                        // Only mic has audio in this slice (system is quiet)
                        pcmBuffer.clear()
                        pcmBuffer.put(secondaryBytes, 0, readSec)
                        pcmBuffer.position(0)
                        readSys = readSec
                    } else if (readSys > 0) {
                        // Only system has audio (e.g. mic muted)
                        if (sysVolumeRatio < 1.0f) {
                            for (i in 0 until readSys - 1 step 2) {
                                val sRaw = (primaryBytes[i].toInt() and 0xFF or (primaryBytes[i + 1].toInt() shl 8)).toShort()
                                val s = (sRaw.toFloat() * sysVolumeRatio).toInt().toShort()
                                primaryBytes[i] = (s.toInt() and 0xFF).toByte()
                                primaryBytes[i + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                            }
                        }
                        pcmBuffer.clear()
                        pcmBuffer.put(primaryBytes, 0, readSys)
                        pcmBuffer.position(0)
                    }
                } else {
                    // SINGLE AUDIO SOURCE (System only or Mic only)
                    val rec = primary ?: audioRecordSecondary ?: break
                    pcmBuffer.clear()
                    val r = rec.read(pcmBuffer, CHUNK_BYTES)
                    if (r > 0) {
                        readSys = r
                        if (audioSource == "System" && sysVolumeRatio < 1.0f) {
                            pcmBuffer.get(primaryBytes, 0, r)
                            for (i in 0 until r - 1 step 2) {
                                val sRaw = (primaryBytes[i].toInt() and 0xFF or (primaryBytes[i + 1].toInt() shl 8)).toShort()
                                val s = (sRaw.toFloat() * sysVolumeRatio).toInt().toShort()
                                primaryBytes[i] = (s.toInt() and 0xFF).toByte()
                                primaryBytes[i + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                            }
                            pcmBuffer.clear()
                            pcmBuffer.put(primaryBytes, 0, r)
                            pcmBuffer.position(0)
                        }
                    }
                }

                if (readSys > 0) {
                    val inputIndex = encoder.dequeueInputBuffer(10000L)
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder.getInputBuffer(inputIndex)
                        if (inputBuffer != null) {
                            inputBuffer.clear()
                            val bytesToCopy = minOf(readSys, inputBuffer.remaining())
                            pcmBuffer.limit(bytesToCopy)
                            inputBuffer.put(pcmBuffer)

                            val pts = (totalSamples * 1000000L) / sampleRate
                            encoder.queueInputBuffer(inputIndex, 0, bytesToCopy, pts, 0)
                            totalSamples += (bytesToCopy / 4)
                        }
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
            Log.e(TAG, "drainAudio error", e)
        }
    }

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
            audioThread?.join(1000)
        } catch (_: InterruptedException) {}
        audioThread = null

        releaseResources()
    }

    private fun releaseResources() {
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
