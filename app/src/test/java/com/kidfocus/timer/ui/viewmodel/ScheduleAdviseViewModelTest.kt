package com.kidfocus.timer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.repository.*
import com.kidfocus.timer.data.remote.*
import com.kidfocus.timer.data.schedule.*
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.domain.schedule.*
import io.mockk.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleAdviseViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val allDays = DayOfWeek.entries.toSet()
    private val actualRows = MutableStateFlow<List<com.kidfocus.timer.domain.daylog.DayLogEntry>>(emptyList())
    private val active = MutableStateFlow(ChildProfileEntity.default().copy(ageBand = "l1"))
    private val rows = MutableStateFlow(listOf(ScheduledTask(12, TaskType.HOMEWORK, "Bài tập", "", 20, 30, setOf(2, 3), 30, 0)))
    private val hours = MutableStateFlow(mapOf("default" to ScheduleAnchors(allDays.associateWith { LocalTime.of(6, 15) }, allDays.associateWith { LocalTime.of(21, 15) })))
    private val planRows = MutableStateFlow<Map<String, SchedulePlan?>>(emptyMap())
    private val store = mockk<RoomScheduleStore>()
    private val adviser = mockk<ScheduleAdviser>()
    private val plans = mockk<SchedulePlansRepository>()
    private var snapshot: ScheduleSnapshot? = null
    private lateinit var vm: SmartScheduleViewModel
    private fun state(id: String = "default") = ScheduleState(rows.value.filter { it.childProfileId == id }.sortedBy { it.id }, hours.value[id] ?: ScheduleAnchors())
    private fun proposal(op: String = "MOVE", ref: String = "t0", time: String = "19:30") = mapOf<String, Any?>("op" to op, "taskRef" to ref, "start" to time, "days" to listOf("MON"), "reason" to "Dời sớm", "fixes" to listOf("LATE_HOMEWORK"))
    private fun reply(proposals: List<Map<String, Any?>> = listOf(proposal())) = ScheduleAdviseReply(ScheduleAdvice("Đề xuất", proposals, emptySet()), AiUsage(8, 8, false))
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val profiles = mockk<ChildProfileRepository>()
        val tasks = mockk<ScheduledTaskRepository>()
        val routines = mockk<RoutineRepository>()
        val anchors = mockk<ScheduleAnchorsRepository>()
        every { profiles.activeProfile } returns active
        every { tasks.allTasks } returns rows
        every { routines.observeAll() } returns flowOf(emptyList())
        every { routines.observeCompletionsSince(any<String>(), any<LocalDate>()) } returns flowOf(emptyList())
        every { anchors.observe(any()) } answers { val id = firstArg<String>(); hours.map { it[id] ?: ScheduleAnchors() } }
        every { plans.observe(any()) } answers { val id = firstArg<String>(); planRows.map { it[id] } }
        coEvery { plans.get(any()) } coAnswers { planRows.value[firstArg<String>()] }
        coEvery { plans.save(any(), any()) } coAnswers { planRows.value = planRows.value + (firstArg<String>() to secondArg<SchedulePlan?>()) }
        coEvery { store.snapshot(any()) } coAnswers { snapshot }
        coEvery { store.saveSnapshot(any(), any()) } coAnswers { snapshot = secondArg() }
        coEvery { store.read(any()) } coAnswers { state(firstArg()) }
        coEvery { store.replace(any(), any(), any()) } coAnswers {
            val id = firstArg<String>(); check(state(id) == secondArg<ScheduleState>())
            val next = thirdArg<ScheduleState>()
            rows.value = rows.value.filterNot { it.childProfileId == id } + next.tasks
            hours.value = hours.value + (id to next.anchors)
        }
        every { store.reschedule(any(), any()) } just Runs
        coEvery { adviser.advise(any()) } returns reply()
        vm = SmartScheduleViewModel(profiles, tasks, routines, anchors, ApplyScheduleUseCase(store), store, adviser, plans, dayLogsFixture())
    }
    @After fun teardown() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    private fun TestScope.observe() { backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect() }; runCurrent() }
    @Test fun `preview selection apply remaining rows and undo through real use case`() = runTest(dispatcher) {
        observe()
        coEvery { adviser.advise(any()) } returns reply(listOf(proposal(), proposal(ref = "t999")))
        vm.editNote("Ghi chú"); vm.toggleTag(NoteTag.LONG_HOMEWORK)
        vm.requestAdvice(); runCurrent()
        assertEquals(AdviceRejection.INVALID, vm.advice.value.rows[1].rejection)
        assertTrue(vm.advice.value.selected.isEmpty())
        vm.select(0, true); vm.applyAdvice(vm.advice.value.selected); runCurrent()
        assertEquals(19, rows.value.single { 2 in it.daysOfWeek }.hour)
        assertEquals(1, vm.advice.value.rows.size)
        vm.undo(); runCurrent(); assertEquals(20, rows.value.single().hour)
    }
    @Test fun `REMOVE cannot commit until separate confirmation`() = runTest(dispatcher) {
        rows.value = listOf(rows.value.single().copy(taskType = TaskType.TV_TIME))
        coEvery { adviser.advise(any()) } returns reply(listOf(proposal(op = "REMOVE") - "start"))
        observe(); vm.requestAdvice(); runCurrent()
        assertNull(vm.advice.value.rows.single().rejection)
        vm.applyAdvice(setOf(0)); runCurrent(); assertEquals(setOf(2, 3), rows.value.single().daysOfWeek)
        vm.applyAdvice(setOf(0), true); runCurrent(); assertEquals(setOf(3), rows.value.single().daysOfWeek)
        vm.undo(); runCurrent(); assertEquals(setOf(2, 3), rows.value.single().daysOfWeek)
    }
    @Test fun `quota failure retains note and tags and stale schedule never applies`() = runTest(dispatcher) {
        observe(); vm.editNote("Quan sát"); vm.toggleTag(NoteTag.HARD_TO_WAKE)
        val quota = mockk<com.google.firebase.functions.FirebaseFunctionsException>()
        every { quota.code } returns com.google.firebase.functions.FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED
        coEvery { adviser.advise(any()) } throws quota
        vm.requestAdvice(); runCurrent(); assertEquals(AdviceError.QUOTA, vm.advice.value.error); assertEquals("Quan sát", vm.advice.value.note)
        coEvery { adviser.advise(any()) } returns reply()
        vm.requestAdvice(); runCurrent()
        rows.value = rows.value.map { it.copy(name = "Changed") }; runCurrent()
        vm.applyAdvice(setOf(0)); runCurrent(); assertEquals(AdviceError.STALE, vm.advice.value.error)
        assertEquals(20, rows.value.single().hour)
    }
    @Test fun `late response discarded after changing profile and switching back`() = runTest(dispatcher) {
        observe(); val pending = CompletableDeferred<ScheduleAdviseReply>()
        coEvery { adviser.advise(any()) } coAnswers { pending.await() }
        vm.requestAdvice(); runCurrent()
        active.value = active.value.copy(id = "other"); runCurrent()
        active.value = ChildProfileEntity.default().copy(ageBand = "l1"); runCurrent()
        pending.complete(reply()); runCurrent()
        assertNull(vm.advice.value.advice); assertEquals("", vm.advice.value.note)
        assertFalse(vm.busy.value)
    }
    @Test fun `far bedtime proposal starts 15 minute plan and next step requires due date and own profile`() = runTest(dispatcher) {
        rows.value = emptyList()
        val initial = hours.value.getValue("default").copy(bed = allDays.associateWith { LocalTime.of(22, 30) })
        hours.value = mapOf("default" to initial)
        val proposal = mapOf<String, Any?>("op" to "SET_BED", "days" to allDays.map { it.name.take(3) }, "start" to "21:30", "reason" to "Ngủ sớm", "fixes" to listOf("SLEEP_SHORT"))
        coEvery { adviser.advise(any()) } returns reply(listOf(proposal))
        observe(); vm.requestAdvice(); runCurrent()
        assertTrue(vm.advice.value.rows.single().requiresPlan)
        vm.applyAdvice(setOf(0)); runCurrent(); assertEquals(initial, state().anchors)
        vm.startPlan(0); runCurrent()
        assertTrue(state().anchors.bed.values.all { it == LocalTime.of(22, 15) })
        val next = planRows.value.getValue("default")!!
        assertEquals(2, next.nextStep); assertEquals(LocalDate.now().plusDays(3), next.nextOn)
        vm.applyPlanStep(); runCurrent(); assertEquals(2, planRows.value.getValue("default")!!.nextStep)
        // Due state loaded from settings; switching profile cannot apply it to another child.
        planRows.value = mapOf("default" to next.copy(startedOn = LocalDate.now().minusDays(3), nextOn = LocalDate.now()))
        runCurrent(); active.value = active.value.copy(id = "other"); runCurrent()
        assertNull(vm.state.value!!.plan); vm.applyPlanStep(); runCurrent()
        active.value = ChildProfileEntity.default().copy(ageBand = "l1"); runCurrent()
        vm.applyPlanStep(); runCurrent(); assertTrue(state().anchors.bed.values.all { it == LocalTime.of(22, 0) })
        assertEquals(3, planRows.value.getValue("default")!!.nextStep)
        vm.undo(); runCurrent(); assertTrue(state().anchors.bed.values.all { it == LocalTime.of(22, 15) })
        vm.cancelPlan(); runCurrent(); assertNull(planRows.value["default"])
    }
    @Test fun `real actual flow drives findings and ADVISE sends aggregates with transient refs only`() = runTest(dispatcher) {
        rows.value = rows.value.map { it.copy(daysOfWeek = (1..7).toSet()) }
        val today = LocalDate.now()
        actualRows.value = (1L..3L).flatMap { offset ->
            listOf(
                com.kidfocus.timer.domain.daylog.DayLogEntry(profileId = "default", date = today.minusDays(offset),
                    name = "PRIVATE_SLEEP_TEXT", category = com.kidfocus.timer.domain.daylog.DayLogCategory.SLEEP,
                    startMinute = 22 * 60, source = com.kidfocus.timer.domain.daylog.DayLogSource.AI, createdAt = 1000),
                com.kidfocus.timer.domain.daylog.DayLogEntry(profileId = "default", date = today.minusDays(offset), taskId = 12,
                    name = "PRIVATE_HOMEWORK_TEXT", category = com.kidfocus.timer.domain.daylog.DayLogCategory.STUDY,
                    startMinute = 20 * 60 + 30, endMinute = 21 * 60 + 20,
                    source = com.kidfocus.timer.domain.daylog.DayLogSource.AI, createdAt = 1000),
            )
        }
        observe()
        assertTrue(vm.state.value!!.findings.any { it.ruleId == RuleId.BED_DRIFT })
        assertTrue(vm.state.value!!.findings.any { it.ruleId == RuleId.TASK_OVERRUN })
        val payload = slot<Map<String, Any>>()
        coEvery { adviser.advise(capture(payload)) } returns reply(emptyList())
        vm.requestAdvice(); runCurrent()
        val stats = payload.captured["actualStats"] as Map<*, *>
        assertEquals(3, stats["recordedDays"]); assertEquals(3, stats["bedLateDays"])
        val task = (stats["tasks"] as List<*>).single() as Map<*, *>
        assertEquals("t0", task["taskRef"]); assertEquals(3, task["overrun"])
        assertEquals(setOf("taskRef", "completed", "overrun", "missed", "averageDelayMin"), task.keys)
        assertFalse(payload.captured.toString().contains("PRIVATE_"))
        actualRows.value.forEach { assertFalse(payload.captured.toString().contains(it.id)) }
        actualRows.value = emptyList(); runCurrent()
        assertFalse(vm.state.value!!.findings.any { it.ruleId in setOf(RuleId.BED_DRIFT, RuleId.TASK_OVERRUN, RuleId.OFTEN_SKIPPED) })
    }
    private fun dayLogsFixture() = mockk<com.kidfocus.timer.data.repository.DayLogRepository>().also {
        every { it.observe(any()) } returns actualRows
    }

}
