package com.jojings.ringtonemaker.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

class DecodeResult(val frames: Long, val peaks: FloatArray, val truncated: Boolean)

/**
 * Pulls the first audio track out of any audio or video file the phone can read
 * (MP3, AAC/M4A, OGG, OPUS, FLAC, WAV, AMR, MP4, MKV, WEBM, 3GP, …) and converts it to
 * the app's internal PCM format (see [Pcm]).
 */
object AudioDecoder {

    /** Longest stretch of a source file we decode (keeps the cache file reasonable). */
    const val MAX_SOURCE_MS = 20L * 60 * 1000

    suspend fun decode(
        context: Context,
        uri: Uri,
        out: File,
        maxMs: Long = MAX_SOURCE_MS,
        onProgress: (Float) -> Unit = {},
    ): DecodeResult = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
        } catch (e: Exception) {
            extractor.release()
            throw AudioException("Couldn't open this file. Your phone may not support this format.", e)
        }

        var codec: MediaCodec? = null
        try {
            val trackIndex = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: throw AudioException("No sound found in this file.")

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                format.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else AudioFormat.ENCODING_PCM_16BIT

            try {
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()
            } catch (e: Exception) {
                throw AudioException("Your phone can't decode this kind of audio ($mime).", e)
            }

            val maxFrames = Pcm.msToFrames(maxMs)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var truncated = false

            PcmSink(out).use { sink ->
                while (!outputDone) {
                    ensureActive()
                    if (!inputDone) {
                        val inIdx = codec.dequeueInputBuffer(10_000)
                        if (inIdx >= 0) {
                            val buf = codec.getInputBuffer(inIdx)!!
                            val size = extractor.readSampleData(buf, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val of = codec.outputFormat
                            sampleRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            encoding = if (of.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                of.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            } else AudioFormat.ENCODING_PCM_16BIT
                        }
                        outIdx >= 0 -> {
                            if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                val buf = codec.getOutputBuffer(outIdx)!!
                                buf.position(info.offset)
                                buf.limit(info.offset + info.size)
                                sink.write(buf, encoding, channels, sampleRate)
                            }
                            codec.releaseOutputBuffer(outIdx, false)
                            if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                            if (durationUs > 0) {
                                onProgress((info.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f))
                            }
                            if (sink.frames >= maxFrames) {
                                truncated = true
                                outputDone = true
                            }
                        }
                    }
                }
                onProgress(1f)
                DecodeResult(sink.frames.coerceAtMost(maxFrames), sink.peaks(), truncated)
            }
        } catch (e: AudioException) {
            throw e
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw AudioException("Something went wrong while reading this file.", e)
        } finally {
            codec?.let { runCatching { it.stop() }; it.release() }
            extractor.release()
        }
    }
}

/** Converts decoder output of any PCM flavour to 44.1 kHz stereo 16-bit and writes it to a file. */
private class PcmSink(file: File) : Closeable {
    private val stream = FileOutputStream(file)
    private val outBuf = ByteArray(1 shl 16)
    private var outLen = 0

    private var resampler: StereoResampler? = null
    private var resamplerRate = -1

    var frames = 0L
        private set

    private var peaks = FloatArray(4096)
    private var peakCount = 0
    private var peakAcc = 0f
    private var peakFrames = 0

    private var l = FloatArray(4096)
    private var r = FloatArray(4096)

    fun write(buf: ByteBuffer, encoding: Int, channels: Int, rate: Int) {
        buf.order(ByteOrder.LITTLE_ENDIAN)
        val bps = when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT -> 1
            AudioFormat.ENCODING_PCM_FLOAT -> 4
            ENCODING_PCM_24BIT_PACKED -> 3
            ENCODING_PCM_32BIT -> 4
            else -> 2
        }
        val ch = max(1, channels)
        val frameBytes = bps * ch
        val n = buf.remaining() / frameBytes
        if (n <= 0) return
        if (l.size < n) {
            l = FloatArray(n); r = FloatArray(n)
        }
        val start = buf.position()
        for (i in 0 until n) {
            val base = start + i * frameBytes
            val a = sample(buf, base, encoding)
            l[i] = a
            r[i] = if (ch > 1) sample(buf, base + bps, encoding) else a
        }
        if (rate != resamplerRate) {
            resampler = StereoResampler(rate, Pcm.RATE)
            resamplerRate = rate
        }
        resampler!!.process(l, r, n) { x, y -> emit(x, y) }
    }

    private fun sample(buf: ByteBuffer, pos: Int, encoding: Int): Float = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> ((buf.get(pos).toInt() and 0xFF) - 128) / 128f
        AudioFormat.ENCODING_PCM_FLOAT -> buf.getFloat(pos)
        ENCODING_PCM_24BIT_PACKED -> {
            val v = (buf.get(pos).toInt() and 0xFF) or
                ((buf.get(pos + 1).toInt() and 0xFF) shl 8) or
                (buf.get(pos + 2).toInt() shl 16)
            v / 8_388_608f
        }
        ENCODING_PCM_32BIT -> buf.getInt(pos) / 2_147_483_648f
        else -> buf.getShort(pos) / 32_768f
    }

    private fun emit(x: Float, y: Float) {
        val sl = (x.coerceIn(-1f, 1f) * 32_767f).toInt()
        val sr = (y.coerceIn(-1f, 1f) * 32_767f).toInt()
        if (outLen + 4 > outBuf.size) flush()
        outBuf[outLen++] = sl.toByte()
        outBuf[outLen++] = (sl shr 8).toByte()
        outBuf[outLen++] = sr.toByte()
        outBuf[outLen++] = (sr shr 8).toByte()
        frames++

        val p = max(abs(x), abs(y))
        if (p > peakAcc) peakAcc = p
        if (++peakFrames == Pcm.PEAK_FRAMES) pushPeak()
    }

    private fun pushPeak() {
        if (peakCount == peaks.size) peaks = peaks.copyOf(peaks.size * 2)
        peaks[peakCount++] = peakAcc.coerceAtMost(1f)
        peakAcc = 0f
        peakFrames = 0
    }

    private fun flush() {
        stream.write(outBuf, 0, outLen)
        outLen = 0
    }

    fun peaks(): FloatArray {
        if (peakFrames > 0) pushPeak()
        val result = peaks.copyOf(peakCount)
        // Normalise so quiet recordings still draw a nice big waveform.
        val max = result.maxOrNull() ?: 0f
        if (max > 0.0001f) for (i in result.indices) result[i] = result[i] / max
        return result
    }

    override fun close() {
        flush()
        stream.close()
    }

    companion object {
        // AudioFormat constants added in newer API levels; values are stable.
        const val ENCODING_PCM_24BIT_PACKED = 21
        const val ENCODING_PCM_32BIT = 22
    }
}
