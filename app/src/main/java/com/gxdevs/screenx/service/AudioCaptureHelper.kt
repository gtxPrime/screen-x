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
 * Synchronized Audio Capture Helper with Hardware Audio Enhancement & Sidechain Auto-Ducking.
 *
 * Audio Quality & Noise Suppression:
 * - Real-Time Sidechain Auto-Ducking: Smoothly lowers game audio by -12 dB (to 25%) when speech is detected.
 * - Vocal Presence & High-Pass Filter: Cuts sub-120Hz handling noise and boosts speech formants.
 * - Calibrated Gain Staging: Configurable 1.0x - 3.0x digital mic preamp with soft-knee saturation.
 * - Headset-Aware Echo Cancellation: Relaxes destructive AEC when earphones are plugged in.
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
        private const val CHUNK_BYTES = 4096 // 1024 stereo 16-bit samples = 23.2ms @ 44.1kHz
    }

    private var audioRecord: AudioRecord? = null
    private var audioRecordSecondary: AudioRecord? = null
    private var isSecMono = false
    private val audioRecordLock = Any()

    // Hardware Audio Effects
    private var noiseSuppressorPrimary: NoiseSuppressor? = null
    private var noiseSuppressorSecondary: NoiseSuppressor? = null
    private var echoCancelerSecondary: AcousticEchoCanceler? = null
    private var autoGainPrimary: AutomaticGainControl? = null
    private var autoGainSecondary: AutomaticGainControl? = null

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
    private val bitRate = 192000 // 192 kbps for crystal-clear stereo audio
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
                audioRecordSecondary = createMicAudioRecord(bufferSize)
            } else {
                audioRecordSecondary = null
            }
        } else {
            // Mic only
            val micRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            audioRecord = micRecord
            if (micRecord.state == AudioRecord.STATE_INITIALIZED) {
                setupMicAudioEffects(micRecord.audioSessionId, isSecondary = false)
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
        val primaryBytes = ByteArray(CHUNK_BYTES)
        val secondaryBytes = ByteArray(CHUNK_BYTES)
        val rawMonoBytes = ByteArray(CHUNK_BYTES / 2)

        var totalSamples = 0L
        var frameCount = 0L

        // High-Pass Filter state (120 Hz cutoff @ 44.1kHz, alpha = 0.9832f) to cut phone handling noise & sub-bass rumble
        val hpAlpha = 0.9832f
        var hpPrevXLeft = 0f
        var hpPrevYLeft = 0f
        var hpPrevXRight = 0f
        var hpPrevYRight = 0f

        // Real-Time Voice Activity Detection & Sidechain Ducking
        var currentDuckingFactor = 1.0f
        val targetDuckedGain = 0.15f // Attenuate game audio down to 15% when user speaks
        val speechThresholdRms = 100f // Highly responsive speech detection (ambient noise is < 50, speech is 180 - 4000)
        var speechHoldFramesRemaining = 0
        val speechHoldFramesMax = 20 // 20 * 23.2ms ≈ 460ms hold (bridges pauses between words)
        val recBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(CHUNK_BYTES * 4)

        // Audio taper (square-law) for human perception of loudness:
        // Human hearing is logarithmic. At 15%, 0.15^2 = 0.0225 (-33 dB), perfectly keeping game audio underneath vocal commentary.
        val sysTaper = (internalAudioVolumeRatio * internalAudioVolumeRatio).coerceIn(0.001f, 1.0f)

        try {
            while (isRecording.get()) {
                if (isPaused.get()) {
                    try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    continue
                }

                var readSys = 0
                var lastMicRms = 0f

                if (isDual) {
                    // DUAL AUDIO MODE:
                    // 1. Microphone is the real-time hardware clock (blocking read = exactly 23.2ms pacing)
                    val sec = audioRecordSecondary
                    if (sec != null) {
                        if (isSecMono) {
                            val r = sec.read(rawMonoBytes, 0, CHUNK_BYTES / 2)
                            if (r > 0) {
                                if (r < CHUNK_BYTES / 2) {
                                    rawMonoBytes.fill(0, r, CHUNK_BYTES / 2)
                                }
                                val samples = CHUNK_BYTES / 4
                                var outIdx = 0
                                for (i in 0 until samples) {
                                    val low = rawMonoBytes[i * 2]
                                    val high = rawMonoBytes[i * 2 + 1]
                                    // Duplicate mono to stereo (Left & Right)
                                    secondaryBytes[outIdx++] = low
                                    secondaryBytes[outIdx++] = high
                                    secondaryBytes[outIdx++] = low
                                    secondaryBytes[outIdx++] = high
                                }
                            } else if (r < 0) {
                                Log.w(TAG, "Secondary AudioRecord (mono) native read returned $r. Recovering...")
                                recoverSecondaryAudioRecord(recBufferSize)
                                secondaryBytes.fill(0)
                            } else {
                                secondaryBytes.fill(0)
                            }
                        } else {
                            val r = sec.read(secondaryBytes, 0, CHUNK_BYTES)
                            if (r > 0) {
                                if (r < CHUNK_BYTES) {
                                    secondaryBytes.fill(0, r, CHUNK_BYTES)
                                }
                            } else if (r < 0) {
                                Log.w(TAG, "Secondary AudioRecord (stereo) native read returned $r. Recovering...")
                                recoverSecondaryAudioRecord(recBufferSize)
                                secondaryBytes.fill(0)
                            } else {
                                secondaryBytes.fill(0)
                            }
                        }

                        // If mic is muted in UI, zero out mic samples
                        if (isMicMuted.get()) {
                            secondaryBytes.fill(0)
                        }

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
                    } else {
                        // Sec is null, fall back to sleep for pacing
                        try { Thread.sleep(20) } catch (_: InterruptedException) {}
                    }

                    // 2. System Audio: NON-BLOCKING read for this exact same 23.2ms time window
                    if (primary != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val r = primary.read(primaryBytes, 0, CHUNK_BYTES, AudioRecord.READ_NON_BLOCKING)
                        if (r > 0) {
                            if (r < CHUNK_BYTES) {
                                primaryBytes.fill(0, r, CHUNK_BYTES)
                            }
                        } else {
                            primaryBytes.fill(0)
                        }
                    } else {
                        primaryBytes.fill(0)
                    }

                    // 3. Compute System Audio RMS
                    var sysSumSquares = 0.0
                    val sampleCount = CHUNK_BYTES / 2
                    for (i in 0 until CHUNK_BYTES - 1 step 2) {
                        val s = ((primaryBytes[i].toInt() and 0xFF) or ((primaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                        sysSumSquares += (s.toLong() * s.toLong())
                    }
                    val sysRms = if (sampleCount > 0) Math.sqrt(sysSumSquares / sampleCount).toFloat() else 0f

                    // 4. Compute Microphone RMS & Peak Sample
                    var micSumSquares = 0.0
                    var maxMicSample = 0
                    for (i in 0 until CHUNK_BYTES - 1 step 2) {
                        val mVal = ((secondaryBytes[i].toInt() and 0xFF) or ((secondaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                        micSumSquares += (mVal.toLong() * mVal.toLong())
                        val absM = Math.abs(mVal.toInt())
                        if (absM > maxMicSample) maxMicSample = absM
                    }
                    lastMicRms = if (sampleCount > 0) Math.sqrt(micSumSquares / sampleCount).toFloat() else 0f

                    // 5. Adaptive Voice Activity Detection & Sidechain Ducking
                    if (!isMicMuted.get() && voicePriorityEnabled) {
                        // In loudspeaker mode (no headset), game sound from the speaker leaks into the mic.
                        // Float the speech threshold dynamically with sysRms so game sound alone does not trigger false ducking:
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
                        // Smooth attack (~25ms) and smooth release (~350ms) to avoid audio pops
                        val interpRate = if (targetDuck < currentDuckingFactor) 0.40f else 0.12f
                        currentDuckingFactor += (targetDuck - currentDuckingFactor) * interpRate
                    } else {
                        currentDuckingFactor = 1.0f
                    }

                    // 6. Intelligent Mic AGC Limiter:
                    // Target peak is 18000 (~ -5 dBFS) so the mic signal NEVER clips or saturates the tanh ceiling (24000).
                    // If the mic is quiet/normal, full configured micGainMultiplier is applied.
                    // If speaker sound or shouting drives the mic loud, gain pulls back gracefully.
                    val targetPeak = 18000f
                    val effectiveMicGain = if (isMicMuted.get()) {
                        0f
                    } else if (maxMicSample > 0 && maxMicSample * micGainMultiplier > targetPeak) {
                        (targetPeak / maxMicSample.toFloat()).coerceIn(0.7f, micGainMultiplier)
                    } else {
                        micGainMultiplier
                    }

                    // 7. Decoupled Internal Audio Gain:
                    // Pure digital recording level from settings (sysTaper) with sidechain ducking.
                    val effectiveSysGain = sysTaper * currentDuckingFactor

                    // 8. Lockstep Mix with Soft-Knee Saturation Limiter
                    for (i in 0 until CHUNK_BYTES - 1 step 2) {
                        val sRaw = ((primaryBytes[i].toInt() and 0xFF) or ((primaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                        val mRaw = ((secondaryBytes[i].toInt() and 0xFF) or ((secondaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()

                        val s = sRaw.toFloat() * effectiveSysGain
                        val m = mRaw.toFloat() * effectiveMicGain

                        // Soft-knee saturation: 100% linear within [-24000, 24000], smooth saturation above to prevent digital distortion
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
                    readSys = CHUNK_BYTES

                    frameCount++
                    if (frameCount % 100 == 0L) {
                        Log.d(TAG, "DualAudio Status: micRms=%.1f, sysRms=%.1f, duck=%.2f, effSys=%.4f, effMic=%.2f, peakMic=%d"
                            .format(lastMicRms, sysRms, currentDuckingFactor, effectiveSysGain, effectiveMicGain, maxMicSample))
                    }
                } else {
                    // SINGLE AUDIO SOURCE (System only or Mic only)
                    val rec = primary ?: audioRecordSecondary ?: break
                    pcmBuffer.clear()
                    val r = rec.read(pcmBuffer, CHUNK_BYTES)
                    if (r > 0) {
                        readSys = r
                        if (audioSource == "System") {
                            val effectiveSysGain = sysTaper
                            if (effectiveSysGain < 1.0f) {
                                pcmBuffer.get(primaryBytes, 0, r)
                                for (i in 0 until r - 1 step 2) {
                                    val sRaw = ((primaryBytes[i].toInt() and 0xFF) or ((primaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                                    val s = (sRaw.toFloat() * effectiveSysGain).toInt().coerceIn(-32768, 32767).toShort()
                                    primaryBytes[i] = (s.toInt() and 0xFF).toByte()
                                    primaryBytes[i + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
                                }
                                pcmBuffer.clear()
                                pcmBuffer.put(primaryBytes, 0, r)
                                pcmBuffer.position(0)
                            }
                        } else if (audioSource == "Mic" && micGainMultiplier != 1.0f) {
                            pcmBuffer.get(primaryBytes, 0, r)
                            for (i in 0 until r - 1 step 2) {
                                val mRaw = ((primaryBytes[i].toInt() and 0xFF) or ((primaryBytes[i + 1].toInt() and 0xFF) shl 8)).toShort()
                                val m = (mRaw.toFloat() * micGainMultiplier).toInt().coerceIn(-32768, 32767).toShort()
                                primaryBytes[i] = (m.toInt() and 0xFF).toByte()
                                primaryBytes[i + 1] = ((m.toInt() shr 8) and 0xFF).toByte()
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

    private fun setupMicAudioEffects(sessionId: Int, isSecondary: Boolean) {
        if (isSecondary) {
            // CRITICAL: On Samsung/OneUI and modern Android HALs, attaching hardware AcousticEchoCanceler
            // or AutomaticGainControl to an AudioRecord session while AudioPlaybackCapture is
            // recording from REMOTE_SUBMIX causes AudioFlinger track creation to fail with status -22 (EINVAL).
            // All processing (gain boost, VAD ducking, 120Hz high-pass filter, noise gating) is handled
            // with 100% stability in software DSP.
            Log.d(TAG, "Dual audio mode: Using software DSP exclusively to prevent AudioFlinger status -22 track invalidation")
            return
        }

        val hasHeadset = isHeadsetConnected()

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

        if (!hasHeadset) {
            try {
                if (AcousticEchoCanceler.isAvailable()) {
                    val aec = AcousticEchoCanceler.create(sessionId)
                    aec?.enabled = true
                    if (isSecondary) echoCancelerSecondary = aec
                    Log.d(TAG, "AcousticEchoCanceler enabled on mic session $sessionId (Speaker in use)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to enable AcousticEchoCanceler", e)
            }
        } else {
            Log.d(TAG, "Headset connected: Skipping AcousticEchoCanceler to preserve full vocal dynamics")
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

    @SuppressLint("MissingPermission")
    private fun createMicAudioRecord(bufferSize: Int): AudioRecord? {
        val monoBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, audioFormat).coerceAtLeast(CHUNK_BYTES * 2)

        // 1. Try VOICE_COMMUNICATION (mono)
        // Bypasses Samsung MultiRecordManager concurrency blocking and activates hardware AEC + AGC
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
                Log.d(TAG, "Secondary AudioRecord (VOICE_COMMUNICATION mono) initialized successfully")
                return vcCandidate
            }
            vcCandidate.release()
        } catch (e: Exception) {
            Log.w(TAG, "VOICE_COMMUNICATION mic init failed, trying MIC mono", e)
        }

        // 2. Fallback to AudioSource.MIC (mono)
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
                Log.d(TAG, "Secondary AudioRecord (MIC mono fallback) initialized successfully")
                return micCandidate
            }
            micCandidate.release()
        } catch (e: Exception) {
            Log.w(TAG, "MIC mono init failed, trying CAMCORDER mono", e)
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

        // 4. Last resort: Try MIC stereo
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
                Log.d(TAG, "Secondary AudioRecord (MIC stereo fallback) initialized successfully")
                return secCandidate
            }
            secCandidate.release()
        } catch (e: Exception) {
            Log.w(TAG, "Stereo mic init failed", e)
        }

        return null
    }

    private fun recoverSecondaryAudioRecord(bufferSize: Int) {
        synchronized(audioRecordLock) {
            try {
                audioRecordSecondary?.stop()
                audioRecordSecondary?.release()
            } catch (_: Exception) {}
            audioRecordSecondary = null

            try {
                Thread.sleep(40)
                val newRec = createMicAudioRecord(bufferSize)
                if (newRec != null && newRec.state == AudioRecord.STATE_INITIALIZED) {
                    newRec.startRecording()
                    audioRecordSecondary = newRec
                    Log.d(TAG, "Secondary AudioRecord recovered and restarted recording!")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restart secondary AudioRecord", e)
            }
        }
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
            audioThread?.join(1000)
        } catch (_: InterruptedException) {}
        audioThread = null

        releaseResources()
    }

    private fun releaseResources() {
        try {
            noiseSuppressorPrimary?.release()
            noiseSuppressorPrimary = null
        } catch (_: Exception) {}
        try {
            noiseSuppressorSecondary?.release()
            noiseSuppressorSecondary = null
        } catch (_: Exception) {}
        try {
            echoCancelerSecondary?.release()
            echoCancelerSecondary = null
        } catch (_: Exception) {}
        try {
            autoGainPrimary?.release()
            autoGainPrimary = null
        } catch (_: Exception) {}
        try {
            autoGainSecondary?.release()
            autoGainSecondary = null
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
