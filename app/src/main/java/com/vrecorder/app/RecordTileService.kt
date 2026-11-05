package com.vrecorder.app

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Quick settings "Record" button. Starting goes through [QuickRecordActivity] rather than straight
 * to the service: an activity may start the microphone from the lock screen and on Android 14+,
 * where a tile can't start a microphone service from the background.
 */
class RecordTileService : TileService() {

    companion object {
        fun refresh(context: Context) {
            runCatching { requestListeningState(context, ComponentName(context, RecordTileService::class.java)) }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val state = RecorderService.status.value.state
        tile.state = if (state == RecState.IDLE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = when (state) {
            RecState.IDLE -> "Record"
            RecState.RECORDING -> "Stop recording"
            RecState.PAUSED -> "Stop (paused)"
        }
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (RecorderService.status.value.state != RecState.IDLE) {
            RecorderService.send(this, RecorderService.ACTION_STOP)
            return
        }
        val intent = QuickRecordActivity.intent(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
