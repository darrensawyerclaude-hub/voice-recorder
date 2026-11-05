package com.vrecorder.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.FileOutputStream

enum class RecState { IDLE, RECORDING, PAUSED }

data class RecStatus(
    val state: RecState = RecState.IDLE,
    // Elapsed recording time excluding pauses, frozen while paused.
    val elapsedBeforeMs: Long = 0,
    val segmentStartedAt: Long = 0,
    val error: String? = null,
) {
    fun elapsedMs(now: Long = SystemClock.elapsedRealtime()): Long =
        if (state == RecState.RECORDING) elapsedBeforeMs + (now - segmentStartedAt) else elapsedBeforeMs
}

/**
 * Foreground service so recording keeps going with the screen off or the app in the background.
 * The capture and encoding itself lives in [AudioEngine].
 */
class RecorderService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_STOP = "stop"
        private const val CHANNEL_ID = "recording"
        private const val NOTIFICATION_ID = 1

        private val _status = MutableStateFlow(RecStatus())
        val status: StateFlow<RecStatus> = _status

        // Peak level of the latest audio block, 0..1, for the level meter.
        private val _level = MutableStateFlow(0f)
        val level: StateFlow<Float> = _level

        // The most recently finished recording, until the UI has offered to name it.
        private val _justSaved = MutableStateFlow<Uri?>(null)
        val justSaved: StateFlow<Uri?> = _justSaved
        fun consumeJustSaved() { _justSaved.value = null }

        fun send(context: Context, action: String) {
            val intent = Intent(context, RecorderService::class.java).setAction(action)
            if (action == ACTION_START) context.startForegroundService(intent) else context.startService(intent)
        }
    }

    private var engine: AudioEngine? = null
    private var pfd: ParcelFileDescriptor? = null
    private var outputUri: Uri? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stop()
            // Restarted by the system after being killed: the old recording is already safe on disk.
            null -> if (engine == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start() {
        if (engine != null) return
        startInForeground()
        try {
            val uri = Recordings.create(this) ?: error("Couldn't create file in ${Recordings.relativePath}")
            outputUri = uri
            val fd = contentResolver.openFileDescriptor(uri, "w") ?: error("Couldn't open file")
            pfd = fd
            val e = AudioEngine(FileOutputStream(fd.fileDescriptor)) { _level.value = it }
            e.start()
            engine = e
            _status.value = RecStatus(RecState.RECORDING, 0, SystemClock.elapsedRealtime())
            updateNotification()
        } catch (e: Exception) {
            cleanUp()
            outputUri?.let { Recordings.delete(this, it) }
            outputUri = null
            _status.value = RecStatus(error = "Couldn't start recording: ${e.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pause() {
        val s = _status.value
        if (s.state != RecState.RECORDING) return
        engine?.paused = true
        _status.value = s.copy(state = RecState.PAUSED, elapsedBeforeMs = s.elapsedMs())
        updateNotification()
    }

    private fun resume() {
        val s = _status.value
        if (s.state != RecState.PAUSED) return
        engine?.paused = false
        _status.value = s.copy(state = RecState.RECORDING, segmentStartedAt = SystemClock.elapsedRealtime())
        updateNotification()
    }

    private fun stop() {
        cleanUp()
        val saved = outputUri
        markFinished()
        _level.value = 0f
        _status.value = RecStatus()
        _justSaved.value = saved
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun markFinished() {
        val uri = outputUri ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_RECORDING, 0) }
            runCatching { contentResolver.update(uri, values, null, null) }
        }
        outputUri = null
    }

    private fun cleanUp() {
        runCatching { engine?.stop() }
        engine = null
        runCatching { pfd?.close() }
        pfd = null
    }

    override fun onDestroy() {
        if (engine != null) stop()
        super.onDestroy()
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val s = _status.value
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(if (s.state == RecState.PAUSED) "Recording paused" else "Recording")
            .setContentIntent(open)
            .setOngoing(true)
            .setUsesChronometer(s.state == RecState.RECORDING)
            .setWhen(System.currentTimeMillis() - s.elapsedMs())
        if (s.state == RecState.RECORDING) builder.addAction(action("Pause", ACTION_PAUSE, 1))
        if (s.state == RecState.PAUSED) builder.addAction(action("Resume", ACTION_RESUME, 2))
        builder.addAction(action("Stop", ACTION_STOP, 3))
        return builder.build()
    }

    private fun action(title: String, action: String, requestCode: Int): Notification.Action {
        val pi = PendingIntent.getService(
            this, requestCode, Intent(this, RecorderService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(null, title, pi).build()
    }
}
