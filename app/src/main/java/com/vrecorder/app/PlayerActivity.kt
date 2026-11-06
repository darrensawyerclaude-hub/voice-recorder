package com.vrecorder.app

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Full-screen player with a timeline, skip, speed, bookmarks, and trim/cut. */
class PlayerActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_NAME = "name"
        private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f, 0.75f)
        private const val SKIP_MS = 10_000
        // Lead-in/out around a cut when previewing it.
        private const val PREVIEW_MARGIN_MS = 3_000L

        fun intent(context: Context, rec: Recording): Intent =
            Intent(context, PlayerActivity::class.java).setData(rec.uri).putExtra(EXTRA_NAME, rec.name)
    }

    private lateinit var uri: Uri
    private lateinit var name: String
    private var player: MediaPlayer? = null

    private var durationMs by mutableLongStateOf(0L)
    private var positionMs by mutableLongStateOf(0L)
    private var playing by mutableStateOf(false)
    private var speedIndex by mutableStateOf(0)
    private var bookmarks by mutableStateOf(emptyList<Bookmark>())
    private var renaming by mutableStateOf<Bookmark?>(null)

    // Transcribe choice sheet.
    private var transcribing by mutableStateOf(false)
    private var transcript by mutableStateOf<Transcript?>(null)
    private var showTranscript by mutableStateOf(false)
    private var transcriptQuery by mutableStateOf("")
    private var confirmDeleteTranscript by mutableStateOf(false)
    private var aiApps by mutableStateOf(emptyList<AiApp>())
    private var preparing by mutableStateOf(false)

    // Trim/cut mode.
    private var editing by mutableStateOf(false)
    private var editMode by mutableStateOf(AdtsEditor.Mode.KEEP)
    private var range by mutableStateOf(0f..1f)
    private var confirmReplace by mutableStateOf(false)
    private var saving by mutableStateOf(false)
    // Preview of the edit: stop at (KEEP) or jump over (CUT) the selection.
    private var previewing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uri = intent.data ?: return finish()
        name = intent.getStringExtra(EXTRA_NAME) ?: "Recording"
        bookmarks = Bookmarks.get(this, uri)
        transcript = Transcripts.get(this, uri)
        showTranscript = transcript != null
        if (!prepare()) return
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val job by TranscriptionService.job.collectAsStateWithLifecycle()
                    // Pick up the transcript as soon as a job for this recording finishes.
                    LaunchedEffect(job?.phase) {
                        if (job?.uri == uri && job?.phase == TranscribeJob.Phase.DONE) {
                            transcript = Transcripts.get(this@PlayerActivity, uri)
                            showTranscript = true
                        }
                    }
                    LaunchedEffect(Unit) {
                        while (true) {
                            player?.let { if (playing) positionMs = it.currentPosition.toLong() }
                            previewTick()
                            delay(100)
                        }
                    }
                    PlayerScreen(job?.takeIf { it.uri == uri })
                    renaming?.let { RenameDialog(it) }
                    if (confirmReplace) ReplaceDialog()
                    if (transcribing) TranscribeDialog()
                    if (confirmDeleteTranscript) DeleteTranscriptDialog()
                }
            }
        }
    }

    private fun prepare(): Boolean {
        val p = MediaPlayer()
        return runCatching {
            p.setDataSource(this, uri)
            p.prepare()
            p.setOnCompletionListener {
                playing = false
                previewing = false
                positionMs = durationMs
            }
            player = p
            durationMs = p.duration.toLong().coerceAtLeast(1)
            range = 0f..durationMs.toFloat()
            true
        }.getOrElse {
            p.release()
            Toast.makeText(this, "Couldn't open this recording.", Toast.LENGTH_LONG).show()
            finish()
            false
        }
    }

    override fun onPause() {
        super.onPause()
        pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun play() {
        val p = player ?: return
        if (positionMs >= durationMs - 50) seekTo(0)
        runCatching { p.playbackParams = p.playbackParams.setSpeed(SPEEDS[speedIndex]) }
        p.start()
        playing = true
    }

    private fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        playing = false
        previewing = false
    }

    private fun seekTo(ms: Long) {
        val target = ms.coerceIn(0, durationMs)
        player?.seekTo(target, MediaPlayer.SEEK_CLOSEST)
        positionMs = target
    }

    private fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.size
        val p = player ?: return
        // Setting params on a paused MediaPlayer starts it, so only apply while playing.
        if (playing) runCatching { p.playbackParams = p.playbackParams.setSpeed(SPEEDS[speedIndex]) }
    }

    private fun addBookmark() {
        Bookmarks.add(this, uri, positionMs)
        bookmarks = Bookmarks.get(this, uri)
    }

    private fun startPreview() {
        val start = range.start.toLong()
        seekTo(if (editMode == AdtsEditor.Mode.KEEP) start else (start - PREVIEW_MARGIN_MS).coerceAtLeast(0))
        previewing = true
        play()
    }

    private fun previewTick() {
        if (!previewing || !playing) return
        val start = range.start.toLong()
        val end = range.endInclusive.toLong()
        when (editMode) {
            AdtsEditor.Mode.KEEP -> if (positionMs >= end) pause()
            AdtsEditor.Mode.CUT -> when {
                positionMs in start until end -> seekTo(end)
                positionMs >= end + PREVIEW_MARGIN_MS -> pause()
            }
        }
    }

    private fun save(asCopy: Boolean) {
        confirmReplace = false
        pause()
        saving = true
        val start = range.start.toLong()
        val end = range.endInclusive.toLong()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { AdtsEditor.apply(this@PlayerActivity, uri, name, start, end, editMode, asCopy) }.isSuccess
            }
            saving = false
            if (!ok) {
                Toast.makeText(this@PlayerActivity, "Couldn't save the edit. The original is unchanged.", Toast.LENGTH_LONG).show()
                return@launch
            }
            val what = if (editMode == AdtsEditor.Mode.KEEP) "Trimmed" else "Cut"
            Toast.makeText(
                this@PlayerActivity,
                if (asCopy) "$what copy saved next to the original" else "$what. The original has been replaced.",
                Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    @Composable
    private fun PlayerScreen(job: TranscribeJob?) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name.removeSuffix(".aac"),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { finish() }) { Text("Close") }
            }

            Timeline()
            Row(Modifier.fillMaxWidth()) {
                Text(formatClock(positionMs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(formatClock(durationMs), style = MaterialTheme.typography.bodySmall)
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { seekTo(positionMs - SKIP_MS) }) { Text("−10 s") }
                Button(onClick = { if (playing) pause() else play() }, modifier = Modifier.width(110.dp)) {
                    Text(if (playing) "Pause" else "Play")
                }
                TextButton(onClick = { seekTo(positionMs + SKIP_MS) }) { Text("+10 s") }
                TextButton(onClick = ::cycleSpeed) { Text(speedLabel(SPEEDS[speedIndex])) }
            }

            if (editing) EditPanel() else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = ::addBookmark) { Text("Bookmark here") }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = {
                        pause()
                        aiApps = AiHandoff.installed(this@PlayerActivity)
                        transcribing = true
                    }) { Text("Transcribe") }
                    TextButton(onClick = {
                        pause()
                        editing = true
                    }) { Text("Trim / cut") }
                }
            }

            job?.let { JobProgress(it) }
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabLabel("Bookmarks (${bookmarks.size})", !showTranscript) { showTranscript = false }
                TabLabel(if (transcript != null) "Transcript" else "Transcript (none yet)", showTranscript) { showTranscript = true }
            }
            if (showTranscript) TranscriptPanel() else {
                if (bookmarks.isEmpty()) {
                    Text(
                        "No bookmarks yet. Tap Bookmark while recording or playing.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(bookmarks, key = { it.id }) { b -> BookmarkRow(b) }
                }
            }
        }
    }

    @Composable
    private fun TabLabel(text: String, selected: Boolean, onClick: () -> Unit) {
        TextButton(onClick = onClick) {
            Text(
                text,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    @Composable
    private fun JobProgress(j: TranscribeJob) {
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            when (j.phase) {
                TranscribeJob.Phase.DOWNLOADING, TranscribeJob.Phase.TRANSCRIBING -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (j.phase == TranscribeJob.Phase.DOWNLOADING) "Downloading the ${j.model.label} model… "
                            else "Transcribing on this phone… ") + "${(j.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { TranscriptionService.cancel(this@PlayerActivity) }) { Text("Cancel") }
                    }
                    LinearProgressIndicator(progress = { j.progress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "You can leave this screen; it carries on in the background.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TranscribeJob.Phase.FAILED -> Text(
                    "Transcription failed: ${j.error}. Check your connection if the model was downloading, then try again.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> {}
            }
        }
    }

    @Composable
    private fun TranscriptPanel() {
        val t = transcript
        if (t == null) {
            Text(
                "No transcript yet. Tap Transcribe to make one on this phone or with Gemini.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            return
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = transcriptQuery,
                onValueChange = { transcriptQuery = it },
                placeholder = { Text("Search transcript") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row {
            TextButton(onClick = ::shareTranscript) { Text("Share") }
            TextButton(onClick = ::copyTranscript) { Text("Copy") }
            TextButton(onClick = { confirmDeleteTranscript = true }) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
        val q = transcriptQuery.trim()
        val shown = if (q.isEmpty()) t.segments else t.segments.filter { it.text.contains(q, ignoreCase = true) }
        if (t.segments.isEmpty()) {
            Text("No speech was found in this recording.", style = MaterialTheme.typography.bodySmall)
        } else if (shown.isEmpty()) {
            Text("Nothing matches \"$q\".", style = MaterialTheme.typography.bodySmall)
        }
        val playingColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        val markColor = MaterialTheme.colorScheme.error
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.startMs }) { s ->
                val current = positionMs in s.startMs until s.endMs
                val marks = bookmarks.filter { it.positionMs in s.startMs until s.endMs }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (current) Modifier.background(playingColor) else Modifier)
                        .clickable { seekTo(s.startMs) }
                        .padding(vertical = 6.dp, horizontal = 4.dp)
                ) {
                    marks.forEach {
                        Text("🔖 ${it.label}", color = markColor, style = MaterialTheme.typography.bodySmall)
                    }
                    Row {
                        Text(formatClock(s.startMs), style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(52.dp))
                        Text(s.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    private fun transcriptText() = transcript?.toPlainText(name.removeSuffix(".aac"), bookmarks) ?: ""

    private fun shareTranscript() {
        val dir = java.io.File(cacheDir, "share").apply { mkdirs() }
        val f = java.io.File(dir, name.removeSuffix(".aac") + " (transcript).txt")
        f.writeText(transcriptText())
        val fileUri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_STREAM, fileUri)
            .putExtra(Intent.EXTRA_SUBJECT, "Transcript: ${name.removeSuffix(".aac")}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = android.content.ClipData.newRawUri(null, fileUri)
        startActivity(Intent.createChooser(send, "Share transcript"))
    }

    private fun copyTranscript() {
        getSystemService(android.content.ClipboardManager::class.java)
            .setPrimaryClip(android.content.ClipData.newPlainText("Transcript", transcriptText()))
        Toast.makeText(this, "Transcript copied", Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun DeleteTranscriptDialog() {
        AlertDialog(
            onDismissRequest = { confirmDeleteTranscript = false },
            title = { Text("Delete transcript?") },
            text = { Text("The recording is kept. You can transcribe it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    Transcripts.delete(this@PlayerActivity, uri)
                    transcript = null
                    confirmDeleteTranscript = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteTranscript = false }) { Text("Cancel") } },
        )
    }

    @Composable
    private fun Timeline() {
        val primary = MaterialTheme.colorScheme.primary
        val error = MaterialTheme.colorScheme.error
        // While dragging, show the drag position; seek once on release.
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 10.dp)) {
                if (editing) {
                    val x0 = size.width * range.start / durationMs
                    val x1 = size.width * range.endInclusive / durationMs
                    drawRect(
                        color = if (editMode == AdtsEditor.Mode.KEEP) primary.copy(alpha = 0.15f) else error.copy(alpha = 0.2f),
                        topLeft = Offset(x0, 0f),
                        size = androidx.compose.ui.geometry.Size(x1 - x0, size.height),
                    )
                }
                for (b in bookmarks) {
                    val x = size.width * b.positionMs / durationMs
                    drawLine(error, Offset(x, 4f), Offset(x, size.height * 0.35f), strokeWidth = 5f)
                }
            }
            Slider(
                value = if (dragging) dragValue else positionMs.toFloat(),
                onValueChange = {
                    dragging = true
                    dragValue = it
                },
                onValueChangeFinished = {
                    seekTo(dragValue.toLong())
                    dragging = false
                },
                valueRange = 0f..durationMs.toFloat(),
            )
        }
    }

    @Composable
    private fun EditPanel() {
        Column(Modifier.fillMaxWidth()) {
            Text("Trim or cut", style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = editMode == AdtsEditor.Mode.KEEP, onClick = { editMode = AdtsEditor.Mode.KEEP })
                Text("Keep only the selection", Modifier.clickable { editMode = AdtsEditor.Mode.KEEP })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = editMode == AdtsEditor.Mode.CUT, onClick = { editMode = AdtsEditor.Mode.CUT })
                Text("Cut out the selection", Modifier.clickable { editMode = AdtsEditor.Mode.CUT })
            }
            RangeSlider(
                value = range,
                onValueChange = { range = it },
                valueRange = 0f..durationMs.toFloat(),
            )
            Text(
                "${formatClock(range.start.toLong())} to ${formatClock(range.endInclusive.toLong())} " +
                    "(${formatClock((range.endInclusive - range.start).toLong())} selected)",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Tip: tap a bookmark to move the playhead there, then use Start here / End here.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row {
                TextButton(onClick = {
                    range = positionMs.toFloat().coerceAtMost(range.endInclusive)..range.endInclusive
                }) { Text("Start here") }
                TextButton(onClick = {
                    range = range.start..positionMs.toFloat().coerceAtLeast(range.start)
                }) { Text("End here") }
                TextButton(onClick = ::startPreview) { Text("Preview") }
            }
            val nothingSelected = range.endInclusive - range.start < 100f
            val everything = range.start < 100f && range.endInclusive > durationMs - 100f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { save(asCopy = true) }, enabled = !saving && !nothingSelected && !everything) {
                    Text("Save as copy")
                }
                TextButton(onClick = { confirmReplace = true }, enabled = !saving && !nothingSelected && !everything) {
                    Text("Replace original")
                }
                TextButton(onClick = {
                    pause()
                    editing = false
                    range = 0f..durationMs.toFloat()
                }) { Text("Cancel") }
            }
            if (saving) Text("Saving…", style = MaterialTheme.typography.bodySmall)
        }
    }

    @Composable
    private fun BookmarkRow(b: Bookmark) {
        Row(
            Modifier.fillMaxWidth().clickable { seekTo(b.positionMs) }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatClock(b.positionMs), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
            Text(b.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { renaming = b }) { Text("Rename") }
            TextButton(onClick = {
                Bookmarks.set(this@PlayerActivity, uri, bookmarks - b)
                bookmarks = Bookmarks.get(this@PlayerActivity, uri)
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
    }

    @Composable
    private fun RenameDialog(b: Bookmark) {
        var text by remember(b) { mutableStateOf(b.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename bookmark") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val label = text.trim().ifEmpty { b.label }
                    Bookmarks.set(this@PlayerActivity, uri, bookmarks.map { if (it.id == b.id) it.copy(label = label) else it })
                    bookmarks = Bookmarks.get(this@PlayerActivity, uri)
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }

    private fun handOff(app: AiApp?) {
        preparing = true
        val rec = Recording(uri, name, 0, 0)
        lifecycleScope.launch {
            val file = runCatching {
                Exporter.prepareForSharing(this@PlayerActivity, listOf(rec), small = false) { _, _ -> }.first()
            }.getOrNull()
            preparing = false
            transcribing = false
            if (file == null) {
                Toast.makeText(this@PlayerActivity, "Couldn't prepare the recording.", Toast.LENGTH_LONG).show()
                return@launch
            }
            runCatching { AiHandoff.send(this@PlayerActivity, file, "audio/mp4", AiHandoff.prompt(bookmarks), app) }
                .onSuccess {
                    Toast.makeText(
                        this@PlayerActivity,
                        "Instructions copied. If they aren't already in the message, paste them in.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                .onFailure { Toast.makeText(this@PlayerActivity, "Couldn't open that app.", Toast.LENGTH_LONG).show() }
        }
    }

    @Composable
    private fun TranscribeDialog() {
        AlertDialog(
            onDismissRequest = { if (!preparing) transcribing = false },
            title = { Text("Transcribe") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("On this phone (private)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Nothing leaves your phone. English only, and it doesn't name speakers. It works hard: the phone " +
                            "gets warm and uses battery, so for long recordings plug it in. Older or cheaper phones " +
                            "can take several times longer. Check names and figures against the recording.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val busy = TranscriptionService.job.value?.running == true
                    SpeechModel.entries.forEach { m ->
                        val installed = SpeechModels.isInstalled(this@PlayerActivity, m)
                        val estimate = formatEstimate((durationMs / m.speedOnTestPhone).toLong())
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !busy) {
                                    TranscriptionService.start(this@PlayerActivity, Recording(uri, name, 0, 0), m, durationMs)
                                    transcribing = false
                                    showTranscript = true
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text("${m.label}: ${m.summary}", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (if (installed) "Downloaded. " else "One-off download of ${Formatter.formatShortFileSize(this@PlayerActivity, m.downloadBytes)} (Wi-Fi recommended). ") +
                                    "About $estimate for this recording on a fast phone.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    if (busy) Text("A transcription is already running.", style = MaterialTheme.typography.bodySmall)
                    val downloaded = SpeechModel.entries.filter { SpeechModels.isInstalled(this@PlayerActivity, it) }
                    if (downloaded.isNotEmpty() && !busy) {
                        TextButton(onClick = {
                            downloaded.forEach { SpeechModels.delete(this@PlayerActivity, it) }
                            transcribing = false
                            Toast.makeText(this@PlayerActivity, "Downloaded models removed", Toast.LENGTH_SHORT).show()
                        }) { Text("Remove downloaded models to free space") }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("With an AI app", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Fast and accurate, with speakers, a summary and action points. The recording is sent to that " +
                            "company under your own account, so check everyone recorded is happy with that. Very long " +
                            "recordings may be over the app's limit.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (aiApps.isEmpty()) {
                        Text(
                            "Gemini isn't installed. Install it from the Play Store (it works with your Google account), or try another app below.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    aiApps.forEach { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !preparing) { handOff(app) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            app.icon?.let {
                                Image(
                                    bitmap = it.toBitmap(96, 96).asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    TextButton(onClick = { handOff(null) }, enabled = !preparing) { Text("Other app…") }
                    if (preparing) Text("Preparing the recording…", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { transcribing = false }, enabled = !preparing) { Text("Cancel") } },
        )
    }

    @Composable
    private fun ReplaceDialog() {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace the original?") },
            text = {
                Text(
                    if (editMode == AdtsEditor.Mode.KEEP) "Everything outside the selection will be permanently removed from \"${name.removeSuffix(".aac")}\"."
                    else "The selected part will be permanently removed from \"${name.removeSuffix(".aac")}\"."
                )
            },
            confirmButton = {
                TextButton(onClick = { save(asCopy = false) }) { Text("Replace", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

private fun formatEstimate(ms: Long): String {
    val s = ms / 1000
    return when {
        s < 60 -> "${s.coerceAtLeast(5)} seconds"
        s < 3600 -> "${(s + 30) / 60} minute${if ((s + 30) / 60 == 1L) "" else "s"}"
        else -> "%.1f hours".format(s / 3600f)
    }
}

private fun speedLabel(s: Float) = "${if (s == s.toInt().toFloat()) s.toInt().toString() else s.toString()}×"
