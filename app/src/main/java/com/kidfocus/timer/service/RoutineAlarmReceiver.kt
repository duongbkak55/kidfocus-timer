package com.kidfocus.timer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kidfocus.timer.data.repository.RoutineRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class RoutineAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: RoutineRepository
    @Inject lateinit var scheduler: RoutineAlarmScheduler
    @Inject lateinit var notifications: RoutineNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RoutineAlarmScheduler.ACTION_REMIND_ROUTINE) return
        val routineId = intent.getLongExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_ID, -1L)
        if (routineId <= 0L) return

        notifications.show(
            routineId = routineId,
            title = intent.getStringExtra(RoutineAlarmScheduler.EXTRA_TITLE).orEmpty(),
            emoji = intent.getStringExtra(RoutineAlarmScheduler.EXTRA_EMOJI) ?: "⭐",
            deadlineMinutes = intent.getIntExtra(RoutineAlarmScheduler.EXTRA_DEADLINE_MINUTES, 0),
            occurrenceDate = intent.getStringExtra(RoutineAlarmScheduler.EXTRA_OCCURRENCE_DATE).orEmpty(),
            linkedTimerMinutes = intent.getIntExtra(RoutineAlarmScheduler.EXTRA_LINKED_TIMER_MINUTES, -1)
                .takeIf { it > 0 },
        )

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getById(routineId)?.let {
                    scheduler.schedule(it, System.currentTimeMillis() + 60_000L)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
