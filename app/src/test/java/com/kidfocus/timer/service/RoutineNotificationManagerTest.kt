package com.kidfocus.timer.service

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import com.kidfocus.timer.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RoutineNotificationManagerTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val notifications get() = shadowOf(context.getSystemService(NotificationManager::class.java))

    private fun localized(text: String, action: String, timer: Int?) {
        RoutineNotificationManager(context).show(42L, "Reading", "📚", 420, "2026-09-29", timer)
        val notification = notifications.allNotifications.single()
        assertEquals("📚 Reading", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(text, notification.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(action, notification.actions.single().title.toString())
    }

    @Test @Config(qualifiers = "en") fun `English reminder includes deadline timer and completion action`() = localized("Complete by 07:00 • 25-minute timer", "Done", 25)
    @Test @Config(qualifiers = "vi") fun `Vietnamese reminder includes deadline timer and completion action`() = localized("Cần hoàn thành trước 07:00 • Có timer 25 phút", "Đã xong", 25)
    @Test @Config(qualifiers = "en") fun `reminder without timer has no dangling hint`() = localized("Complete by 07:00", "Done", null)

    @Test fun `IDs sharing low 32 bits retain separate complete and open intents with correct extras`() {
        val firstId = 1_800_000_000_000_123L
        val secondId = firstId + (1L shl 32)
        assertEquals(firstId.toInt(), secondId.toInt())
        val manager = RoutineNotificationManager(context)
        val date = "2026-09-28"
        manager.show(firstId, "First", "", 420, date, null)
        manager.show(secondId, "Second", "", 450, date, null)

        val shown = notifications.allNotifications
        assertEquals(2, shown.size)
        val first = shown.single { shadowOf(it.actions.single().actionIntent).savedIntent.getLongExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_ID, -1) == firstId }
        val second = shown.single { it !== first }
        assertNotEquals(first.actions.single().actionIntent, second.actions.single().actionIntent)
        assertNotEquals(first.contentIntent, second.contentIntent)
        assertIntents(first, firstId, date)
        assertIntents(second, secondId, date)
    }

    @Test fun `new occurrence does not overwrite pending completion date from previous notification`() {
        val manager = RoutineNotificationManager(context)
        val id = 1_800_000_000_000_123L
        manager.show(id, "Routine", "", 420, "2026-09-28", null)
        val first = notifications.allNotifications.single()
        manager.show(id, "Routine", "", 420, "2026-09-29", null)
        val second = notifications.allNotifications.single()
        assertNotEquals(first.actions.single().actionIntent, second.actions.single().actionIntent)
        assertNotEquals(first.contentIntent, second.contentIntent)
        assertIntents(first, id, "2026-09-28")
        assertIntents(second, id, "2026-09-29")
    }

    private fun assertIntents(notification: Notification, id: Long, date: String) {
        val complete = shadowOf(notification.actions.single().actionIntent)
        val open = shadowOf(notification.contentIntent)
        val requestCode = (id xor (id ushr 32)).toInt() and Int.MAX_VALUE
        assertEquals(requestCode, complete.requestCode)
        assertEquals(requestCode, open.requestCode)
        assertEquals("kidfocus://routine/$id/$date/complete", complete.savedIntent.data.toString())
        assertEquals(RoutineCompleteReceiver.ACTION_COMPLETE_ROUTINE, complete.savedIntent.action)
        assertEquals(id, complete.savedIntent.getLongExtra(RoutineAlarmScheduler.EXTRA_ROUTINE_ID, -1))
        assertEquals(date, complete.savedIntent.getStringExtra(RoutineAlarmScheduler.EXTRA_OCCURRENCE_DATE))
        assertEquals("kidfocus://routine/$id/$date/open", open.savedIntent.data.toString())
        assertEquals(MainActivity::class.java.name, open.savedIntent.component!!.className)
        assertTrue(complete.isBroadcast)
        assertTrue(open.isActivity)
    }
}
