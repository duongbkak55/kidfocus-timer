package com.kidfocus.timer.alarm

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "en")
class TaskAlarmNotificationTest {
    private fun notification(title: String, text: String) {
        val context = RuntimeEnvironment.getApplication()
        ReflectionHelpers.callInstanceMethod<Unit>(TaskAlarmReceiver(), "showNotification",
            ClassParameter.from(Context::class.java, context),
            ClassParameter.from(String::class.java, "Reading"), ClassParameter.from(String::class.java, "📚"))
        val notification = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals(title, notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(text, notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals("com.kidfocus.timer.MainActivity", shadowOf(notification.contentIntent).savedIntent.component!!.className)
    }

    @Test fun `English task notification keeps emoji and task name`() = notification("📚 It’s time!", "Time to start Reading! 🎯")
    @Test @Config(qualifiers = "vi") fun `Vietnamese task notification keeps existing wording`() = notification("📚 Đến giờ rồi!", "Bắt đầu Reading nào! 🎯")
}
