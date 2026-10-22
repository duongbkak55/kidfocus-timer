package com.kidfocus.timer.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.*
import androidx.navigation.testing.TestNavHostController
import com.kidfocus.timer.R
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.CloudSyncStatus
import com.kidfocus.timer.data.repository.SettingsRepository
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.domain.usecase.*
import com.kidfocus.timer.ui.screens.TodayActivityScreen
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.*
import io.mockk.*
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = ParentNavigationTestApplication::class, qualifiers = "en-w411dp-h891dp")
class UxParentRoutesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var settings: SettingsViewModel
    private lateinit var nav: TestNavHostController
    private val account = mockk<CloudSyncViewModel>(relaxed = true)
    private val schedule = mockk<ScheduleViewModel>(relaxed = true)
    private val timer = mockk<TimerViewModel>(relaxed = true)
    private val logs = mockk<DayLogViewModel>(relaxed = true)
    private val permission = mockk<ScheduleAlarmPermissionViewModel>(relaxed = true)
    private val task = ScheduledTask(47, TaskType.HOMEWORK, "Second homework", "📝", 17, 45,
        setOf(2, 4), 25, 5, enabled = false)

    @Before fun setup() {
        val repository = mockk<SettingsRepository>()
        every { repository.settingsFlow } returns MutableStateFlow(TimerSettings(pinHash = "hash", onboardingCompleted = true))
        every { repository.verifyPin(any(), "hash") } answers { firstArg<String>() == "2468" }
        settings = SettingsViewModel(GetTimerSettingsUseCase(repository), SaveTimerSettingsUseCase(repository), repository)
        every { account.account } returns MutableStateFlow(CloudAccount(true, "test-parent", "parent@example.test"))
        every { account.syncStatus } returns MutableStateFlow(CloudSyncStatus.LocalOnly)
        every { account.oldAppWarning } returns MutableStateFlow(false)
        every { account.formMessage } returns MutableStateFlow(null)
        every { account.working } returns MutableStateFlow(false)
        every { schedule.tasks } returns MutableStateFlow(listOf(task))
        every { schedule.anchors } returns MutableStateFlow(com.kidfocus.timer.domain.schedule.ScheduleAnchors())
        every { logs.data } returns MutableStateFlow(DayLogData(profileId = "default"))
        every { permission.reminderVisible } returns MutableStateFlow(false)
        every { permission.settingsUnavailable } returns MutableStateFlow(false)
        every { schedule.taskFromType(any()) } answers { task.copy(id = 0, taskType = firstArg()) }
    }
    @After fun teardown() { settings.viewModelScope.cancel() }

    private fun graph(destination: String) {
        compose.setContent {
            val current by settings.settings.collectAsState()
            if (current != null) {
                val context = LocalContext.current
                nav = remember { TestNavHostController(context).apply { navigatorProvider.addNavigator(ComposeNavigator()) } }
                ParentSessionLifecycle(settings)
                ParentSessionNavigation(nav, settings)
                KidFocusTheme {
                    NavHost(nav, startDestination = NavRoutes.Home.route) {
                        composable(NavRoutes.Home.route) { Button(onClick = { nav.navigate(NavRoutes.CloudSync.route) }) { Text("Account entry") } }
                        composable(NavRoutes.PinSetup.route) { Text("Set PIN first") }
                        composable(NavRoutes.Focus.route) { Text("Focus") }
                        childDailyScheduleDestinations(nav, schedule, logs, timer, settings, permission, false)
                        parentPinEntry(nav, settings)
                        parentAccountDestination(nav, settings, account)
                        parentTaskEditorDestination(nav, settings, schedule)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { nav.navigate(destination) }
        compose.waitForIdle()
    }
    private fun pin(value: String = "2468") {
        value.forEach { compose.onNodeWithText(it.toString()).performClick() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
    }

    @Test fun accountEntryHidesAllAccountOperationsUntilRealPinSucceeds() {
        graph(NavRoutes.CloudSync.route)
        compose.onNodeWithText("parent@example.test").assertDoesNotExist()
        compose.onNodeWithText("Đăng xuất").assertDoesNotExist()
        compose.onNodeWithText("Xóa tài khoản và dữ liệu cloud").assertDoesNotExist()
        pin("0000")
        compose.onNodeWithText("Incorrect PIN").assertIsDisplayed()
        verify(exactly = 0) { account.signOut(); account.deleteAccount(); account.syncNow() }
        pin()
        compose.onNodeWithText("parent@example.test").assertExists()
        compose.onNodeWithText("Đăng xuất").performScrollTo().performClick()
        verify(exactly = 1) { account.signOut() }
    }

    @Test fun deleteCloudNeedsUnlockedScreenAndExplicitConfirmationAndRelocks() {
        graph(NavRoutes.CloudSync.route); pin()
        compose.onNodeWithText("Xóa tài khoản và dữ liệu cloud").performScrollTo().performClick()
        verify(exactly = 0) { account.deleteAccount() }
        compose.runOnIdle { settings.lockParentSession() }
        compose.waitForIdle()
        compose.onNodeWithText("Xóa vĩnh viễn").assertDoesNotExist()
        pin()
        compose.onNodeWithText("Xóa tài khoản và dữ liệu cloud").performScrollTo().performClick()
        compose.onNodeWithText("Xóa vĩnh viễn").performClick()
        verify(exactly = 1) { account.deleteAccount() }
    }

    @Test fun returningToHomeRequiresPinAgainEvenFromDirectAccountEntry() {
        graph(NavRoutes.CloudSync.route); pin()
        compose.runOnIdle { nav.popBackStack() }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(settings.parentUnlocked.value) }
        compose.onNodeWithText("Account entry").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.pin_verify_title)).assertIsDisplayed()
    }

    @Test fun noPinCannotExposeAccountScreen() {
        val repo = mockk<SettingsRepository>()
        every { repo.settingsFlow } returns MutableStateFlow(TimerSettings(onboardingCompleted = true))
        settings.viewModelScope.cancel()
        settings = SettingsViewModel(GetTimerSettingsUseCase(repo), SaveTimerSettingsUseCase(repo), repo)
        graph(NavRoutes.CloudSync.route)
        compose.onNodeWithText("Set PIN first").assertIsDisplayed()
        compose.onNodeWithText("parent@example.test").assertDoesNotExist()
    }

    @Test fun childTodayActivityStartsSessionWithoutWritingWeeklyPlan() {
        graph(NavRoutes.TodayActivity.route)
        compose.onNodeWithTag("task_edit_name").assertDoesNotExist()
        compose.onNodeWithTag("today_activity_name").performTextInput("Draw a cat")
        compose.onNodeWithTag("today_activity_start").performScrollTo().performClick()
        verify(exactly = 1) { timer.startFocusForActivity("Draw a cat", 25 * 60) }
        verify(exactly = 0) { schedule.saveTask(any()) }
    }

    @Test fun dailyGapOnTodayPastOrFutureDayAlwaysOpensTodaySessionNotWeeklyEditor() {
        every { schedule.tasks } returns MutableStateFlow(listOf(task.copy(enabled = true, daysOfWeek = (1..7).toSet())))
        graph(NavRoutes.DailySchedule.route)
        for (direction in listOf(null, R.string.daily_next_day, R.string.daily_previous_day)) {
            if (direction != null) compose.onNodeWithContentDescription(compose.activity.getString(direction)).performClick()
            compose.onAllNodesWithTag("daily_gap_add").onFirst().performClick()
            compose.onNodeWithTag("today_activity_name").assertExists()
            compose.onNodeWithTag("task_edit_name").assertDoesNotExist()
            verify(exactly = 0) { schedule.saveTask(any()) }
            compose.runOnIdle { nav.popBackStack() }
            compose.waitForIdle()
        }
    }

    @Test fun weeklyChoiceFromChildActivityCannotOpenEditorWithoutPin() {
        graph(NavRoutes.TodayActivity.route)
        compose.onNodeWithTag("today_activity_weekly").performScrollTo().performClick()
        compose.onNodeWithTag("task_edit_name").assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(R.string.pin_verify_title)).assertIsDisplayed()
        pin()
        compose.onNodeWithTag("task_edit_name").assertExists()
        verify(exactly = 0) { schedule.saveTask(any()) }
    }

    @Test fun newParentEditorExplicitlyOffersWeeklyOrTodaySession() {
        graph(NavRoutes.TaskEdit.buildRoute(0, TaskType.CUSTOM.name)); pin()
        compose.onNodeWithText(compose.activity.getString(R.string.activity_scope_weekly)).assertExists()
        compose.onNodeWithTag("task_choose_today_session").performScrollTo().performClick()
        compose.onNodeWithTag("today_activity_name").assertExists()
        compose.onNodeWithTag("task_edit_name").assertDoesNotExist()
        verify(exactly = 0) { schedule.saveTask(any()) }
    }

    @Test fun restoredParameterizedEditorNeedsPinAndKeepsExactIdAndDisabledState() {
        graph(NavRoutes.TaskEdit.buildRoute(47, TaskType.HOMEWORK.name, 10, 15))
        compose.onNodeWithTag("task_edit_name").assertDoesNotExist()
        pin()
        compose.onNodeWithTag("task_edit_name").performTextReplacement("Renamed second")
        compose.onNodeWithText(compose.activity.getString(R.string.schedule_save)).performScrollTo().performClick()
        verify(exactly = 1) { schedule.saveTask(match {
            it.id == 47L && it.name == "Renamed second" && it.hour == 10 && it.minute == 15 && !it.enabled
        }) }
    }
}
