package com.kidfocus.timer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import com.kidfocus.timer.data.cloud.CloudSyncManager
import com.kidfocus.timer.data.billing.SubscriptionManager
import com.kidfocus.timer.data.repository.LearningRepository
import javax.inject.Inject

/**
 * Application class for KidFocus Timer.
 * Initializes Hilt DI and creates notification channels.
 */
@HiltAndroidApp
class KidFocusApp : Application() {

    @Inject lateinit var cloudSyncManager: CloudSyncManager
    @Inject lateinit var subscriptionManager: SubscriptionManager
    @Inject lateinit var learningRepository: LearningRepository

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        cloudSyncManager.start()
        subscriptionManager.start()
        learningRepository.start()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timerChannel = NotificationChannel(
                TIMER_CHANNEL_ID,
                "Timer Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows timer countdown while running in background"
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Timer Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when focus or break session ends"
            }

            val routineChannel = NotificationChannel(
                ROUTINE_CHANNEL_ID,
                "Nhắc lịch sinh hoạt",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Nhắc các việc cần hoàn thành đúng giờ"
                enableVibration(true)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(timerChannel)
            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(routineChannel)
        }
    }

    companion object {
        const val TIMER_CHANNEL_ID = "kidfocus_timer_channel"
        const val ALERT_CHANNEL_ID = "kidfocus_alert_channel"
        const val ROUTINE_CHANNEL_ID = "kidfocus_routine_channel"
        const val TIMER_NOTIFICATION_ID = 1001
    }
}
