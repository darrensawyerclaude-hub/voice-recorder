package com.vrecorder.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews

/** Home-screen widget: one tap to record, with a running timer and Stop while recording. */
class RecordWidget : AppWidgetProvider() {

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordWidget::class.java))
            if (ids.isNotEmpty()) manager.updateAppWidget(ids, views(context))
        }

        private fun views(context: Context): RemoteViews {
            val s = RecorderService.status.value
            val v = RemoteViews(context.packageName, R.layout.widget_record)
            val idle = s.state == RecState.IDLE
            v.setTextViewText(
                R.id.widget_button,
                if (idle) "● Record" else "■ Stop",
            )
            v.setTextViewText(
                R.id.widget_status,
                when {
                    idle -> "Tap to record"
                    s.pausedForCall -> "Paused for call"
                    s.state == RecState.PAUSED -> "Paused"
                    else -> "Recording"
                },
            )
            v.setViewVisibility(R.id.widget_timer, if (idle) View.GONE else View.VISIBLE)
            v.setChronometer(
                R.id.widget_timer,
                SystemClock.elapsedRealtime() - s.elapsedMs(),
                null,
                s.state == RecState.RECORDING,
            )
            val tap = if (idle) {
                PendingIntent.getActivity(
                    context, 10, QuickRecordActivity.intent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            } else {
                PendingIntent.getService(
                    context, 11,
                    Intent(context, RecorderService::class.java).setAction(RecorderService.ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
            v.setOnClickPendingIntent(R.id.widget_root, tap)
            return v
        }
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        manager.updateAppWidget(ids, views(context))
    }
}
