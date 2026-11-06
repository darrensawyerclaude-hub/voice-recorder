package com.vrecorder.app

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.OutputStream

/**
 * Trims and cuts ADTS AAC files frame by frame. Every ADTS frame is self-contained, so keeping or
 * dropping whole frames edits the audio without re-encoding: no quality loss, and it's fast.
 * Precision is one frame (1024 samples, about 21 ms at 48 kHz).
 */
object AdtsEditor {

    enum class Mode { KEEP, CUT }

    private val sampleRates = intArrayOf(
        96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350,
    )

    /**
     * Writes [source] to [out], keeping only [startMs]..[endMs] (KEEP) or everything except it
     * (CUT). Returns the length of the result in ms.
     */
    fun edit(context: Context, source: Uri, out: OutputStream, startMs: Long, endMs: Long, mode: Mode): Long {
        val bytes = context.contentResolver.openInputStream(source)!!.use { it.readBytes() }
        var pos = 0
        var timeUs = 0.0
        var keptUs = 0.0
        while (pos + 7 <= bytes.size) {
            // Sync word 0xFFF; skip anything else (e.g. a torn last frame).
            if (bytes[pos].toInt() and 0xFF != 0xFF || bytes[pos + 1].toInt() and 0xF0 != 0xF0) {
                pos++
                continue
            }
            val freqIdx = (bytes[pos + 2].toInt() shr 2) and 0x0F
            val len = ((bytes[pos + 3].toInt() and 0x03) shl 11) or
                ((bytes[pos + 4].toInt() and 0xFF) shl 3) or
                ((bytes[pos + 5].toInt() and 0xE0) shr 5)
            val blocks = (bytes[pos + 6].toInt() and 0x03) + 1
            if (len < 7 || pos + len > bytes.size || freqIdx >= sampleRates.size) break
            val frameUs = blocks * 1024 * 1_000_000.0 / sampleRates[freqIdx]
            val midMs = (timeUs + frameUs / 2) / 1000
            val inRange = midMs >= startMs && midMs < endMs
            if (inRange == (mode == Mode.KEEP)) {
                out.write(bytes, pos, len)
                keptUs += frameUs
            }
            timeUs += frameUs
            pos += len
        }
        return (keptUs / 1000).toLong()
    }

    /** Moves bookmarks to where they land in the edited file; ones inside a cut are dropped. */
    fun remapBookmarks(list: List<Bookmark>, startMs: Long, endMs: Long, mode: Mode): List<Bookmark> =
        list.mapNotNull { b ->
            when (mode) {
                Mode.KEEP -> if (b.positionMs in startMs until endMs) b.copy(positionMs = b.positionMs - startMs) else null
                Mode.CUT -> when {
                    b.positionMs < startMs -> b
                    b.positionMs >= endMs -> b.copy(positionMs = b.positionMs - (endMs - startMs))
                    else -> null
                }
            }
        }

    /**
     * Applies an edit either as a new recording next to the original, or by replacing the original
     * (written to a temp file first, so a failure can't leave it half-written). Returns the URI of
     * the result.
     */
    fun apply(
        context: Context,
        source: Uri,
        sourceName: String,
        startMs: Long,
        endMs: Long,
        mode: Mode,
        asCopy: Boolean,
    ): Uri {
        val tmp = File.createTempFile("edit", ".aac", context.cacheDir)
        try {
            tmp.outputStream().use { edit(context, source, it, startMs, endMs, mode) }
            val target = if (asCopy) {
                val base = sourceName.removeSuffix(".aac")
                val suffix = if (mode == Mode.KEEP) "trimmed" else "cut"
                Recordings.create(context, "$base ($suffix)") ?: error("Couldn't create the copy")
            } else {
                source
            }
            context.contentResolver.openOutputStream(target, "wt")!!.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            }
            Bookmarks.set(context, target, remapBookmarks(Bookmarks.get(context, source), startMs, endMs, mode))
            return target
        } finally {
            tmp.delete()
        }
    }
}
