package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.repository.*
import com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository
import com.kidfocus.timer.data.daylog.DayLogSyncManager
import com.kidfocus.timer.domain.daylog.*
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class DayLogViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val logs=mockk<DayLogRepository>(relaxed=true)
    private val profiles=mockk<ChildProfileRepository>()
    private val active=MutableStateFlow("default")
    private val rows=MutableStateFlow(listOf(ChildProfileEntity.default(),ChildProfileEntity.default().copy(id="other")))
    private lateinit var vm: DayLogViewModel
    private val plan=DayPlanItem("task:42",LocalDate.parse("2026-09-28"),42,"Homework",DayLogCategory.STUDY,1080,30)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { profiles.activeProfileId } returns active
        every { profiles.profiles } returns rows
        coEvery { logs.activeProfileId() } coAnswers { active.value.takeIf { rows.value.any { p -> p.id == it } } }
        every { logs.observe(any()) } returns MutableStateFlow(emptyList())
        val tasks=mockk<ScheduledTaskRepository>()
        every { tasks.allTasks } returns MutableStateFlow(emptyList())
        val anchors=mockk<ScheduleAnchorsRepository>()
        every { anchors.all } returns MutableStateFlow(mapOf("default" to ScheduleAnchors()))
        val sync=mockk<DayLogSyncManager>()
        every { sync.error } returns MutableStateFlow(false)
        vm=DayLogViewModel(logs,profiles,tasks,anchors,sync)
    }
    @After fun close() { vm.viewModelScope.cancel();Dispatchers.resetMain() }
    @Test fun planDefaultsThenSavesActualOnlyAndKeepsTaskReference()=runTest(dispatcher) {
        runCurrent();vm.openPlan(plan)
        assertEquals(1080,vm.draft.value!!.startMinute);assertEquals(1110,vm.draft.value!!.endMinute)
        vm.save("Homework","18:15","19:00",DayLogCategory.STUDY);runCurrent()
        coVerify { logs.add(match { it.taskId==42L && it.startMinute==1095 && it.endMinute==1140 && it.source==DayLogSource.MANUAL }) }
        assertNull(vm.draft.value)
    }
    @Test fun invalidClockDoesNotWriteAndProfileSwitchCannotSaveTheOldDraft()=runTest(dispatcher) {
        runCurrent();vm.openPlan(plan)
        vm.save("Homework","24:00","19:00",DayLogCategory.STUDY)
        assertEquals(DayLogError.INVALID,vm.error.value)
        coVerify(exactly=0) { logs.add(any()) }
        vm.save("Homework","18:00","19:00",DayLogCategory.STUDY)
        active.value="other";runCurrent()
        coVerify(exactly=0) { logs.add(any()) }
    }
    @Test fun manualEditAndDeleteUseExistingUuidAndProfileAndNoProfileCannotPrepareEntry()=runTest(dispatcher) {
        runCurrent();vm.openPlan(plan);val entry=vm.draft.value!!
        vm.openActual(entry);vm.save("Renamed","23:50","00:10",DayLogCategory.STUDY);runCurrent()
        coVerify { logs.edit(match { it.id==entry.id && it.date==entry.date && it.endMinute==10 }) }
        vm.openActual(entry);vm.deleteDraft();runCurrent()
        coVerify { logs.delete(entry.id,"default") }
        rows.value=emptyList();runCurrent();vm.openNew(DayLogCategory.SLEEP,"Sleep")
        assertNull(vm.draft.value);assertEquals(DayLogError.NO_PROFILE,vm.error.value)
    }
}
