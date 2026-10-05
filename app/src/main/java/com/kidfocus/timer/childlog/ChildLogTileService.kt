package com.kidfocus.timer.childlog

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChildLogTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refresh() }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        scope.launch {
            if (!ChildLogEntryControls.allowed(this@ChildLogTileService)) { refresh(); return@launch }
            val intent = Intent(this@ChildLogTileService, LockScreenLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (Build.VERSION.SDK_INT >= 34) {
                val pending = PendingIntent.getActivity(this@ChildLogTileService, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                startActivityAndCollapse(pending)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private suspend fun refresh() {
        qsTile?.let { tile ->
            tile.state = if (ChildLogEntryControls.allowed(this)) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
            tile.updateTile()
        }
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
