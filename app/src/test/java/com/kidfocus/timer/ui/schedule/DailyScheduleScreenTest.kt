package com.kidfocus.timer.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import com.kidfocus.timer.ui.screens.DailyScheduleScreen
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ScheduleAlarmPermissionViewModel
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = ParentNavigationTestApplication::class, qualifiers = "en-w411dp-h891dp")
class DailyScheduleScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun childKeepsWeekStripTaskVisualAndOnlyTheParentEntryLink() {
        val task = ScheduledTask(42, TaskType.HOMEWORK, "Child homework", "📝", 9, 0,
            (1..7).toSet(), 25, 5)
        val schedule = mockk<ScheduleViewModel>()
        every { schedule.tasks } returns MutableStateFlow(listOf(task))
        every { schedule.anchors } returns MutableStateFlow(ScheduleAnchors())
        val permission = mockk<ScheduleAlarmPermissionViewModel>(relaxed = true)
        every { permission.reminderVisible } returns MutableStateFlow(false)
        every { permission.settingsUnavailable } returns MutableStateFlow(false)
        var openedDate: LocalDate? = null
        compose.setContent {
            KidFocusTheme {
                DailyScheduleScreen(schedule, onBack = {}, onStartTask = {},
                    alarmPermission = permission, onActualAndComparison = { openedDate = it })
            }
        }
        compose.onNodeWithTag("daily_week_strip").assertIsDisplayed()
        compose.onNodeWithTag("daily_task_visual_42").assertIsDisplayed()
        for (label in listOf(R.string.daylog_add_incidental, R.string.daylog_sleep, R.string.daylog_wake,
            R.string.daylog_edit_plan, R.string.daylog_compare_title, R.string.daylog_actual)) {
            compose.onNodeWithText(compose.activity.getString(label)).assertDoesNotExist()
        }
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.daily_next_day)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.daylog_actual_and_compare)).performClick()
        compose.runOnIdle { assertEquals(LocalDate.now().plusDays(1), openedDate) }
    }
}
