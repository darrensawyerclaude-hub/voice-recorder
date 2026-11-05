package com.vrecorder.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

/**
 * A cut-down recorder that can show over the lock screen (quick settings tile, widget, Samsung
 * lock-screen shortcut). It only offers Pause/Resume and Stop: the list of recordings stays in
 * [MainActivity], which needs the phone unlocked, so a locked phone can't be used to play them.
 */
class QuickRecordActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_AUTO_START = "autoStart"

        fun intent(context: Context, autoStart: Boolean = true): Intent =
            Intent(context, QuickRecordActivity::class.java)
                .putExtra(EXTRA_AUTO_START, autoStart)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }

    private val micGranted
        get() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        if (savedInstanceState == null) maybeStart(intent)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val status by RecorderService.status.collectAsStateWithLifecycle()
                    val level by RecorderService.level.collectAsStateWithLifecycle()
                    QuickScreen(status, level)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        maybeStart(intent)
    }

    private fun maybeStart(intent: Intent) {
        val idle = RecorderService.status.value.state == RecState.IDLE
        if (idle && micGranted && intent.getBooleanExtra(EXTRA_AUTO_START, true)) {
            RecorderService.send(this, RecorderService.ACTION_START)
        }
    }

    private fun stopAndClose() {
        RecorderService.send(this, RecorderService.ACTION_STOP)
        finish()
    }

    @Composable
    private fun QuickScreen(status: RecStatus, level: Float) {
        var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
        LaunchedEffect(status.state) {
            while (status.state == RecState.RECORDING) {
                now = SystemClock.elapsedRealtime()
                delay(200)
            }
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (!micGranted) {
                Text(
                    "Open the Recorder app once and allow the microphone, then this will start recording straight away.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(onClick = { finish() }) { Text("Close") }
                return@Column
            }
            Text(
                when {
                    status.pausedForCall -> "Paused for call"
                    status.state == RecState.PAUSED -> "Paused"
                    status.state == RecState.RECORDING -> "Recording"
                    else -> "Ready"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (status.state == RecState.RECORDING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                formatClock(status.elapsedMs(now)),
                fontSize = 72.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            LinearProgressIndicator(
                progress = { if (status.state == RecState.RECORDING) level else 0f },
                modifier = Modifier.fillMaxWidth().height(8.dp).padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(40.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                when (status.state) {
                    RecState.IDLE -> {
                        Button(onClick = { RecorderService.send(this@QuickRecordActivity, RecorderService.ACTION_START) }) {
                            Text("Record")
                        }
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(onClick = { finish() }) { Text("Close") }
                    }
                    RecState.RECORDING -> {
                        OutlinedButton(onClick = { RecorderService.send(this@QuickRecordActivity, RecorderService.ACTION_PAUSE) }) {
                            Text("Pause")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = ::stopAndClose) { Text("Stop") }
                    }
                    RecState.PAUSED -> {
                        OutlinedButton(onClick = { RecorderService.send(this@QuickRecordActivity, RecorderService.ACTION_RESUME) }) {
                            Text("Resume")
                        }
                        Spacer(Modifier.width(16.dp))
                        Button(onClick = ::stopAndClose) { Text("Stop") }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Recording carries on if you leave this screen. You can name it next time you open the app.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

internal fun formatClock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
