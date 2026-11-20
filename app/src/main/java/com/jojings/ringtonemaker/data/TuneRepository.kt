package com.jojings.ringtonemaker.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

data class Tune(
    val file: File,
    val name: String,
    val durationMs: Long,
    val createdAt: Long,
)

enum class RingKind(val label: String, val ringtoneType: Int, val folder: String) {
    Ringtone("Ringtone", RingtoneManager.TYPE_RINGTONE, Environment.DIRECTORY_RINGTONES),
    Notification("Notification", RingtoneManager.TYPE_NOTIFICATION, Environment.DIRECTORY_NOTIFICATIONS),
    Alarm("Alarm", RingtoneManager.TYPE_ALARM, Environment.DIRECTORY_ALARMS),
}

/**
 * Tunes live in the app's private storage on the phone (the private `files/tunes` folder, as .m4a files).
 * They can also be copied into the phone's shared Ringtones / Notifications / Alarms folders.
 */
class TuneRepository(private val context: Context) {

    val dir: File = File(context.filesDir, "tunes").apply { mkdirs() }

    private val durationCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun list(): List<Tune> =
        dir.listFiles { f -> f.isFile && f.extension.equals("m4a", ignoreCase = true) }
            .orEmpty()
            .map { Tune(it, it.nameWithoutExtension, duration(it), it.lastModified()) }
            .sortedByDescending { it.createdAt }

    private fun duration(f: File): Long {
        val key = "${f.path}:${f.lastModified()}:${f.length()}"
        durationCache[key]?.let { return it }
        val d = runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(f.path)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            } finally {
                r.release()
            }
        }.getOrDefault(0L)
        durationCache[key] = d
        return d
    }

    fun newFile(name: String): File {
        val base = sanitize(name).ifBlank { "My Tune" }
        var f = File(dir, "$base.m4a")
        var i = 2
        while (f.exists()) {
            f = File(dir, "$base ($i).m4a")
            i++
        }
        return f
    }

    fun rename(tune: Tune, newName: String): Boolean {
        val clean = sanitize(newName)
        if (clean.isBlank() || clean == tune.name) return false
        val target = newFile(clean)
        val ok = tune.file.renameTo(target)
        if (ok) target.setLastModified(tune.createdAt)
        return ok
    }

    fun delete(tune: Tune): Boolean = tune.file.delete()

    fun canSetDefaults(): Boolean = Settings.System.canWrite(context)

    /**
     * Copies the tune into the phone's shared Ringtones/Notifications/Alarms folder so it shows up
     * in the system sound pickers. If [makeDefault] and the app is allowed to modify system settings,
     * it also becomes the phone's default sound of that kind.
     */
    fun exportToSystem(tune: Tune, kind: RingKind, makeDefault: Boolean): Boolean {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportScoped(tune, kind) else exportLegacy(tune, kind)
        if (makeDefault && canSetDefaults()) {
            RingtoneManager.setActualDefaultRingtoneUri(context, kind.ringtoneType, uri)
            return true
        }
        return false
    }

    /** Android 9 and older: copy into /sdcard/Ringtones (etc.) and register it with the media store. */
    @Suppress("DEPRECATION")
    private fun exportLegacy(tune: Tune, kind: RingKind): Uri {
        val folder = Environment.getExternalStoragePublicDirectory(kind.folder).apply { mkdirs() }
        var target = File(folder, "${tune.name}.m4a")
        var i = 2
        while (target.exists()) {
            target = File(folder, "${tune.name} ($i).m4a")
            i++
        }
        tune.file.copyTo(target)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, target.absolutePath)
            put(MediaStore.Audio.Media.TITLE, target.nameWithoutExtension)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_RINGTONE, kind == RingKind.Ringtone)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, kind == RingKind.Notification)
            put(MediaStore.Audio.Media.IS_ALARM, kind == RingKind.Alarm)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
        }
        return context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: run {
                target.delete()
                error("Couldn't add to the phone's sounds")
            }
    }

    private fun exportScoped(tune: Tune, kind: RingKind): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "${tune.name}.m4a")
            put(MediaStore.Audio.Media.TITLE, tune.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, kind.folder)
            put(MediaStore.Audio.Media.IS_RINGTONE, kind == RingKind.Ringtone)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, kind == RingKind.Notification)
            put(MediaStore.Audio.Media.IS_ALARM, kind == RingKind.Alarm)
            put(MediaStore.Audio.Media.IS_MUSIC, false)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri: Uri = resolver.insert(collection, values) ?: error("Couldn't add to the phone's sounds")
        try {
            resolver.openOutputStream(uri)?.use { out -> tune.file.inputStream().use { it.copyTo(out) } }
                ?: error("Couldn't write the sound")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        return uri
    }

    fun shareIntent(tune: Tune): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", tune.file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, tune.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Share ${tune.name}")
    }

    companion object {
        fun sanitize(name: String): String =
            name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().trim('.').take(60)
    }
}
