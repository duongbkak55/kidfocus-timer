package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.repository.DayLogRepository
import com.kidfocus.timer.data.remote.*
import com.kidfocus.timer.domain.daylog.*
import com.kidfocus.timer.domain.model.*
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class QuickDayLogViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val logs=mockk<DayLogRepository>(relaxed=true)
    private val logger=mockk<ScheduleLogger>()
    private val today=LocalDate.parse("2026-09-29")
    private val task=ScheduledTask(42,TaskType.HOMEWORK,"Homework","x",19,0,(1..7).toSet(),30,5)
    private val data=DayLogData("child",listOf(task),ScheduleAnchors())
    private var active:String?="child"
    private lateinit var vm:QuickDayLogViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher);coEvery { logs.activeProfileId() } coAnswers { active }
        coEvery { logger.log(any()) } returns ScheduleLogReply(DayLogPreview(listOf(
            DayLogCandidate(today,"p0","Homework",DayLogCategory.STUDY,1140,1170,.9),
            DayLogCandidate(today,null,"Extra",DayLogCategory.OTHER,1200,null,.4)),listOf("Confirm extra time")),AiUsage(9,9,false))
        vm=QuickDayLogViewModel(logger,logs);vm.context("child",today);vm.editText("Làm bài lúc 19h, chơi lúc 20h")
    }
    @After fun close() { vm.viewModelScope.cancel();Dispatchers.resetMain() }
    @Test fun previewSelectionSaveSourceAiAndUndoBatchUseTheFrozenProfileAndTask()=runTest(dispatcher) {
        vm.preview(data,today);runCurrent();assertEquals(setOf(0),vm.state.value.selected)
        vm.save(data);runCurrent();assertTrue(vm.state.value.saved)
        coVerify(exactly=1) { logs.addAiBatch(match { it.size==1 && it[0].taskId==42L && it[0].profileId=="child" && it[0].source==DayLogSource.AI }) }
        val batch=vm.state.value.undoBatch;vm.save(data);runCurrent();coVerify(exactly=1) { logs.addAiBatch(any()) }
        vm.undo();runCurrent();coVerify { logs.undoAiBatch(batch) };assertTrue(vm.state.value.restored);assertTrue(vm.state.value.undoBatch.isEmpty())
    }
    @Test fun stalePlanAndProfileChangeNeverSaveAnOldPreview()=runTest(dispatcher) {
        vm.preview(data,today);runCurrent();vm.save(data.copy(tasks=listOf(task.copy(hour=18))))
        assertEquals(LogError.STALE,vm.state.value.error);coVerify(exactly=0) { logs.addAiBatch(any()) }
        active="other";vm.context("other",today);assertNull(vm.state.value.preview);assertTrue(vm.state.value.undoBatch.isEmpty())
        vm.save(data);runCurrent();coVerify(exactly=0) { logs.addAiBatch(any()) }
    }
    @Test fun pendingResponseFromAnotherDateIsDiscardedAndInputIsBounded()=runTest(dispatcher) {
        val pending=CompletableDeferred<ScheduleLogReply>();coEvery { logger.log(any()) } coAnswers { pending.await() }
        vm.preview(data,today);runCurrent();vm.context("child",today.minusDays(1))
        pending.complete(ScheduleLogReply(DayLogPreview(emptyList(),emptyList()),AiUsage(9,9,false)));runCurrent()
        assertNull(vm.state.value.preview);assertFalse(vm.state.value.busy)
        vm.editText("x".repeat(2001));assertEquals(2000,vm.state.value.text.length)
    }
    @Test fun noProfileAndNetworkFailureCreateNoEntries()=runTest(dispatcher) {
        vm.preview(data.copy(profileId=null),today);coVerify(exactly=0) { logger.log(any()) }
        coEvery { logger.log(any()) } throws IllegalStateException("No network")
        vm.preview(data,today);runCurrent();assertEquals(LogError.NETWORK,vm.state.value.error);assertFalse(vm.state.value.busy)
        coVerify(exactly=0) { logs.addAiBatch(any()) }
    }
    @Test fun profileSwitchDuringWriteCannotExposeTheOtherChildUndoAndStaleUndoPreservesBatch()=runTest(dispatcher) {
        vm.preview(data,today);runCurrent()
        val pending=CompletableDeferred<Unit>()
        coEvery { logs.addAiBatch(any()) } coAnswers { pending.await() }
        vm.save(data);runCurrent();active="other";vm.context("other",today)
        pending.complete(Unit);runCurrent()
        assertFalse(vm.state.value.saved);assertTrue(vm.state.value.undoBatch.isEmpty());assertFalse(vm.state.value.busy)
        active="child";vm.context("child",today);vm.editText("Làm bài lúc 19h")
        coEvery { logs.addAiBatch(any()) } returns Unit
        vm.preview(data,today);runCurrent();vm.save(data);runCurrent()
        val batch=vm.state.value.undoBatch
        coEvery { logs.undoAiBatch(any()) } throws IllegalStateException("STALE")
        vm.undo();runCurrent();assertEquals(LogError.STALE,vm.state.value.error)
        assertEquals(batch,vm.state.value.undoBatch);assertFalse(vm.state.value.restored)
    }

    @Test fun changingAccountDiscardsPendingPreviewAndOldCreditUsage()=runTest(dispatcher) {
        val pending=CompletableDeferred<ScheduleLogReply>()
        coEvery { logger.log(any()) } coAnswers { pending.await() }
        vm.context("child",today,"account-a");vm.editText("Làm bài lúc 19h")
        vm.preview(data,today);runCurrent();vm.context("child",today,"account-b")
        pending.complete(ScheduleLogReply(DayLogPreview(emptyList(),emptyList()),AiUsage(9,9,false)));runCurrent()
        assertEquals("account-b",vm.state.value.ownerId)
        assertNull(vm.state.value.preview);assertNull(vm.state.value.usage);assertFalse(vm.state.value.busy)
        coVerify(exactly=0) { logs.addAiBatch(any()) }
    }

    @Test fun futureHighConfidenceEntryStartsUncheckedAndIsLabeledForReview()=runTest(dispatcher) {
        val currentDate = LocalDate.now()
        val future = DayLogCandidate(currentDate.plusDays(1), null, "Tomorrow", DayLogCategory.OTHER, 0, null, .95)
        coEvery { logger.log(any()) } returns ScheduleLogReply(DayLogPreview(listOf(future), emptyList()), AiUsage(9,9,false))
        vm.context("child",currentDate);vm.editText("Ngày mai chơi lúc 0h")
        vm.preview(data,currentDate);runCurrent()
        assertEquals(setOf(0),vm.state.value.future)
        assertTrue(vm.state.value.selected.isEmpty())
        vm.select(0,true);assertEquals(setOf(0),vm.state.value.selected)
        vm.editText("change");assertTrue(vm.state.value.future.isEmpty())
    }

}
