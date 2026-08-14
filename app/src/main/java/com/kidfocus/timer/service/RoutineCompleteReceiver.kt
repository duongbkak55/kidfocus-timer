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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@AndroidEntryPoint
class RoutineCompleteReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: RoutineRepository
    @Inject lateinit var scheduler: RoutineAlarmScheduler
    @Inject lateinit var notifications: RoutineNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE_ROUTINE) return
        val routineId = intent.getLongExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_ID, -1L)
        val date = runCatching {
            LocalDate.parse(intent.getStringExtra(RoutineAlarmScheduler.EXTRA_OCCURRENCE_DATE).orEmpty())
        }.getOrDefault(LocalDate.now())
        if (routineId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getById(routineId)?.let { routine ->
                    repository.complete(routine, date)
                    notifications.cancel(routineId)
                    val tomorrow = LocalDate.now().plusDays(1)
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    scheduler.schedule(routine, tomorrow)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_COMPLETE_ROUTINE = "com.kidfocus.timer.ACTION_COMPLETE_ROUTINE"
    }
}
