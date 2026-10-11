package com.kidfocus.timer.ui.screens

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.domain.model.RoutineStatus
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.domain.model.TodayRoutine
import com.kidfocus.timer.ui.components.TodayRoutineCard
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.HomeViewModel
import com.kidfocus.timer.ui.viewmodel.RoutineViewModel
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import com.kidfocus.timer.ui.viewmodel.TimerViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "en-rUS")
class RoutineFocusEntryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val routine = TodayRoutine(
        routine = RoutineEntity(id = 17, title = "Read a book", emoji = "📖",
            deadlineMinutes = 1200, repeatDaysMask = 127, linkedTimerMinutes = null),
        occurrenceDate = LocalDate.of(2026, 10, 11), deadlineMillis = 0,
        status = RoutineStatus.PENDING,
    )

    @Test fun untimedRoutineOffersFocusAndDoneAsSeparateActions() {
        var focused: TodayRoutine? = null
        var completed: TodayRoutine? = null
        compose.setContent { KidFocusTheme {
            TodayRoutineCard(routine, onComplete = { completed = it }, onStartFocus = { focused = it })
        } }
        compose.onNodeWithTag("routine_focus_17").performClick()
        compose.runOnIdle {
            assertEquals(routine, focused)
            assertEquals(null, completed)
        }
        compose.onNodeWithTag("routine_done_17").performClick()
        compose.runOnIdle { assertEquals(routine, completed) }
    }

    @Test fun startFocusPickerIncludesAnExistingUntimedRoutine() {
        val timer = mockk<TimerViewModel>(relaxed = true)
        var navigations = 0
        showHome(timer) { navigations++ }
        compose.onNodeWithText("Start focusing!").performScrollTo().performClick()
        compose.onNodeWithTag("focus_routine_17").performClick()
        compose.onNodeWithTag("focus_activity_next").performClick()
        compose.onNodeWithTag("focus_start_selected").performClick()
        verify(exactly = 1) { timer.startFocusForRoutine(17, routine.occurrenceDate, 25 * 60, "Read a book") }
        compose.runOnIdle { assertEquals(1, navigations) }
    }

    @Test fun tappingAnExistingRoutineCardStartsFocusWithoutMarkingItDone() {
        val timer = mockk<TimerViewModel>(relaxed = true)
        var navigations = 0
        showHome(timer) { navigations++ }
        compose.onNodeWithTag("routine_focus_17").performScrollTo().performClick()
        compose.onNodeWithTag("focus_start_selected").performClick()
        verify(exactly = 1) { timer.startFocusForRoutine(17, routine.occurrenceDate, 25 * 60, "Read a book") }
        compose.runOnIdle { assertEquals(1, navigations) }
    }

    private fun showHome(timer: TimerViewModel, onNavigate: () -> Unit) {
        val settings = mockk<SettingsViewModel>()
        every { settings.settings } returns MutableStateFlow(TimerSettings())
        val schedule = mockk<ScheduleViewModel>()
        every { schedule.tasks } returns MutableStateFlow(emptyList())
        val home = mockk<HomeViewModel>()
        every { home.todayFocusMinutes } returns MutableStateFlow(0)
        every { home.todayFocusCount } returns MutableStateFlow(0)
        every { home.dailyGoalProgress } returns MutableStateFlow(0f)
        val routines = mockk<RoutineViewModel>()
        every { routines.todayRoutines } returns MutableStateFlow(listOf(routine))
        compose.setContent { KidFocusTheme {
            HomeScreen(timer, settings, schedule, onStartFocus = onNavigate,
                onOpenTheme = {}, onOpenParentSettings = {}, homeViewModel = home,
                routineViewModel = routines)
        } }
    }
}
