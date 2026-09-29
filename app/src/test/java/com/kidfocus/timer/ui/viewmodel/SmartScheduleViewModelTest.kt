package com.kidfocus.timer.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.data.repository.RoutineRepository
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.data.schedule.RoomScheduleStore
import com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.domain.schedule.ApplyScheduleUseCase
import com.kidfocus.timer.domain.schedule.RuleId
import com.kidfocus.timer.domain.schedule.ScheduleAdvisor
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import io.mockk.*
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SmartScheduleViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val taskRows = MutableStateFlow<List<ScheduledTask>>(emptyList())
    private var viewModel: SmartScheduleViewModel? = null

    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() {
        viewModel?.viewModelScope?.cancel()
        unmockkConstructor(ScheduleAdvisor::class)
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        ageBand: String = "4-5",
        routineRows: List<RoutineEntity> = emptyList(),
        completions: List<RoutineCompletionEntity> = emptyList(),
    ): SmartScheduleViewModel {
        val profiles = mockk<ChildProfileRepository>()
        val tasks = mockk<ScheduledTaskRepository>()
        val routines = mockk<RoutineRepository>()
        val anchors = mockk<ScheduleAnchorsRepository>()
        val store = mockk<RoomScheduleStore>()
        every { profiles.activeProfile } returns flowOf(ChildProfileEntity.default().copy(ageBand = ageBand))
        every { tasks.allTasks } returns taskRows
        every { routines.observeAll() } returns flowOf(routineRows)
        every { routines.observeCompletionsSince(any<String>(), any<LocalDate>()) } returns flowOf(completions)
        every { anchors.observe(any()) } returns flowOf(ScheduleAnchors())
        coEvery { store.snapshot(any()) } returns null
        val plans = mockk<com.kidfocus.timer.data.schedule.SchedulePlansRepository>()
        every { plans.observe(any()) } returns flowOf(null)
        return SmartScheduleViewModel(profiles, tasks, routines, anchors, mockk<ApplyScheduleUseCase>(), store, mockk<com.kidfocus.timer.data.remote.ScheduleAdviser>(), plans, dayLogsFixture())
            .also { viewModel = it }
    }

    @Test fun `invalid masks are skipped while zero and all-day masks do not crash state flow`() = runTest(dispatcher) {
        val today = LocalDate.now()
        val created = today.minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val routines = listOf(-1, 128, Int.MAX_VALUE, 0, 127).mapIndexed { index, mask ->
            RoutineEntity(index + 1L, "Routine", deadlineMinutes = 420, repeatDaysMask = mask, createdAtMillis = created)
        }
        val completions = routines.map { RoutineCompletionEntity(routineId = it.id, occurrenceDate = today.toString(),
            scheduledDeadlineMillis = 0, completedAtMillis = 1, status = "LATE") }
        val vm = createViewModel(routineRows = routines, completions = completions)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        val state = vm.state.value
        assertNotNull(state)
        assertEquals(1, state!!.findings.count { it.ruleId == RuleId.MORNING_LATE_PATTERN })
    }

    @Test fun `legacy age band still yields fallback findings instead of ending state flow`() = runTest(dispatcher) {
        taskRows.value = listOf(ScheduledTask(1, TaskType.CUSTOM, "Task", "", 18, 0, setOf(2), 21, 0))
        val vm = createViewModel(ageBand = "legacy")
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertEquals(20, vm.state.value!!.findings.single { it.ruleId == RuleId.FOCUS_TOO_LONG }.params["maximum"])
    }

    @Test fun `advisor failure logs and emits empty findings then recovers on next update`() = runTest(dispatcher) {
        mockkConstructor(ScheduleAdvisor::class)
        every { anyConstructed<ScheduleAdvisor>().advise(any(), any(), any(), any(), any(), any(), any()) } throws IllegalStateException("Injected advisor failure")
        val vm = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }
        runCurrent()
        assertNotNull(vm.state.value)
        assertTrue(vm.state.value!!.findings.isEmpty())
        assertTrue(ShadowLog.getLogsForTag("SmartScheduleViewModel").any { it.type == Log.WARN && it.throwable is IllegalStateException })

        unmockkConstructor(ScheduleAdvisor::class)
        taskRows.value = listOf(ScheduledTask(1, TaskType.CUSTOM, "Task", "", 18, 0, setOf(2), 21, 0))
        runCurrent()
        assertTrue(vm.state.value!!.findings.any { it.ruleId == RuleId.FOCUS_TOO_LONG })
    }
    private fun dayLogsFixture() = mockk<com.kidfocus.timer.data.repository.DayLogRepository>().also {
        every { it.observe(any()) } returns kotlinx.coroutines.flow.MutableStateFlow(emptyList())
    }

}
