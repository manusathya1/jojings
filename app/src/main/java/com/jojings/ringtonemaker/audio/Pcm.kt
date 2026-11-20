package com.jojings.ringtonemaker.audio

import java.io.File
import kotlin.math.min

/**
 * Every sound the app works with is converted to one internal format:
 * 44.1 kHz, stereo, 16-bit little-endian PCM ("raw" files in the cache dir).
 * Having a single format makes cutting, previewing and joining trivial.
 */
object Pcm {
    const val RATE = 44_100
    const val CHANNELS = 2
    const val BYTES_PER_FRAME = 4

    /** One waveform peak is stored for every 10 ms of audio. */
    const val PEAK_FRAMES = 441
    const val MS_PER_PEAK = 10

    const val MIN_TUNE_MS = 1_000L
    const val MAX_TUNE_MS = 120_000L

    fun msToFrames(ms: Long): Long = ms * RATE / 1000
    fun framesToMs(frames: Long): Long = frames * 1000 / RATE
}

class AudioException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** A decoded sound ready for editing. */
class DecodedAudio(
    val pcmFile: File,
    val frames: Long,
    val peaks: FloatArray,
    val title: String,
) {
    val durationMs: Long get() = Pcm.framesToMs(frames)
}

/** Linear fade-in / fade-out gain for frame [i] of [total]. */
fun fadeGain(i: Long, total: Long, fadeIn: Long, fadeOut: Long): Float {
    var g = 1f
    if (fadeIn > 0 && i < fadeIn) g = min(g, i.toFloat() / fadeIn)
    if (fadeOut > 0 && i > total - fadeOut) g = min(g, (total - i).toFloat() / fadeOut)
    return g.coerceIn(0f, 1f)
}

/** Streaming linear-interpolation resampler for stereo float audio. */
internal class StereoResampler(private val inRate: Int, private val outRate: Int) {
    private val step = inRate.toDouble() / outRate
    private var pos = 0.0
    private var prevL = 0f
    private var prevR = 0f
    private var hasPrev = false

    fun process(l: FloatArray, r: FloatArray, n: Int, emit: (Float, Float) -> Unit) {
        if (n <= 0) return
        if (inRate == outRate) {
            for (i in 0 until n) emit(l[i], r[i])
            return
        }
        if (!hasPrev) {
            prevL = l[0]; prevR = r[0]; hasPrev = true
        }
        // Virtual sequence: index 0 = previous chunk's last frame, index k = l/r[k-1].
        while (pos < n) {
            val idx = pos.toInt()
            val frac = (pos - idx).toFloat()
            val al = if (idx == 0) prevL else l[idx - 1]
            val ar = if (idx == 0) prevR else r[idx - 1]
            val bl = l[idx]
            val br = r[idx]
            emit(al + (bl - al) * frac, ar + (br - ar) * frac)
            pos += step
        }
        pos -= n
        prevL = l[n - 1]; prevR = r[n - 1]
    }
}
