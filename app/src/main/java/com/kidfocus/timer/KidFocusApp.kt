package com.kidfocus.timer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class for KidFocus Timer.
 * Initializes Hilt DI and creates notification channels.
 */
@HiltAndroidApp
class KidFocusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(timerChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    companion object {
        const val TIMER_CHANNEL_ID = "kidfocus_timer_channel"
        const val ALERT_CHANNEL_ID = "kidfocus_alert_channel"
        const val TIMER_NOTIFICATION_ID = 1001
    }
}
