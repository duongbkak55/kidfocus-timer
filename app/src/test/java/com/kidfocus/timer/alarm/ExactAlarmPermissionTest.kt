package com.kidfocus.timer.alarm

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.provider.Settings
import com.kidfocus.timer.data.datastore.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class)
class ExactAlarmPermissionTest {
    @Test fun `checks current permission and opens package specific special access`() {
        val context = RuntimeEnvironment.getApplication()
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        val permission = ExactAlarmPermission(context)
        assertFalse(permission.isAllowed())
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        assertTrue(permission.isAllowed())
        assertTrue(permission.openSettings())
        val intent = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test @Config(sdk = [28]) fun `old Android needs no special access`() {
        val context = RuntimeEnvironment.getApplication()
        val permission = ExactAlarmPermission(context)
        assertTrue(permission.isAllowed())
        assertFalse(permission.openSettings())
        assertNull(shadowOf(context).nextStartedActivity)
    }

    @Test fun `unavailable settings does not crash`() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { throw android.content.ActivityNotFoundException() }
        }
        assertFalse(ExactAlarmPermission(context).openSettings())
    }

    @Test fun `real DataStore remembers reminder dismissal across wrappers and settings writes`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val first = SettingsDataStore(context)
        assertFalse(first.scheduleAlarmReminderDismissed.first())
        first.dismissScheduleAlarmReminder()
        val reloaded = SettingsDataStore(context)
        assertTrue(reloaded.scheduleAlarmReminderDismissed.first())
        reloaded.saveSettings(reloaded.settingsFlow.first())
        assertTrue(reloaded.scheduleAlarmReminderDismissed.first())
    }
}
