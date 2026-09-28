package com.kidfocus.timer.ui.schedule

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.alarm.AlarmScheduler
import com.kidfocus.timer.alarm.ExactAlarmPermission
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.ui.components.ScheduleAlarmPermissionLifecycle
import com.kidfocus.timer.ui.components.ScheduleExactAlarmReminder
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ScheduleAlarmPermissionViewModel
import io.mockk.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "en-w411dp-h891dp")
class ScheduleAlarmPermissionUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val permission = mockk<ExactAlarmPermission>()
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private val rows = listOf(ScheduledTask(42, TaskType.CUSTOM, "Reading", "", 18, 0, setOf(2), 30, 5))
    private val dismissed = MutableStateFlow(false)
    private lateinit var settings: SettingsDataStore
    private lateinit var vm: ScheduleAlarmPermissionViewModel
    private val title = "For on-time reminders, allow Alarms & reminders"

    @Before fun setup() {
        every { permission.isAllowed() } returns false
        every { permission.openSettings() } returns true
        val repository = mockk<ScheduledTaskRepository>()
        every { repository.allTasks } returns MutableStateFlow(rows)
        coEvery { repository.getEnabledTasks() } returns rows
        settings = mockk<SettingsDataStore>()
        every { settings.scheduleAlarmReminderDismissed } returns dismissed
        coEvery { settings.dismissScheduleAlarmReminder() } coAnswers { dismissed.value = true }
        vm = ScheduleAlarmPermissionViewModel(permission, repository, scheduler, settings)
        compose.setContent {
            ScheduleAlarmPermissionLifecycle(vm)
            KidFocusTheme { ScheduleExactAlarmReminder(vm) }
        }
        compose.waitForIdle()
    }

    @After fun teardown() { vm.viewModelScope.cancel() }

    @Test fun `real card opens settings and returning with permission hides it and reschedules`() {
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Open settings").performClick()
        verify(exactly = 1) { permission.openSettings() }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        every { permission.isAllowed() } returns true
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.onNodeWithText(title).assertDoesNotExist()
        verify(exactly = 1) { scheduler.scheduleAll(rows) }
    }

    @Test fun `real dismiss action persists state and hides card`() {
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText("Dismiss this reminder").performClick()
        compose.waitForIdle()
        compose.onNodeWithText(title).assertDoesNotExist()
        coVerify(exactly = 1) { settings.dismissScheduleAlarmReminder() }
    }
}
