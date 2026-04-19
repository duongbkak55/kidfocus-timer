package com.kidfocus.timer.alarm

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kidfocus.timer.KidFocusApp
import com.kidfocus.timer.MainActivity
import com.kidfocus.timer.R
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TaskAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var repository: ScheduledTaskRepository
    @Inject lateinit var alarmScheduler: AlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val taskName = intent.getStringExtra(EXTRA_TASK_NAME) ?: return
        val emoji = intent.getStringExtra(EXTRA_TASK_EMOJI) ?: ""
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val dayOfWeek = intent.getIntExtra(EXTRA_DAY_OF_WEEK, -1)

        showNotification(context, taskName, emoji)

        // Reschedule same alarm for next week
        if (taskId >= 0 && dayOfWeek >= 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val tasks = repository.getEnabledTasks()
                    val task = tasks.find { it.id == taskId }
                    task?.let { alarmScheduler.scheduleTask(it) }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun showNotification(context: Context, taskName: String, emoji: String) {
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, KidFocusApp.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("$emoji Đến giờ rồi!")
            .setContentText("Bắt đầu $taskName nào! 🎯")
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_BASE + taskName.hashCode(), notification)
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_NAME = "extra_task_name"
        const val EXTRA_TASK_EMOJI = "extra_task_emoji"
        const val EXTRA_FOCUS_MINUTES = "extra_focus_minutes"
        const val EXTRA_DAY_OF_WEEK = "extra_day_of_week"
        private const val NOTIFICATION_ID_BASE = 1000
    }
}
