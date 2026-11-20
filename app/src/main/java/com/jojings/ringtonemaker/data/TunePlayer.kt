package com.jojings.ringtonemaker.data

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/** Plays saved tunes from the "My Tunes" list. */
class TunePlayer(private val scope: CoroutineScope) {
    var playing by mutableStateOf<File?>(null)
        private set
    var progress by mutableFloatStateOf(0f)
        private set

    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    fun toggle(file: File): Boolean {
        if (playing == file) {
            stop()
            return true
        }
        return play(file)
    }

    private fun play(file: File): Boolean {
        stop()
        return try {
            player = MediaPlayer().apply {
                setDataSource(file.path)
                setOnCompletionListener { stop() }
                prepare()
                start()
            }
            playing = file
            ticker = scope.launch {
                while (isActive) {
                    player?.let { p ->
                        progress = runCatching { p.currentPosition.toFloat() / p.duration.coerceAtLeast(1) }
                            .getOrDefault(0f)
                    }
                    delay(40)
                }
            }
            true
        } catch (e: Exception) {
            stop()
            false
        }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playing = null
        progress = 0f
    }
}
