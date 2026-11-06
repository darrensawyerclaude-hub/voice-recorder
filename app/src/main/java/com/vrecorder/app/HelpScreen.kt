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
        "Bookmarks",
        listOf(
            "While recording, tap Bookmark (in the app, on the quick record screen, or in the notification) to mark an important moment. You don't need to unlock the phone or stop.",
            "When you play the recording, bookmarks show as red marks on the timeline and as a list. Tap one to jump there. Rename them (\"Budget question\") or delete them.",
            "You can also add a bookmark while listening back: tap Bookmark here.",
            "Bookmarks are kept by this app. They stay when you rename a recording, but don't travel with a shared or exported copy.",
        ),
    ),
    HelpSection(
        "Playing, trimming and cutting",
        listOf(
            "Tap a recording, then Play. Drag the timeline to move around, use −10 s / +10 s to skip, and tap 1× to change speed (up to 2×).",
            "Tap Trim / cut. Choose Keep only the selection (trim the ends) or Cut out the selection (remove a middle part), then drag the two handles.",
            "To cut exactly at a bookmark, tap the bookmark to move there, then Start here or End here. Preview plays the result before you save.",
            "Save as copy keeps the original and adds a \"(trimmed)\" or \"(cut)\" recording. Replace original changes it permanently. Editing doesn't re-encode, so there's no loss of quality.",
        ),
    ),
    HelpSection(
        "Transcripts",
        listOf(
            "Open a recording, tap Transcribe, and choose how.",
            "On this phone: Quick (about 92% of words right, very fast) or Accurate (about 95%, slower). The first time, the app downloads the model you pick (111 MB or 375 MB; use Wi-Fi). After that it works offline and nothing leaves your phone. English only, and speakers aren't named.",
            "It runs in the background with progress in a notification, so you can leave the app. Long recordings make the phone warm and use battery, so plug it in. Older phones can take several times longer.",
            "With Gemini: faster for long recordings, names speakers and adds a summary, but the recording is sent to Google under your account.",
            "In the transcript, tap a line to jump to that point. Bookmarks are shown in place. Search, Copy or Share it as a text file.",
            "The main Search box also finds words inside transcripts.",
            "Always check names and figures against the recording.",
            "To free space, use Remove downloaded models in the Transcribe box.",
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
            "Tap a recording to show Play, Rename, Share, Open with, Export and Delete. Tap it again to hide them. The row shows how many bookmarks it has.",
            "To work with several at once, press and hold one, then tap the others. You can then Share, Export or Delete them together.",
            "Export saves copies to any folder you pick (Downloads, an SD card, Google Drive, a USB stick). M4A opens on iPhones, Macs and Windows as well as Android. WAV is about 10 times bigger but suits editing software. Your originals stay where they are.",
        ),
    ),
    HelpSection(
        "Getting recordings off your phone",
        listOf(
            "Email, WhatsApp, Drive: tap Share and pick the app. Recordings are sent as M4A so the other person can open them on any device.",
            "Long recordings: email services often refuse attachments over 20–25 MB (about 20 minutes of recording). Share will offer a smaller copy, about a quarter of a megabyte a minute, which is clear for speech.",
            "To a computer: plug the phone in with a USB cable and open Recordings/VoiceRecorder. Or use Quick Share to a nearby Windows PC.",
            "Lots of files at once: press and hold one recording, tap the others, then Share or Export.",
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
        "Start recording in one tap",
        listOf(
            "Quick settings button: swipe down twice from the top of the screen, tap the pencil (or ⋮ → Edit buttons), and drag Record into your buttons. Tap it to start recording from anywhere, even the lock screen. Tap it again to stop.",
            "Home-screen widget: press and hold an empty space on your home screen, tap Widgets, find Recorder and drag it on. Tap it to record, and again to stop.",
            "Both open a simple recording screen that works without unlocking. It only has Pause and Stop, so nobody can play your recordings from a locked phone. You name the recording next time you open the app.",
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
