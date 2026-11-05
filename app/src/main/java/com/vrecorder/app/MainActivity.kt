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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.min
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
private data class NameRequest(
    val uri: Uri,
    val currentName: String,
    val isNew: Boolean,
    val note: String? = null,
)

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
    private var deleteRequest by mutableStateOf<List<Recording>?>(null)
    private var expandedUri by mutableStateOf<Uri?>(null)
    // Multi-select: entered with a long press, empty when not selecting.
    private var selected by mutableStateOf(emptySet<Uri>())
    // Recordings waiting for an export format, then for a folder.
    private var exportRequest by mutableStateOf<List<Recording>?>(null)
    private var pendingExport: Pair<List<Recording>, ExportFormat>? = null
    private var exportProgress by mutableStateOf<Pair<Int, Int>?>(null)
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

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        val job = pendingExport
        pendingExport = null
        if (tree == null || job == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        prefs.edit().putString("exportTree", tree.toString()).apply()
        runExport(job.first, job.second, tree)
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
                        val saved = justSaved ?: return@LaunchedEffect
                        RecorderService.consumeJustSaved()
                        Recordings.displayName(this@MainActivity, saved.uri)?.let {
                            nameRequest = NameRequest(saved.uri, it, isNew = true, note = saved.callSummary)
                        }
                    }
                    if (showHelp) {
                        HelpScreen(onClose = { showHelp = false })
                    } else {
                        Screen(status, level)
                    }
                    nameRequest?.let { NameDialog(it) }
                    deleteRequest?.let { DeleteDialog(it) }
                    exportRequest?.let { ExportDialog(it) }
                    exportProgress?.let { (done, total) ->
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Exporting") },
                            text = { Text("${min(done + 1, total)} of $total…") },
                            confirmButton = {},
                        )
                    }
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

    private fun shareMany(recs: List<Recording>) {
        if (recs.size == 1) return share(recs[0])
        val uris = ArrayList(recs.map { it.uri })
        val send = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("audio/aac")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // ClipData carries the read permission for every file, not just the first.
        send.clipData = android.content.ClipData.newRawUri(null, uris[0]).apply {
            uris.drop(1).forEach { addItem(android.content.ClipData.Item(it)) }
        }
        startActivity(Intent.createChooser(send, "Share ${recs.size} recordings"))
    }

    private fun chooseExportFolder(recs: List<Recording>, format: ExportFormat) {
        exportRequest = null
        pendingExport = recs to format
        // Start the picker in the last folder used, if there is one.
        folderPicker.launch(prefs.getString("exportTree", null)?.let(Uri::parse))
    }

    private fun runExport(recs: List<Recording>, format: ExportFormat, tree: Uri) {
        exportProgress = 0 to recs.size
        lifecycleScope.launch {
            val result = runCatching {
                Exporter.export(this@MainActivity, recs, format, tree) { done, total -> exportProgress = done to total }
            }.getOrElse { ExportResult(0, recs.map { it.name }) }
            exportProgress = null
            selected = emptySet()
            val message = when {
                result.failed.isEmpty() -> "Exported ${plural(result.exported, "recording")} as ${format.ext.uppercase()}"
                result.exported == 0 -> "Couldn't export. Try a different folder."
                else -> "Exported ${result.exported}. Couldn't export: ${result.failed.joinToString()}"
            }
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun delete(recs: List<Recording>) {
        if (recs.any { it.uri == playingUri }) stopPlayback()
        recs.forEach { Recordings.delete(this, it.uri) }
        selected = emptySet()
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
    private fun DeleteDialog(recs: List<Recording>) {
        AlertDialog(
            onDismissRequest = { deleteRequest = null },
            title = { Text(if (recs.size == 1) "Delete recording?" else "Delete ${recs.size} recordings?") },
            text = {
                Text(
                    if (recs.size == 1) "\"${recs[0].name.removeSuffix(".aac")}\" will be permanently deleted."
                    else "These ${recs.size} recordings will be permanently deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    deleteRequest = null
                    delete(recs)
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteRequest = null }) { Text("Cancel") } },
        )
    }

    @Composable
    private fun ExportDialog(recs: List<Recording>) {
        var format by remember { mutableStateOf(ExportFormat.AAC) }
        AlertDialog(
            onDismissRequest = { exportRequest = null },
            title = { Text(if (recs.size == 1) "Export recording" else "Export ${recs.size} recordings") },
            text = {
                Column {
                    ExportFormat.entries.forEach { f ->
                        Row(
                            Modifier.fillMaxWidth().clickable { format = f }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = format == f, onClick = { format = f })
                            Text(f.label)
                        }
                    }
                    Text(
                        "Copies are saved to a folder you choose next. Your originals stay where they are.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { chooseExportFolder(recs, format) }) { Text("Choose folder") } },
            dismissButton = { TextButton(onClick = { exportRequest = null }) { Text("Cancel") } },
        )
    }

    @Composable
    private fun SelectionBar(shown: List<Recording>) {
        val picked = recordings.filter { it.uri in selected }
        BackHandler { selected = emptySet() }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${picked.size} selected", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { selected = shown.map { it.uri }.toSet() }) { Text("Select all") }
                TextButton(onClick = { selected = emptySet() }) { Text("Cancel") }
            }
            Row {
                TextButton(onClick = { shareMany(picked) }, enabled = picked.isNotEmpty()) { Text("Share") }
                TextButton(onClick = { exportRequest = picked }, enabled = picked.isNotEmpty()) { Text("Export") }
                TextButton(onClick = { deleteRequest = picked }, enabled = picked.isNotEmpty()) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
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
                Column {
                    req.note?.let {
                        Text(
                            "$it. The call isn't in the recording.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    OutlinedTextField(
                        value = field,
                        onValueChange = { field = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                    )
                }
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
            val callLine = when {
                status.pausedForCall -> "Paused for a call. Recording resumes when the call ends."
                status.state != RecState.IDLE -> callSummary(status.callPauses)?.let { "$it · resumed" }
                else -> null
            }
            callLine?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                )
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
            if (selected.isNotEmpty()) SelectionBar(shown) else SearchAndSort()
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
                        isSelected = rec.uri in selected,
                        selecting = selected.isNotEmpty(),
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
    @Composable
    private fun RecordingRow(
        rec: Recording,
        isPlaying: Boolean,
        isActive: Boolean,
        isExpanded: Boolean,
        isSelected: Boolean,
        selecting: Boolean,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = !isActive,
                    onClick = {
                        if (selecting) selected = if (isSelected) selected - rec.uri else selected + rec.uri
                        else expandedUri = if (isExpanded) null else rec.uri
                    },
                    onLongClick = {
                        expandedUri = null
                        selected = selected + rec.uri
                    },
                )
                .padding(vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = null,
                        enabled = !isActive,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
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
            if (isExpanded && !isActive && !selecting) {
                FlowRow {
                    TextButton(onClick = { togglePlay(rec) }) { Text(if (isPlaying) "Stop" else "Play") }
                    TextButton(onClick = { nameRequest = NameRequest(rec.uri, rec.name, isNew = false) }) { Text("Rename") }
                    TextButton(onClick = { share(rec) }) { Text("Share") }
                    TextButton(onClick = { openWith(rec) }) { Text("Open with") }
                    TextButton(onClick = { exportRequest = listOf(rec) }) { Text("Export") }
                    TextButton(onClick = { deleteRequest = listOf(rec) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"

// Bits per second of the saved files, used to estimate length from size.
private const val ENCODE_BITRATE = 128_000L

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
