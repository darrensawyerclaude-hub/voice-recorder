package com.vrecorder.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TranscriptSegment(val startMs: Long, val endMs: Long, val text: String)

data class Transcript(val model: String, val createdAt: Long, val segments: List<TranscriptSegment>) {
    val text get() = segments.joinToString(" ") { it.text }

    /** Plain text with timestamps and bookmarks, for sharing or saving. */
    fun toPlainText(title: String, bookmarks: List<Bookmark>): String = buildString {
        appendLine(title)
        appendLine("Transcribed on the phone (${model.lowercase()} model). Check important details against the recording.")
        appendLine()
        val marks = bookmarks.sortedBy { it.positionMs }.toMutableList()
        for (s in segments) {
            while (marks.isNotEmpty() && marks.first().positionMs < s.endMs) {
                val b = marks.removeAt(0)
                appendLine("— Bookmark ${formatClock(b.positionMs)}: ${b.label} —")
            }
            appendLine("[${formatClock(s.startMs)}] ${s.text}")
        }
        marks.forEach { appendLine("— Bookmark ${formatClock(it.positionMs)}: ${it.label} —") }
    }
}

/** Transcripts live in the app's own storage, keyed by the recording's MediaStore ID like bookmarks. */
object Transcripts {
    private fun dir(context: Context) = File(context.filesDir, "transcripts").apply { mkdirs() }
    private fun file(context: Context, uri: Uri) = File(dir(context), "${uri.lastPathSegment}.json")

    fun get(context: Context, uri: Uri): Transcript? {
        val f = file(context, uri)
        if (!f.exists()) return null
        return runCatching {
            val o = JSONObject(f.readText())
            val arr = o.getJSONArray("segments")
            Transcript(
                o.getString("model"),
                o.getLong("createdAt"),
                (0 until arr.length()).map {
                    val s = arr.getJSONObject(it)
                    TranscriptSegment(s.getLong("s"), s.getLong("e"), s.getString("t"))
                },
            )
        }.getOrNull()
    }

    fun save(context: Context, uri: Uri, t: Transcript) {
        val o = JSONObject()
            .put("model", t.model)
            .put("createdAt", t.createdAt)
            .put("segments", JSONArray(t.segments.map { JSONObject().put("s", it.startMs).put("e", it.endMs).put("t", it.text) }))
        val tmp = File(dir(context), "${uri.lastPathSegment}.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file(context, uri))
    }

    fun delete(context: Context, uri: Uri) {
        file(context, uri).delete()
    }

    /** Full text of every transcript by MediaStore ID, for searching the recordings list. */
    fun allText(context: Context): Map<String, String> =
        dir(context).listFiles { f -> f.name.endsWith(".json") }.orEmpty().associate { f ->
            val id = f.name.removeSuffix(".json")
            id to (runCatching {
                val arr = JSONObject(f.readText()).getJSONArray("segments")
                (0 until arr.length()).joinToString(" ") { arr.getJSONObject(it).getString("t") }
            }.getOrDefault(""))
        }
}
