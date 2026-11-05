package com.vrecorder.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Recording(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
)

/**
 * Recordings live in shared storage (Recordings/VoiceRecorder on Android 12+,
 * Music/VoiceRecorder on 10-11) so they show up in file managers and survive an uninstall.
 */
object Recordings {
    private const val FOLDER = "VoiceRecorder"
    private const val MIME = "audio/aac"

    val relativePath: String
        get() {
            val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Environment.DIRECTORY_RECORDINGS
            } else {
                Environment.DIRECTORY_MUSIC
            }
            return "$base/$FOLDER/"
        }

    private val collection: Uri
        get() = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

    /**
     * Creates the file up front and visible (not IS_PENDING), so a crash mid-recording
     * leaves a playable partial file rather than a hidden one that the system purges.
     */
    fun create(context: Context): Uri? {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.UK).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Recording_$stamp.aac")
            put(MediaStore.Audio.Media.MIME_TYPE, MIME)
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                put(MediaStore.Audio.Media.IS_RECORDING, 1)
            }
        }
        return context.contentResolver.insert(collection, values)
    }

    fun list(context: Context): List<Recording> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        val result = mutableListOf<Recording>()
        context.contentResolver.query(
            collection, projection, selection, arrayOf(relativePath),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            while (c.moveToNext()) {
                result += Recording(
                    uri = Uri.withAppendedPath(collection, c.getLong(idCol).toString()),
                    name = c.getString(nameCol),
                    sizeBytes = c.getLong(sizeCol),
                    dateAddedSec = c.getLong(dateCol),
                )
            }
        }
        return result
    }

    fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }

    /** Renames the file on disk. Returns false if the name is taken or invalid. */
    fun rename(context: Context, uri: Uri, newName: String): Boolean {
        val clean = newName.trim()
            .removeSuffix(".aac")
            .replace(Regex("""[\\/:*?"<>|]"""), "_")
            .trim()
        if (clean.isEmpty()) return false
        val values = ContentValues().apply { put(MediaStore.Audio.Media.DISPLAY_NAME, "$clean.aac") }
        return runCatching { context.contentResolver.update(uri, values, null, null) > 0 }.getOrDefault(false)
    }

    fun delete(context: Context, uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }
}
