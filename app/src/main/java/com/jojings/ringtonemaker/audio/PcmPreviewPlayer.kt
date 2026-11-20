package com.jojings.ringtonemaker.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

/** Plays a frame range of an internal PCM file, with the same fades the saved tune will get. */
class PcmPreviewPlayer(private val scope: CoroutineScope) {

    private val _position = MutableStateFlow(-1L)

    /** Current playback frame in the PCM file, or -1 when stopped. */
    val position: StateFlow<Long> = _position

    @Volatile
    private var job: Job? = null

    fun play(file: File, start: Long, end: Long, fadeIn: Long, fadeOut: Long) {
        stop()
        val total = end - start
        if (total <= 0) return
        job = scope.launch(Dispatchers.IO) {
            val minBuf = AudioTrack.getMinBufferSize(
                Pcm.RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(Pcm.RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuf, 8192 * Pcm.BYTES_PER_FRAME))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            try {
                _position.value = start
                RandomAccessFile(file, "r").use { raf ->
                    raf.seek(start * Pcm.BYTES_PER_FRAME)
                    track.play()
                    val chunk = 2048
                    val bytes = ByteArray(chunk * Pcm.BYTES_PER_FRAME)
                    val shorts = ShortArray(chunk * Pcm.CHANNELS)
                    var pos = 0L
                    while (pos < total && isActive) {
                        val n = min(chunk.toLong(), total - pos).toInt()
                        raf.readFully(bytes, 0, n * Pcm.BYTES_PER_FRAME)
                        for (f in 0 until n) {
                            val g = fadeGain(pos + f, total, fadeIn, fadeOut)
                            for (c in 0 until 2) {
                                val k = f * 2 + c
                                val s = (bytes[k * 2].toInt() and 0xFF) or (bytes[k * 2 + 1].toInt() shl 8)
                                shorts[k] = (s * g).toInt().toShort()
                            }
                        }
                        var written = 0
                        while (written < n * 2 && isActive) {
                            val w = track.write(shorts, written, n * 2 - written)
                            if (w < 0) break
                            written += w
                        }
                        pos += n
                        _position.value = start + head(track)
                    }
                    // Let the buffered tail play out.
                    var last = -1L
                    var stalls = 0
                    while (isActive && head(track) < total && stalls < 20) {
                        delay(25)
                        val h = head(track)
                        _position.value = start + h
                        if (h == last) stalls++ else stalls = 0
                        last = h
                    }
                }
            } finally {
                runCatching { track.pause(); track.flush(); track.stop() }
                track.release()
                if (job == coroutineContext[Job]) _position.value = -1
            }
        }
    }

    private fun head(track: AudioTrack): Long = track.playbackHeadPosition.toLong() and 0xFFFF_FFFFL

    fun stop() {
        job?.cancel()
        job = null
        _position.value = -1
    }
}
