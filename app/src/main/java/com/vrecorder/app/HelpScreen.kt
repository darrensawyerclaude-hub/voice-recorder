package com.vrecorder.app

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class HelpSection(val title: String, val points: List<String>)

private fun sections(folder: String) = listOf(
    HelpSection(
        "Recording",
        listOf(
            "Tap Record. The bar under the timer moves with your voice, so you can see the mic is hearing you.",
            "Pause and Resume as often as you like. It all goes into one file.",
            "If a call comes in, recording pauses and then carries on by itself after the call.",
            "Tap Stop, then type a name or tap Keep default.",
            "Recording carries on with the screen off or while you use other apps. Pause and Stop are also in the notification, including on the lock screen.",
            "Quiet speech is raised automatically and loud sounds are held back, so recordings play at a normal volume everywhere.",
        ),
    ),
    HelpSection(
        "Your files",
        listOf(
            "Recordings are saved to $folder on your phone, as ordinary .aac files that play in any app.",
            "Tap Open folder to see them in your file manager.",
            "They stay on your phone even if you uninstall this app.",
            "If the phone dies or the app is closed mid-recording, everything up to that moment is kept.",
        ),
    ),
    HelpSection(
        "Managing recordings",
        listOf(
            "Tap a recording to show Play, Rename, Share, Open with and Delete. Tap it again to hide them.",
            "Share sends the file to WhatsApp, email and so on. Open with plays or edits it in another app.",
            "Use Search to find a recording by name, and Sort to change the order.",
        ),
    ),
    HelpSection(
        "Stop your phone cutting recordings short",
        listOf(
            "Some phones close background apps to save battery. To stop that:",
            "Samsung: Settings → Battery and device care → Battery → Background usage limits → Never sleeping apps → add Recorder.",
            "Other phones: Settings → Apps → Recorder → Battery → Unrestricted.",
        ),
    ),
    HelpSection(
        "Start from the lock screen (Samsung)",
        listOf(
            "Settings → Lock screen → Shortcuts, then choose Recorder for one corner. Swipe it like the camera. You'll need to unlock before the app opens.",
        ),
    ),
    HelpSection(
        "Something not working?",
        listOf(
            "Record does nothing: allow the microphone in Settings → Apps → Recorder → Permissions → Microphone.",
            "No notification while recording: allow notifications for Recorder in the same place.",
            "Playback is quiet: check the phone's media volume (press volume up while a recording plays).",
            "Phone calls: recording pauses by itself when a call rings or starts, including WhatsApp and other internet calls, and carries on when the call ends. The call itself isn't recorded. Tap Resume if you want to carry on during the call. Android may silence the mic until the call ends.",
        ),
    ),
)

@Composable
fun HelpScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Help", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            for (section in sections(Recordings.relativePath.trimEnd('/'))) {
                Text(
                    section.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                for (point in section.points) {
                    Text(point, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    )
                },
                modifier = Modifier.padding(top = 20.dp),
            ) { Text("Open Recorder's settings") }
            Text(
                "Version ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
