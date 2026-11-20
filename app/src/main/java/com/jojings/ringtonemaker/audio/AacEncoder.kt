package com.jojings.ringtonemaker.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/** Encodes a frame range of an internal PCM file into an AAC .m4a file (great for ringtones). */
object AacEncoder {

    suspend fun encode(
        pcm: File,
        startFrame: Long,
        endFrame: Long,
        out: File,
        fadeInFrames: Long = 0,
        fadeOutFrames: Long = 0,
        onProgress: (Float) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val total = endFrame - startFrame
        require(total > 0) { "Nothing to encode" }
        val tmp = File(out.parentFile, out.name + ".part")

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, Pcm.RATE, Pcm.CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 192_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var muxer: MediaMuxer? = null
        var success = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            RandomAccessFile(pcm, "r").use { raf ->
                raf.seek(startFrame * Pcm.BYTES_PER_FRAME)
                val info = MediaCodec.BufferInfo()
                var track = -1
                var inputDone = false
                var outputDone = false
                var pos = 0L
                var readBuf = ByteArray(16_384)

                while (!outputDone) {
                    ensureActive()
                    if (!inputDone) {
                        val idx = codec.dequeueInputBuffer(10_000)
                        if (idx >= 0) {
                            val inBuf = codec.getInputBuffer(idx)!!
                            inBuf.clear()
                            inBuf.order(ByteOrder.LITTLE_ENDIAN)
                            val frames = min(min((inBuf.capacity() / Pcm.BYTES_PER_FRAME).toLong(), 4096L), total - pos).toInt()
                            if (frames <= 0) {
                                codec.queueInputBuffer(idx, 0, 0, ptsUs(pos), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                val nBytes = frames * Pcm.BYTES_PER_FRAME
                                if (readBuf.size < nBytes) readBuf = ByteArray(nBytes)
                                raf.readFully(readBuf, 0, nBytes)
                                val src = ByteBuffer.wrap(readBuf, 0, nBytes).order(ByteOrder.LITTLE_ENDIAN)
                                for (f in 0 until frames) {
                                    val g = fadeGain(pos + f, total, fadeInFrames, fadeOutFrames)
                                    inBuf.putShort((src.short * g).toInt().toShort())
                                    inBuf.putShort((src.short * g).toInt().toShort())
                                }
                                codec.queueInputBuffer(idx, 0, nBytes, ptsUs(pos), 0)
                                pos += frames
                                onProgress(pos.toFloat() / total)
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                    } else if (outIdx >= 0) {
                        val outBuf = codec.getOutputBuffer(outIdx)!!
                        if ((info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) info.size = 0
                        if (info.size > 0 && track >= 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            muxer.writeSampleData(track, outBuf, info)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    }
                }
            }
            muxer.stop()
            success = true
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { muxer?.release() }
            if (!success) tmp.delete()
        }
        if (!tmp.renameTo(out)) {
            tmp.delete()
            throw AudioException("Couldn't save the tune.")
        }
    }

    private fun ptsUs(frame: Long) = frame * 1_000_000L / Pcm.RATE
}
