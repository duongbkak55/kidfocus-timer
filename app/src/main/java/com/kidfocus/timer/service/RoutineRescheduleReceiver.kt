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
class RoutineRescheduleReceiver : BroadcastReceiver() {
    @Inject lateinit var repository: RoutineRepository
    @Inject lateinit var scheduler: RoutineAlarmScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val supported = intent.action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
        if (!supported) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.getEnabled().forEach(scheduler::schedule)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
