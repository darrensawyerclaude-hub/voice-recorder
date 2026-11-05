package com.vrecorder.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
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
    // Paused automatically for a phone or VoIP call; resumes by itself when the call ends.
    val pausedForCall: Boolean = false,
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
        private const val CALL_POLL_MS = 500L
        private const val CALL_RESUME_DELAY_MS = 1500L

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

    /*
     * Call detection via the audio mode rather than TelephonyManager: it needs no phone
     * permission and also catches WhatsApp/Teams-style calls (MODE_IN_COMMUNICATION).
     * During a call Android silences other apps' mic input anyway, so pausing loses nothing.
     */
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    // Set when the user taps Resume during a call: don't pause again until that call ends.
    private var callOverride = false
    private var normalSince = 0L

    private val callWatch = object : Runnable {
        override fun run() {
            if (engine == null) return
            val inCall = audioManager.mode != AudioManager.MODE_NORMAL
            val s = _status.value
            val now = SystemClock.elapsedRealtime()
            if (inCall) {
                normalSince = 0
                if (s.state == RecState.RECORDING && !callOverride) pause(forCall = true)
            } else {
                if (normalSince == 0L) normalSince = now
                callOverride = false
                // Short settle time so the end-of-call tone isn't recorded and brief mode flips don't resume.
                if (s.state == RecState.PAUSED && s.pausedForCall && now - normalSince >= CALL_RESUME_DELAY_MS) {
                    resume()
                }
            }
            handler.postDelayed(this, CALL_POLL_MS)
        }
    }

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
            callOverride = false
            normalSince = 0
            handler.post(callWatch)
        } catch (e: Exception) {
            cleanUp()
            outputUri?.let { Recordings.delete(this, it) }
            outputUri = null
            _status.value = RecStatus(error = "Couldn't start recording: ${e.message}")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pause(forCall: Boolean = false) {
        val s = _status.value
        if (s.state != RecState.RECORDING) return
        engine?.paused = true
        _status.value = s.copy(state = RecState.PAUSED, elapsedBeforeMs = s.elapsedMs(), pausedForCall = forCall)
        updateNotification()
    }

    private fun resume() {
        val s = _status.value
        if (s.state != RecState.PAUSED) return
        if (audioManager.mode != AudioManager.MODE_NORMAL) callOverride = true
        engine?.paused = false
        _status.value = s.copy(
            state = RecState.RECORDING, segmentStartedAt = SystemClock.elapsedRealtime(), pausedForCall = false,
        )
        updateNotification()
    }

    private fun stop() {
        handler.removeCallbacks(callWatch)
        cleanUp()
        val saved = outputUri
        outputUri = null
        _level.value = 0f
        _status.value = RecStatus()
        _justSaved.value = saved
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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
            .setContentTitle(
                when {
                    s.pausedForCall -> "Paused for call"
                    s.state == RecState.PAUSED -> "Recording paused"
                    else -> "Recording"
                }
            )
            .apply { if (s.pausedForCall) setContentText("Resumes automatically when the call ends") }
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
