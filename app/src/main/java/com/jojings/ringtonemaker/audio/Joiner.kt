package com.jojings.ringtonemaker.audio

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

class JoinResult(val pcmFile: File, val frames: Long, val truncated: Boolean)

/** Stitches saved tunes together (with optional silent gaps) into one internal PCM file. */
object Joiner {

    /** A tiny fade at every seam so joins never click. */
    private const val SEAM_FADE_FRAMES = 660L // 15 ms

    suspend fun join(
        context: Context,
        files: List<File>,
        gapMs: Long,
        out: File,
        maxFrames: Long = Pcm.msToFrames(Pcm.MAX_TUNE_MS),
        onProgress: (Float) -> Unit = {},
    ): JoinResult = withContext(Dispatchers.IO) {
        val gapFrames = Pcm.msToFrames(gapMs)
        val part = File(context.cacheDir, "join_part.pcm")
        var written = 0L
        var truncated = false

        BufferedOutputStream(FileOutputStream(out), 1 shl 16).use { stream ->
            val silence = ByteArray(4096)
            fun writeSilence(frames: Long) {
                var left = min(frames, maxFrames - written) * Pcm.BYTES_PER_FRAME
                while (left > 0) {
                    val n = min(left, silence.size.toLong()).toInt()
                    stream.write(silence, 0, n)
                    left -= n
                }
                written = min(written + frames, maxFrames)
            }

            for ((index, file) in files.withIndex()) {
                ensureActive()
                if (written >= maxFrames) {
                    truncated = true
                    break
                }
                if (index > 0 && gapFrames > 0) writeSilence(gapFrames)

                val decoded = AudioDecoder.decode(context, Uri.fromFile(file), part) { p ->
                    onProgress((index + p) / files.size)
                }
                val segFrames = decoded.frames
                RandomAccessFile(part, "r").use { raf ->
                    val buf = ByteArray(4096 * Pcm.BYTES_PER_FRAME)
                    var pos = 0L
                    while (pos < segFrames) {
                        if (written >= maxFrames) {
                            truncated = true
                            break
                        }
                        val n = min(min(4096L, segFrames - pos), maxFrames - written).toInt()
                        raf.readFully(buf, 0, n * Pcm.BYTES_PER_FRAME)
                        val bb = ByteBuffer.wrap(buf, 0, n * Pcm.BYTES_PER_FRAME).order(ByteOrder.LITTLE_ENDIAN)
                        for (f in 0 until n) {
                            val g = fadeGain(pos + f, segFrames, SEAM_FADE_FRAMES, SEAM_FADE_FRAMES)
                            if (g < 1f) {
                                val o = f * Pcm.BYTES_PER_FRAME
                                bb.putShort(o, (bb.getShort(o) * g).toInt().toShort())
                                bb.putShort(o + 2, (bb.getShort(o + 2) * g).toInt().toShort())
                            }
                        }
                        stream.write(buf, 0, n * Pcm.BYTES_PER_FRAME)
                        pos += n
                        written += n
                    }
                }
            }
        }
        part.delete()
        onProgress(1f)
        JoinResult(out, written, truncated)
    }
}
