package com.kidfocus.timer.childlog

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kidfocus.timer.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChildLogWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val enabled = ChildLogEntryControls.allowed(context)
                appWidgetIds.forEach { id -> update(context, manager, id, enabled) }
            } finally { pending.finish() }
        }
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, id: Int, enabled: Boolean) {
            val views = RemoteViews(context.packageName, R.layout.child_log_widget)
            views.setTextViewText(R.id.child_log_widget_button,
                context.getString(if (enabled) R.string.child_log_widget_title else R.string.child_log_parent_disabled))
            val intent = Intent(context, LockScreenLogActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val click = PendingIntent.getActivity(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.child_log_widget_button, click)
            manager.updateAppWidget(id, views)
        }
    }
}
