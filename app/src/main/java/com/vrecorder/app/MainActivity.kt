package com.vrecorder.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/** A recording waiting to be named: either just finished, or picked from the list. */
private data class NameRequest(val uri: Uri, val currentName: String, val isNew: Boolean)

private enum class SortOrder(val label: String) {
    NEWEST("Newest first"),
    OLDEST("Oldest first"),
    NAME("Name A–Z"),
    // Size stands in for length: the encoder runs at a near-constant bitrate.
    LONGEST("Longest first");

    fun apply(list: List<Recording>): List<Recording> = when (this) {
        NEWEST -> list.sortedByDescending { it.dateAddedSec }
        OLDEST -> list.sortedBy { it.dateAddedSec }
        NAME -> list.sortedBy { it.name.lowercase() }
        LONGEST -> list.sortedByDescending { it.sizeBytes }
    }
}

class MainActivity : ComponentActivity() {

    private var recordings by mutableStateOf(emptyList<Recording>())
    private var playingUri by mutableStateOf<Uri?>(null)
    private var nameRequest by mutableStateOf<NameRequest?>(null)
    private var deleteRequest by mutableStateOf<Recording?>(null)
    private var expandedUri by mutableStateOf<Uri?>(null)
    private var query by mutableStateOf("")
    private var sortOrder by mutableStateOf(SortOrder.NEWEST)
    private var showHelp by mutableStateOf(false)
    private var player: MediaPlayer? = null

    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.RECORD_AUDIO] == true) {
                RecorderService.send(this, RecorderService.ACTION_START)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sortOrder = runCatching { SortOrder.valueOf(prefs.getString("sort", null)!!) }.getOrDefault(SortOrder.NEWEST)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val status by RecorderService.status.collectAsStateWithLifecycle()
                    val level by RecorderService.level.collectAsStateWithLifecycle()
                    val justSaved by RecorderService.justSaved.collectAsStateWithLifecycle()
                    // Refresh the list whenever a recording starts or stops.
                    LaunchedEffect(status.state) { refresh() }
                    LaunchedEffect(justSaved) {
                        val uri = justSaved ?: return@LaunchedEffect
                        RecorderService.consumeJustSaved()
                        Recordings.displayName(this@MainActivity, uri)?.let {
                            nameRequest = NameRequest(uri, it, isNew = true)
                        }
                    }
                    if (showHelp) {
                        HelpScreen(onClose = { showHelp = false })
                    } else {
                        Screen(status, level)
                    }
                    nameRequest?.let { NameDialog(it) }
                    deleteRequest?.let { DeleteDialog(it) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onStop() {
        super.onStop()
        stopPlayback()
    }

    private fun refresh() {
        recordings = Recordings.list(this)
    }

    private fun startRecording() {
        stopPlayback()
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (android.os.Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            RecorderService.send(this, RecorderService.ACTION_START)
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun togglePlay(rec: Recording) {
        if (playingUri == rec.uri) {
            stopPlayback()
            return
        }
        stopPlayback()
        player = MediaPlayer().apply {
            setDataSource(this@MainActivity, rec.uri)
            setOnCompletionListener { stopPlayback() }
            prepare()
            start()
        }
        playingUri = rec.uri
    }

    private fun stopPlayback() {
        player?.release()
        player = null
        playingUri = null
    }

    private fun share(rec: Recording) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/aac")
            .putExtra(Intent.EXTRA_STREAM, rec.uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, rec.name))
    }

    private fun openWith(rec: Recording) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(rec.uri, "audio/aac")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(view, "Open with"))
    }

    /**
     * Android has no standard "open this folder" intent, so try Samsung's My Files first (where
     * Samsung owners look for files), then the system Files app, then fall back to showing the path.
     */
    private fun openFolder() {
        val path = Recordings.relativePath.trimEnd('/')
        val myFiles = Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
            .setPackage("com.sec.android.app.myfiles")
            .putExtra("samsung.myfiles.intent.extra.START_PATH", "/storage/emulated/0/$path")
        val docUri = DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:$path"
        )
        val systemFiles = Intent(Intent.ACTION_VIEW)
            .setDataAndType(docUri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        for (intent in listOf(myFiles, systemFiles)) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
            } catch (_: SecurityException) {
            }
        }
        Toast.makeText(this, "Open your file manager and go to $path", Toast.LENGTH_LONG).show()
    }

    private fun delete(rec: Recording) {
        if (playingUri == rec.uri) stopPlayback()
        Recordings.delete(this, rec.uri)
        refresh()
    }

    private fun applyName(req: NameRequest, name: String) {
        if (name.trim().removeSuffix(".aac") != req.currentName.removeSuffix(".aac")) {
            if (!Recordings.rename(this, req.uri, name)) {
                Toast.makeText(this, "Couldn't use that name. It may already be taken.", Toast.LENGTH_LONG).show()
                return
            }
        }
        nameRequest = null
        refresh()
    }

    @Composable
    private fun DeleteDialog(rec: Recording) {
        AlertDialog(
            onDismissRequest = { deleteRequest = null },
            title = { Text("Delete recording?") },
            text = { Text("\"${rec.name.removeSuffix(".aac")}\" will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteRequest = null
                    delete(rec)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteRequest = null }) { Text("Cancel") } },
        )
    }

    @Composable
    private fun SearchAndSort() {
        var menuOpen by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search recordings") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.weight(1f),
            )
            Box {
                TextButton(onClick = { menuOpen = true }) { Text("Sort") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    SortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text((if (order == sortOrder) "✓ " else "    ") + order.label) },
                            onClick = {
                                sortOrder = order
                                prefs.edit().putString("sort", order.name).apply()
                                menuOpen = false
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun NameDialog(req: NameRequest) {
        val base = req.currentName.removeSuffix(".aac")
        var field by remember(req) { mutableStateOf(TextFieldValue(base, TextRange(0, base.length))) }
        AlertDialog(
            onDismissRequest = { nameRequest = null },
            title = { Text(if (req.isNew) "Name this recording" else "Rename recording") },
            text = {
                OutlinedTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { applyName(req, field.text) }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { nameRequest = null }) {
                    Text(if (req.isNew) "Keep default" else "Cancel")
                }
            },
        )
    }

    @Composable
    private fun Screen(status: RecStatus, level: Float) {
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(status.state) {
            while (status.state == RecState.RECORDING) {
                now = SystemClock.elapsedRealtime()
                delay(200)
            }
        }

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(getString(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { showHelp = true }) { Text("Help") }
            }
            Text(
                formatDuration(status.elapsedMs(now)),
                fontSize = 56.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 12.dp),
            )
            LinearProgressIndicator(
                progress = { if (status.state == RecState.RECORDING) level else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 32.dp),
            )
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                when (status.state) {
                    RecState.IDLE -> Button(onClick = ::startRecording) { Text("Record") }
                    RecState.RECORDING -> {
                        OutlinedButton(onClick = { RecorderService.send(this@MainActivity, RecorderService.ACTION_PAUSE) }) {
                            Text("Pause")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { RecorderService.send(this@MainActivity, RecorderService.ACTION_STOP) }) {
                            Text("Stop")
                        }
                    }
                    RecState.PAUSED -> {
                        OutlinedButton(onClick = { RecorderService.send(this@MainActivity, RecorderService.ACTION_RESUME) }) {
                            Text("Resume")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = { RecorderService.send(this@MainActivity, RecorderService.ACTION_STOP) }) {
                            Text("Stop")
                        }
                    }
                }
            }
            status.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Saved to ${Recordings.relativePath.trimEnd('/')}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = ::openFolder) { Text("Open folder") }
            }
            // The file being written is always the newest one; it can't be played or changed yet.
            val activeUri = if (status.state != RecState.IDLE) recordings.maxByOrNull { it.dateAddedSec }?.uri else null
            val shown = sortOrder.apply(
                recordings.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            )
            SearchAndSort()
            HorizontalDivider()
            if (shown.isEmpty()) {
                Text(
                    if (recordings.isEmpty()) "No recordings yet" else "No recordings match \"${query.trim()}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.uri.toString() }) { rec ->
                    RecordingRow(
                        rec,
                        isPlaying = playingUri == rec.uri,
                        isActive = rec.uri == activeUri,
                        isExpanded = expandedUri == rec.uri,
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun RecordingRow(rec: Recording, isPlaying: Boolean, isActive: Boolean, isExpanded: Boolean) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !isActive) {
                    expandedUri = if (isExpanded) null else rec.uri
                }
                .padding(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rec.name.removeSuffix(".aac"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                when {
                    isActive -> Text("Recording…", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    isPlaying -> Text("Playing", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(rec.dateAddedSec * 1000)) + " · " +
                    formatDuration(rec.sizeBytes * 8 * 1000 / ENCODE_BITRATE) + " · " +
                    Formatter.formatShortFileSize(this@MainActivity, rec.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
            )
            if (isExpanded && !isActive) {
                FlowRow {
                    TextButton(onClick = { togglePlay(rec) }) { Text(if (isPlaying) "Stop" else "Play") }
                    TextButton(onClick = { nameRequest = NameRequest(rec.uri, rec.name, isNew = false) }) { Text("Rename") }
                    TextButton(onClick = { share(rec) }) { Text("Share") }
                    TextButton(onClick = { openWith(rec) }) { Text("Open with") }
                    TextButton(onClick = { deleteRequest = rec }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

// Bits per second of the saved files, used to estimate length from size.
private const val ENCODE_BITRATE = 128_000L

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
