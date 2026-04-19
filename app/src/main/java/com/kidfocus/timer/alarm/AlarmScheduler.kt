package com.kidfocus.timer.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.kidfocus.timer.domain.model.ScheduledTask
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAll(tasks: List<ScheduledTask>) {
        tasks.forEach { task ->
            cancelTask(task)
            if (task.enabled) scheduleTask(task)
        }
    }

    fun scheduleTask(task: ScheduledTask) {
        if (!task.enabled || task.daysOfWeek.isEmpty()) return

        task.daysOfWeek.forEach { dayOfWeek ->
            val triggerMs = nextOccurrenceMs(dayOfWeek, task.hour, task.minute)
            val pendingIntent = buildPendingIntent(task, dayOfWeek) ?: return@forEach

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pendingIntent)
            }
        }
    }

    fun cancelTask(task: ScheduledTask) {
        task.daysOfWeek.forEach { dayOfWeek ->
            buildPendingIntent(task, dayOfWeek)?.let { alarmManager.cancel(it) }
        }
        // Also cancel for all possible days in case days changed
        (1..7).forEach { day ->
            buildPendingIntent(task, day)?.let { alarmManager.cancel(it) }
        }
    }

    private fun buildPendingIntent(task: ScheduledTask, dayOfWeek: Int): PendingIntent? {
        val intent = Intent(context, TaskAlarmReceiver::class.java).apply {
            putExtra(TaskAlarmReceiver.EXTRA_TASK_ID, task.id)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_NAME, task.name)
            putExtra(TaskAlarmReceiver.EXTRA_TASK_EMOJI, task.emoji)
            putExtra(TaskAlarmReceiver.EXTRA_FOCUS_MINUTES, task.focusDurationMinutes)
            putExtra(TaskAlarmReceiver.EXTRA_DAY_OF_WEEK, dayOfWeek)
        }
        // Unique request code per task + day
        val requestCode = (task.id * 10 + dayOfWeek).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun nextOccurrenceMs(dayOfWeek: Int, hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, dayOfWeek)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If the target is in the past or within 1 min, add 7 days
        if (target.timeInMillis <= now.timeInMillis + 60_000) {
            target.add(Calendar.WEEK_OF_YEAR, 1)
        }
        return target.timeInMillis
    }
}
