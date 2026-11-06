package com.vrecorder.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.concurrent.thread

/** What the transcription service is doing, for the player screen and the notification. */
data class TranscribeJob(
    val uri: Uri,
    val name: String,
    val model: SpeechModel,
    val phase: Phase,
    // 0..1 within the current phase.
    val progress: Float = 0f,
    val error: String? = null,
) {
    enum class Phase { DOWNLOADING, TRANSCRIBING, DONE, FAILED, CANCELLED }
    val running get() = phase == Phase.DOWNLOADING || phase == Phase.TRANSCRIBING
}

/**
 * Runs model download and transcription as a foreground service, so it carries on if the user
 * leaves the app or the screen goes off (Samsung closes background apps within a minute).
 * One job at a time.
 */
class TranscriptionService : Service() {

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_MODEL = "model"
        private const val EXTRA_DURATION = "durationMs"
        private const val ACTION_CANCEL = "cancel"
        private const val CHANNEL_ID = "transcription"
        private const val NOTIFICATION_ID = 2
        private const val DONE_NOTIFICATION_ID = 3

        private val _job = MutableStateFlow<TranscribeJob?>(null)
        val job: StateFlow<TranscribeJob?> = _job

        fun start(context: Context, rec: Recording, model: SpeechModel, durationMs: Long) {
            if (_job.value?.running == true) return
            context.startForegroundService(
                Intent(context, TranscriptionService::class.java)
                    .setData(rec.uri)
                    .putExtra(EXTRA_NAME, rec.name)
                    .putExtra(EXTRA_MODEL, model.name)
                    .putExtra(EXTRA_DURATION, durationMs)
            )
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, TranscriptionService::class.java).setAction(ACTION_CANCEL))
        }
    }

    @Volatile private var cancelled = false
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            cancelled = true
            return START_NOT_STICKY
        }
        val uri = intent?.data ?: return START_NOT_STICKY.also { stopSelf() }
        if (_job.value?.running == true) return START_NOT_STICKY
        val name = intent.getStringExtra(EXTRA_NAME) ?: "Recording"
        val model = SpeechModel.valueOf(intent.getStringExtra(EXTRA_MODEL) ?: SpeechModel.QUICK.name)
        val durationMs = intent.getLongExtra(EXTRA_DURATION, 1L).coerceAtLeast(1L)
        cancelled = false

        val installed = SpeechModels.isInstalled(this, model)
        update(TranscribeJob(uri, name, model, if (installed) TranscribeJob.Phase.TRANSCRIBING else TranscribeJob.Phase.DOWNLOADING))
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        thread(name = "transcribe") {
            // Keeps the CPU awake with the screen off; released however the job ends.
            val wake = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vrecorder:transcribe")
            wake.acquire(4 * 60 * 60 * 1000L)
            try {
                if (!installed) {
                    SpeechModels.download(this, model, { cancelled }) { bytes ->
                        progress(bytes.toFloat() / model.downloadBytes)
                    }
                    update(_job.value!!.copy(phase = TranscribeJob.Phase.TRANSCRIBING, progress = 0f))
                }
                val segments = Transcriber.transcribe(this, uri, model, durationMs, { cancelled }) { doneMs ->
                    progress(doneMs.toFloat() / durationMs)
                }
                Transcripts.save(this, uri, Transcript(model.name, System.currentTimeMillis(), segments))
                update(_job.value!!.copy(phase = TranscribeJob.Phase.DONE, progress = 1f))
                notifyDone(uri, name, segments.isEmpty())
            } catch (e: Throwable) {
                val wasCancelled = cancelled || e is InterruptedException
                if (wasCancelled && _job.value?.phase == TranscribeJob.Phase.DOWNLOADING) SpeechModels.delete(this, model)
                update(
                    _job.value!!.copy(
                        phase = if (wasCancelled) TranscribeJob.Phase.CANCELLED else TranscribeJob.Phase.FAILED,
                        error = if (wasCancelled) null else (e.message ?: e.javaClass.simpleName),
                    )
                )
            } finally {
                wake.release()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun update(j: TranscribeJob) {
        _job.value = j
    }

    private fun progress(p: Float) {
        val j = _job.value ?: return
        _job.value = j.copy(progress = p.coerceIn(0f, 1f))
        // Notifications are rate-limited; refresh about once a second.
        val now = System.currentTimeMillis()
        if (now - lastNotified > 1000) {
            lastNotified = now
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    private fun channel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Transcription", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun notification(): Notification {
        channel()
        val j = _job.value
        val cancel = PendingIntent.getService(
            this, 20, Intent(this, TranscriptionService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE,
        )
        val pct = ((j?.progress ?: 0f) * 100).toInt()
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(
                if (j?.phase == TranscribeJob.Phase.DOWNLOADING) "Downloading the ${j.model.label} model"
                else "Transcribing ${j?.name?.removeSuffix(".aac") ?: ""}"
            )
            .setContentText("$pct%")
            .setProgress(100, pct, false)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            .build()
    }

    private fun notifyDone(uri: Uri, name: String, empty: Boolean) {
        channel()
        val rec = Recording(uri, name, 0, 0)
        val open = PendingIntent.getActivity(
            this, 21, PlayerActivity.intent(this, rec).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        getSystemService(NotificationManager::class.java).notify(
            DONE_NOTIFICATION_ID,
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_mic)
                .setContentTitle(if (empty) "No speech found" else "Transcript ready")
                .setContentText(name.removeSuffix(".aac"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }
}
