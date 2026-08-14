package com.kidfocus.timer.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.domain.model.RoutineTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    fun schedule(
        routine: RoutineEntity,
        notBeforeMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ) {
        cancel(routine.id)
        if (!routine.enabled || routine.repeatDaysMask == 0) return

        val occurrence = RoutineTime.nextReminder(routine, notBeforeMillis, zoneId) ?: return
        val intent = alarmIntent(
            routine = routine,
            occurrenceDate = occurrence.date,
        )
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(routine.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    occurrence.reminderMillis,
                    pendingIntent,
                )
            } catch (_: SecurityException) {
                scheduleInexact(occurrence.reminderMillis, pendingIntent)
            }
        } else {
            scheduleInexact(occurrence.reminderMillis, pendingIntent)
        }
    }

    fun cancel(routineId: Long) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode(routineId),
            Intent(context, RoutineAlarmReceiver::class.java).apply {
                action = ACTION_REMIND_ROUTINE
                data = Uri.parse("kidfocus://routine/$routineId")
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun alarmIntent(routine: RoutineEntity, occurrenceDate: LocalDate) =
        Intent(context, RoutineAlarmReceiver::class.java).apply {
            action = ACTION_REMIND_ROUTINE
            data = Uri.parse("kidfocus://routine/${routine.id}")
            putExtra(EXTRA_ROUTINE_ID, routine.id)
            putExtra(EXTRA_TITLE, routine.title)
            putExtra(EXTRA_EMOJI, routine.emoji)
            putExtra(EXTRA_DEADLINE_MINUTES, routine.deadlineMinutes)
            putExtra(EXTRA_OCCURRENCE_DATE, occurrenceDate.toString())
            routine.linkedTimerMinutes?.let { putExtra(EXTRA_LINKED_TIMER_MINUTES, it) }
        }

    private fun scheduleInexact(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
    }

    private fun requestCode(id: Long): Int = (id xor (id ushr 32)).toInt() and Int.MAX_VALUE

    companion object {
        const val ACTION_REMIND_ROUTINE = "com.kidfocus.timer.ACTION_REMIND_ROUTINE"
        const val EXTRA_ROUTINE_ID = "routine_id"
        const val EXTRA_TITLE = "routine_title"
        const val EXTRA_EMOJI = "routine_emoji"
        const val EXTRA_DEADLINE_MINUTES = "routine_deadline_minutes"
        const val EXTRA_OCCURRENCE_DATE = "routine_occurrence_date"
        const val EXTRA_LINKED_TIMER_MINUTES = "routine_linked_timer_minutes"
    }
}
