package com.jojings.ringtonemaker

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jojings.ringtonemaker.audio.AacEncoder
import com.jojings.ringtonemaker.audio.AudioDecoder
import com.jojings.ringtonemaker.audio.AudioException
import com.jojings.ringtonemaker.audio.DecodedAudio
import com.jojings.ringtonemaker.audio.JoinResult
import com.jojings.ringtonemaker.audio.Joiner
import com.jojings.ringtonemaker.audio.Pcm
import com.jojings.ringtonemaker.audio.PcmPreviewPlayer
import com.jojings.ringtonemaker.data.RingKind
import com.jojings.ringtonemaker.data.Tune
import com.jojings.ringtonemaker.data.TunePlayer
import com.jojings.ringtonemaker.data.TuneRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { Home, Editor, Join }

data class Busy(val label: String, val progress: Float? = null)

data class ToastMsg(val text: String, val id: Long = System.nanoTime())

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx: Application get() = getApplication()
    val repo = TuneRepository(app)

    var screen by mutableStateOf(Screen.Home)
        private set
    var tunes by mutableStateOf<List<Tune>>(emptyList())
        private set
    var busy by mutableStateOf<Busy?>(null)
        private set
    var toast by mutableStateOf<ToastMsg?>(null)
        private set
    /** Bumped every time something worth celebrating happens (confetti!). */
    var celebrate by mutableIntStateOf(0)
        private set

    val tunePlayer = TunePlayer(viewModelScope)
    val preview = PcmPreviewPlayer(viewModelScope)

    // ---- Editor state ----
    var source by mutableStateOf<DecodedAudio?>(null)
        private set
    var selStart by mutableLongStateOf(0L)
        private set
    var selEnd by mutableLongStateOf(0L)
        private set
    var fadeIn by mutableStateOf(false)
    var fadeOut by mutableStateOf(false)

    // ---- Join state ----
    val joinList = mutableStateListOf<Tune>()
    var joinGapMs by mutableLongStateOf(0L)
    private var joinCache: Pair<String, JoinResult>? = null

    private var work: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { tunes = withContext(Dispatchers.IO) { repo.list() } }
    }

    fun showToast(text: String) {
        toast = ToastMsg(text)
    }

    fun dismissToast(msg: ToastMsg) {
        if (toast == msg) toast = null
    }

    fun cancelWork() {
        work?.cancel()
    }

    fun goHome() {
        preview.stop()
        screen = Screen.Home
    }

    fun openJoin() {
        tunePlayer.stop()
        if (tunes.size < 2) {
            showToast("Save at least 2 tunes first, then join them!")
            return
        }
        screen = Screen.Join
    }

    // ------------------------------------------------------------------ Editor

    fun openFile(uri: Uri) {
        tunePlayer.stop()
        preview.stop()
        work?.cancel()
        work = viewModelScope.launch {
            busy = Busy("Extracting the music…", 0f)
            try {
                val name = displayName(uri)
                val out = File(ctx.cacheDir, "source.pcm")
                val res = AudioDecoder.decode(ctx, uri, out) { p -> busy = busy?.copy(progress = p) }
                if (Pcm.framesToMs(res.frames) < Pcm.MIN_TUNE_MS) {
                    throw AudioException("That sound is shorter than 1 second.")
                }
                val audio = DecodedAudio(out, res.frames, res.peaks, name)
                source = audio
                selStart = 0
                selEnd = minOf(audio.durationMs, 30_000L)
                fadeIn = false
                fadeOut = false
                screen = Screen.Editor
                if (res.truncated) showToast("Long file! Using the first 20 minutes.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudioException) {
                showToast(e.message ?: "Couldn't read that file.")
            } catch (e: Exception) {
                showToast("Oops! Couldn't read that file.")
            } finally {
                busy = null
            }
        }
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "My Tune"
        return name.substringBeforeLast('.').ifBlank { "My Tune" }
    }

    val selLength: Long get() = selEnd - selStart

    fun setStart(ms: Long) {
        val dur = source?.durationMs ?: return
        val lo = maxOf(0L, selEnd - Pcm.MAX_TUNE_MS)
        val hi = selEnd - Pcm.MIN_TUNE_MS
        selStart = ms.coerceIn(lo, maxOf(lo, hi)).coerceIn(0L, dur)
        restartPreviewIfPlaying()
    }

    fun setEnd(ms: Long) {
        val dur = source?.durationMs ?: return
        val lo = selStart + Pcm.MIN_TUNE_MS
        val hi = minOf(dur, selStart + Pcm.MAX_TUNE_MS)
        selEnd = ms.coerceIn(minOf(lo, hi), hi)
        restartPreviewIfPlaying()
    }

    /** Slides the whole selection so it starts at [ms], keeping its length. */
    fun moveSelection(ms: Long) {
        val dur = source?.durationMs ?: return
        val len = selLength
        val s = ms.coerceIn(0L, dur - len)
        selStart = s
        selEnd = s + len
        restartPreviewIfPlaying()
    }

    fun setLength(len: Long) {
        val dur = source?.durationMs ?: return
        val l = len.coerceIn(Pcm.MIN_TUNE_MS, minOf(Pcm.MAX_TUNE_MS, dur))
        var s = selStart
        if (s + l > dur) s = dur - l
        selStart = s
        selEnd = s + l
        restartPreviewIfPlaying()
    }

    private fun restartPreviewIfPlaying() {
        // Keep it simple: changing the cut stops the preview so what you hear always matches.
        if (preview.position.value >= 0) preview.stop()
    }

    fun togglePreview() {
        val src = source ?: return
        if (preview.position.value >= 0) {
            preview.stop()
            return
        }
        val total = Pcm.msToFrames(selLength)
        preview.play(
            src.pcmFile,
            Pcm.msToFrames(selStart),
            Pcm.msToFrames(selEnd),
            if (fadeIn) fadeFrames(total) else 0,
            if (fadeOut) fadeFrames(total) else 0,
        )
    }

    private fun fadeFrames(total: Long) = minOf(Pcm.msToFrames(2_000), total / 3)

    fun saveSelection(name: String) {
        val src = source ?: return
        preview.stop()
        work?.cancel()
        work = viewModelScope.launch {
            busy = Busy("Cooking your tune…", 0f)
            try {
                val out = repo.newFile(name)
                val start = Pcm.msToFrames(selStart)
                val end = Pcm.msToFrames(selEnd)
                val total = end - start
                AacEncoder.encode(
                    src.pcmFile, start, end, out,
                    if (fadeIn) fadeFrames(total) else 0,
                    if (fadeOut) fadeFrames(total) else 0,
                ) { p -> busy = busy?.copy(progress = p) }
                tunes = withContext(Dispatchers.IO) { repo.list() }
                celebrate++
                showToast("Saved “${out.nameWithoutExtension}”!")
                screen = Screen.Home
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Couldn't save the tune. Is the phone full?")
            } finally {
                busy = null
            }
        }
    }

    // ------------------------------------------------------------------ Tunes

    fun toggleTune(tune: Tune) {
        if (!tunePlayer.toggle(tune.file)) showToast("Couldn't play that tune.")
    }

    fun deleteTune(tune: Tune) {
        if (tunePlayer.playing == tune.file) tunePlayer.stop()
        repo.delete(tune)
        joinList.removeAll { it.file == tune.file }
        refresh()
        showToast("Deleted “${tune.name}”")
    }

    fun renameTune(tune: Tune, newName: String) {
        if (tunePlayer.playing == tune.file) tunePlayer.stop()
        if (repo.rename(tune, newName)) {
            joinList.clear()
            refresh()
            showToast("Renamed!")
        }
    }

    fun setAs(tune: Tune, kind: RingKind, makeDefault: Boolean) {
        viewModelScope.launch {
            try {
                val madeDefault = withContext(Dispatchers.IO) { repo.exportToSystem(tune, kind, makeDefault) }
                if (madeDefault) {
                    celebrate++
                    showToast("“${tune.name}” is now your ${kind.label.lowercase()}!")
                } else {
                    showToast("Added to your phone's ${kind.label.lowercase()} list!")
                }
            } catch (e: Exception) {
                showToast("Couldn't set the ${kind.label.lowercase()}.")
            }
        }
    }

    // ------------------------------------------------------------------ Join

    val joinTotalMs: Long
        get() = joinList.sumOf { it.durationMs } + joinGapMs * maxOf(0, joinList.size - 1)

    fun addToJoin(tune: Tune) {
        joinList.add(tune)
        preview.stop()
    }

    fun removeFromJoin(index: Int) {
        if (index in joinList.indices) joinList.removeAt(index)
        preview.stop()
    }

    fun moveInJoin(index: Int, delta: Int) {
        val to = index + delta
        if (index !in joinList.indices || to !in joinList.indices) return
        val t = joinList.removeAt(index)
        joinList.add(to, t)
        preview.stop()
    }

    private suspend fun buildJoin(): JoinResult {
        val key = joinList.joinToString("|") { "${it.file.path}:${it.file.lastModified()}" } + "#$joinGapMs"
        joinCache?.let { (k, r) -> if (k == key && r.pcmFile.exists()) return r }
        val res = Joiner.join(ctx, joinList.map { it.file }, joinGapMs, File(ctx.cacheDir, "joined.pcm")) { p ->
            busy = busy?.copy(progress = p)
        }
        joinCache = key to res
        return res
    }

    fun toggleJoinPreview() {
        if (preview.position.value >= 0) {
            preview.stop()
            return
        }
        if (joinList.isEmpty()) {
            showToast("Tap tunes below to add them first!")
            return
        }
        work?.cancel()
        work = viewModelScope.launch {
            busy = Busy("Mixing your tunes…", 0f)
            try {
                val res = buildJoin()
                busy = null
                preview.play(res.pcmFile, 0, res.frames, 0, if (res.truncated) Pcm.msToFrames(1500) else 0)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Couldn't mix those tunes.")
            } finally {
                busy = null
            }
        }
    }

    fun saveJoin(name: String) {
        if (joinList.size < 2) {
            showToast("Add at least 2 tunes to join!")
            return
        }
        preview.stop()
        work?.cancel()
        work = viewModelScope.launch {
            busy = Busy("Mixing your tunes…", 0f)
            try {
                val res = buildJoin()
                busy = Busy("Saving the mix…", 0f)
                val out = repo.newFile(name)
                AacEncoder.encode(
                    res.pcmFile, 0, res.frames, out,
                    fadeOutFrames = if (res.truncated) Pcm.msToFrames(1500) else 0,
                ) { p -> busy = busy?.copy(progress = p) }
                tunes = withContext(Dispatchers.IO) { repo.list() }
                joinList.clear()
                celebrate++
                showToast("Joined into “${out.nameWithoutExtension}”!")
                screen = Screen.Home
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Couldn't save the mix.")
            } finally {
                busy = null
            }
        }
    }

    override fun onCleared() {
        tunePlayer.stop()
        preview.stop()
    }
}
