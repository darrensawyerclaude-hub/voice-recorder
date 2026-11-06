package com.vrecorder.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bookmark(val id: Long, val positionMs: Long, val label: String)

/**
 * Bookmarks live in the app's own storage, keyed by the recording's MediaStore ID, because an
 * ADTS .aac file has nowhere to hold them. The ID survives renames; it doesn't survive the file
 * being copied elsewhere, so bookmarks don't travel with shared or exported files.
 */
object Bookmarks {
    private const val FILE = "bookmarks.json"
    private val lock = Any()

    private fun key(uri: Uri) = uri.lastPathSegment ?: uri.toString()

    private fun load(context: Context): JSONObject {
        val f = File(context.filesDir, FILE)
        return if (f.exists()) runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject()) else JSONObject()
    }

    private fun save(context: Context, root: JSONObject) {
        // Write then rename, so a crash mid-write can't leave a half-written file.
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(root.toString())
        tmp.renameTo(File(context.filesDir, FILE))
    }

    fun get(context: Context, uri: Uri): List<Bookmark> = synchronized(lock) {
        val arr = load(context).optJSONArray(key(uri)) ?: return emptyList()
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Bookmark(o.getLong("id"), o.getLong("pos"), o.optString("label"))
        }.sortedBy { it.positionMs }
    }

    fun counts(context: Context): Map<String, Int> = synchronized(lock) {
        val root = load(context)
        root.keys().asSequence().associateWith { root.getJSONArray(it).length() }
    }

    fun countFor(counts: Map<String, Int>, uri: Uri) = counts[key(uri)] ?: 0

    fun set(context: Context, uri: Uri, list: List<Bookmark>) = synchronized(lock) {
        val root = load(context)
        if (list.isEmpty()) {
            root.remove(key(uri))
        } else {
            root.put(key(uri), JSONArray(list.sortedBy { it.positionMs }.map {
                JSONObject().put("id", it.id).put("pos", it.positionMs).put("label", it.label)
            }))
        }
        save(context, root)
    }

    /** Adds a bookmark with a default "Bookmark N" label and returns it. */
    fun add(context: Context, uri: Uri, positionMs: Long): Bookmark = synchronized(lock) {
        val list = get(context, uri)
        val b = Bookmark(System.nanoTime(), positionMs, "Bookmark ${list.size + 1}")
        set(context, uri, list + b)
        b
    }

    fun remove(context: Context, uri: Uri) = set(context, uri, emptyList())
}
