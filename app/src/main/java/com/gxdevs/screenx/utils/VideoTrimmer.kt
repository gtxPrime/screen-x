package com.gxdevs.screenx.utils

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer

object VideoTrimmer {

    /**
     * Trims a video from [startMs] to [endMs] and writes to [outputFile].
     * Uses MediaExtractor and MediaMuxer for fast, lossless trimming.
     */
    fun trim(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        startMs: Long,
        endMs: Long,
        progressCallback: (Float) -> Unit = {}
    ) {
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: throw IllegalArgumentException("Could not open source URI: $sourceUri")
        extractor.setDataSource(pfd.fileDescriptor)

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val trackCount = extractor.trackCount
        val indexMap = HashMap<Int, Int>()
        
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val dstIndex = muxer.addTrack(format)
            indexMap[i] = dstIndex
        }

        muxer.start()

        val startUs = startMs * 1000
        val endUs = endMs * 1000

        // Select all tracks before seeking
        for (i in 0 until trackCount) {
            extractor.selectTrack(i)
        }

        // Seek to the keyframe before or at the start time
        extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

        val bufferSize = 2 * 1024 * 1024 // 2MB buffer
        val dstBuf = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) {
                    break
                }

                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs > endUs) {
                    break
                }

                // Only write samples starting from startUs
                if (presentationTimeUs >= startUs) {
                    bufferInfo.presentationTimeUs = presentationTimeUs - startUs
                    bufferInfo.flags = extractor.sampleFlags

                    val trackIndex = extractor.sampleTrackIndex
                    val dstTrackIndex = indexMap[trackIndex]
                    if (dstTrackIndex != null) {
                        muxer.writeSampleData(dstTrackIndex, dstBuf, bufferInfo)
                    }
                }

                // Call progress callback
                val totalDurationUs = endUs - startUs
                if (totalDurationUs > 0) {
                    val currentProgress = (presentationTimeUs - startUs).toFloat() / totalDurationUs
                    progressCallback(currentProgress.coerceIn(0f, 1f))
                }

                extractor.advance()
            }
        } finally {
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
            extractor.release()
            pfd.close()
        }
    }

    /**
     * Cuts out the middle section [cutStartMs .. cutEndMs] of a video.
     * Keeps [0 .. cutStartMs] and [cutEndMs .. durationMs].
     */
    fun cutMiddle(
        context: Context,
        sourceUri: Uri,
        outputFile: File,
        cutStartMs: Long,
        cutEndMs: Long,
        progressCallback: (Float) -> Unit = {}
    ) {
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(sourceUri, "r")
            ?: throw IllegalArgumentException("Could not open source URI: $sourceUri")
        extractor.setDataSource(pfd.fileDescriptor)

        val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        val trackCount = extractor.trackCount
        val indexMap = HashMap<Int, Int>()
        
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val dstIndex = muxer.addTrack(format)
            indexMap[i] = dstIndex
        }

        muxer.start()

        val cutStartUs = cutStartMs * 1000
        val cutEndUs = cutEndMs * 1000
        val cutDurationUs = cutEndUs - cutStartUs

        for (i in 0 until trackCount) {
            extractor.selectTrack(i)
        }

        val bufferSize = 2 * 1024 * 1024
        val dstBuf = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            // Segment 1: Start to cutStartUs
            extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) {
                    break
                }

                val presentationTimeUs = extractor.sampleTime
                if (presentationTimeUs > cutStartUs) {
                    break
                }

                bufferInfo.presentationTimeUs = presentationTimeUs
                bufferInfo.flags = extractor.sampleFlags

                val trackIndex = extractor.sampleTrackIndex
                val dstTrackIndex = indexMap[trackIndex]
                if (dstTrackIndex != null) {
                    muxer.writeSampleData(dstTrackIndex, dstBuf, bufferInfo)
                }
                
                progressCallback((presentationTimeUs.toFloat() / cutStartUs.toFloat() * 0.5f).coerceIn(0f, 0.5f))
                extractor.advance()
            }

            // Segment 2: cutEndUs to End
            extractor.seekTo(cutEndUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(dstBuf, 0)
                if (bufferInfo.size < 0) {
                    break
                }

                val presentationTimeUs = extractor.sampleTime
                val adjustedTimeUs = presentationTimeUs - cutDurationUs

                if (presentationTimeUs >= cutEndUs) {
                    bufferInfo.presentationTimeUs = adjustedTimeUs
                    bufferInfo.flags = extractor.sampleFlags

                    val trackIndex = extractor.sampleTrackIndex
                    val dstTrackIndex = indexMap[trackIndex]
                    if (dstTrackIndex != null) {
                        muxer.writeSampleData(dstTrackIndex, dstBuf, bufferInfo)
                    }
                }
                
                progressCallback((0.5f + (presentationTimeUs - cutEndUs).toFloat() / 10_000_000f * 0.5f).coerceIn(0.5f, 1f))
                extractor.advance()
            }
        } finally {
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
            extractor.release()
            pfd.close()
        }
    }
}
