package com.kidfocus.timer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.kidfocus.timer.KidFocusApp
import com.kidfocus.timer.MainActivity
import com.kidfocus.timer.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun show(
        routineId: Long,
        title: String,
        emoji: String,
        deadlineMinutes: Int,
        occurrenceDate: String,
        linkedTimerMinutes: Int?,
    ) {
        val openApp = PendingIntent.getActivity(
            context,
            routineId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val complete = PendingIntent.getBroadcast(
            context,
            routineId.toInt(),
            Intent(context, RoutineCompleteReceiver::class.java).apply {
                action = RoutineCompleteReceiver.ACTION_COMPLETE_ROUTINE
                putExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_ID, routineId)
                putExtra(RoutineAlarmScheduler.EXTRA_OCCURRENCE_DATE, occurrenceDate)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hour = deadlineMinutes / 60
        val minute = deadlineMinutes % 60
        val deadline = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)
        val timerHint = linkedTimerMinutes?.let { " • Có timer $it phút" }.orEmpty()

        val notification = NotificationCompat.Builder(context, KidFocusApp.ROUTINE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("$emoji $title")
            .setContentText("Cần hoàn thành trước $deadline$timerHint")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(R.drawable.ic_timer_notification, "Đã xong", complete)
            .build()

        manager.notify(notificationId(routineId), notification)
    }

    fun cancel(routineId: Long) = manager.cancel(notificationId(routineId))

    private fun notificationId(routineId: Long): Int =
        200_000 + ((routineId xor (routineId ushr 32)).toInt() and 0xFFFF)
}
