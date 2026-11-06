package com.vrecorder.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri

/** An installed app that can take a shared audio file, e.g. Gemini. */
data class AiApp(val label: String, val packageName: String, val activity: String, val icon: Drawable?)

/**
 * Hands a recording to the user's own AI app for transcription. Nothing goes through us, so it
 * costs us nothing to run; the user's account with that company does the work.
 */
object AiHandoff {

    // Known AI assistants, in the order they're offered. Others that accept audio aren't listed:
    // the share sheet already covers them, and a list of every music app would bury these.
    private val known = listOf(
        "com.google.android.apps.bard",     // Gemini
        "com.openai.chatgpt",
        "com.anthropic.claude",
        "com.microsoft.copilot",
        "ai.perplexity.app.android",
    )

    fun installed(context: Context): List<AiApp> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_SEND).setType("audio/mp4")
        val found = pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName in known }
            .distinctBy { it.activityInfo.packageName }
            .map {
                AiApp(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    activity = it.activityInfo.name,
                    icon = runCatching { it.loadIcon(pm) }.getOrNull(),
                )
            }
        return found.sortedBy { known.indexOf(it.packageName) }
    }

    fun prompt(bookmarks: List<Bookmark>): String = buildString {
        append("Please transcribe this audio recording word for word, in British English spelling. ")
        append("Start each paragraph with a timestamp like [mm:ss], and label different speakers as Speaker 1, Speaker 2 and so on. ")
        append("After the transcript, add a short summary and a list of any action points.")
        if (bookmarks.isNotEmpty()) {
            append("\n\nI bookmarked these moments while recording; please mark them in the transcript:\n")
            bookmarks.forEach { append("${formatClock(it.positionMs)} ${it.label}\n") }
        }
    }

    /**
     * Sends [file] to [app] (or the system share sheet when [app] is null) with [prompt] attached.
     * The prompt is also copied, because some apps ignore text sent alongside a file.
     */
    fun send(context: Context, file: Uri, prompt: String, app: AiApp?) {
        context.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Transcription prompt", prompt))
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/mp4")
            .putExtra(Intent.EXTRA_STREAM, file)
            .putExtra(Intent.EXTRA_TEXT, prompt)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, file)
        if (app != null) {
            send.component = ComponentName(app.packageName, app.activity)
            context.startActivity(send)
        } else {
            context.startActivity(Intent.createChooser(send, "Transcribe with…"))
        }
    }
}
