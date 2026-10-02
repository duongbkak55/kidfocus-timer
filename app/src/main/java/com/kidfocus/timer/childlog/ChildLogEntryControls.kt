package com.kidfocus.timer.childlog

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService
import com.kidfocus.timer.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first

object ChildLogEntryControls {
    suspend fun allowed(context: Context): Boolean = SettingsDataStore(context.applicationContext)
        .settingsFlow.first().allowLockScreenChildLog

    fun update(context: Context, enabled: Boolean) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val component = ComponentName(app, ChildLogWidgetProvider::class.java)
        manager.getAppWidgetIds(component).forEach { id -> ChildLogWidgetProvider.update(app, manager, id, enabled) }
        runCatching { TileService.requestListeningState(app, ComponentName(app, ChildLogTileService::class.java)) }
    }
}
