package com.kidfocus.timer.alarm

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AlarmSchedulerTest {
    private fun task(id: Long, days: Set<Int> = setOf(2)) = ScheduledTask(id, TaskType.CUSTOM, "Task", "", 18, 0, days, 30, 5)
    @Test fun `large IDs with same hash remain distinct by URI`() {
        val context = RuntimeEnvironment.getApplication()
        val scheduler = AlarmScheduler(context)
        val a = task(1)
        val b = task(1L shl 32) // Same folded request code as 1.
        scheduler.scheduleTask(a)
        scheduler.scheduleTask(b)
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        assertEquals(2, alarms.scheduledAlarms.size)
        assertEquals(setOf("kidfocus://task/1/2", "kidfocus://task/4294967296/2"),
            alarms.scheduledAlarms.map { shadowOf(it.operation).savedIntent.data.toString() }.toSet())
        scheduler.cancelTask(a)
        assertEquals(1, alarms.scheduledAlarms.size)
    }
    @Test fun `updating weekdays cancels legacy and new alarms for all previous days`() {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(AlarmManager::class.java)
        val scheduler = AlarmScheduler(context)
        val a = task(42, setOf(2, 3))
        val legacy = PendingIntent.getBroadcast(context, (a.id * 10 + 4).toInt(), Intent(context, TaskAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 60_000, legacy)
        scheduler.scheduleTask(a)
        assertEquals(2, shadowOf(manager).scheduledAlarms.size)
        scheduler.scheduleTask(a.copy(daysOfWeek = setOf(7)))
        assertEquals(1, shadowOf(manager).scheduledAlarms.size)
        assertEquals("kidfocus://task/42/7", shadowOf(shadowOf(manager).scheduledAlarms.single().operation).savedIntent.data.toString())
        scheduler.cancelTask(a)
        assertTrue(shadowOf(manager).scheduledAlarms.isEmpty())
    }
}
