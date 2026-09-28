package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ApplyScheduleUseCaseTest {
    private val days = DayOfWeek.entries.toSet()
    private val anchors = ScheduleAnchors(days.associateWith { LocalTime.of(6, 15) }, days.associateWith { LocalTime.of(22, 30) })
    private val task = ScheduledTask(12, TaskType.HOMEWORK, "Homework", "", 20, 30, DayCodec.toCalendar(days), 30, 5)
    private val initial = ScheduleState(listOf(task), anchors)

    private class FakeStore(var state: ScheduleState) : ScheduleStore {
        var saved: ScheduleSnapshot? = null
        var failInsideTransaction = false
        var alarms = 0
        var transactions = 0
        override suspend fun read(profileId: String) = state
        override suspend fun snapshot(profileId: String) = saved
        override suspend fun saveSnapshot(profileId: String, snapshot: ScheduleSnapshot?) { saved = snapshot }
        override suspend fun replace(profileId: String, expected: ScheduleState, state: ScheduleState) {
            check(this.state == expected)
            transactions++
            val before = this.state
            try {
                // Simulates SQL writes followed by an error before transaction commit.
                this.state = state
                if (failInsideTransaction) error("SQL failure")
            } catch (error: Exception) {
                this.state = before
                throw error
            }
        }
        override fun reschedule(before: List<ScheduledTask>, after: List<ScheduledTask>) { alarms++ }
    }

    @Test fun `canonical sleep suggestion removes finding and undo restores exact state`() = runTest {
        val store = FakeStore(initial)
        val useCase = ApplyScheduleUseCase(store) { 10_000 }
        val changes = ScheduleAdvisor().advise(initial.tasks, anchors, "l1").filter { it.ruleId == RuleId.SLEEP_SHORT }.mapNotNull { it.suggestion }
        useCase.apply("default", initial, changes, "l1")
        assertTrue(ScheduleAdvisor().advise(store.state.tasks, store.state.anchors, "l1").none { it.ruleId == RuleId.SLEEP_SHORT })
        assertEquals(1, store.transactions)
        assertEquals(1, store.alarms)
        useCase.undo("default")
        assertEquals(initial, store.state)
        assertNull(store.saved)
        assertEquals(2, store.alarms)
    }
    @Test fun `transaction failure preserves data alarms and previous snapshot`() = runTest {
        val store = FakeStore(initial)
        val previous = ScheduleSnapshot(initial, initial, 100)
        store.saved = previous
        store.failInsideTransaction = true
        val useCase = ApplyScheduleUseCase(store) { 10_000 }
        assertTrue(runCatching { useCase.saveAnchors("default", initial, anchors.copy(commuteMinutes = 30)) }.isFailure)
        assertEquals(initial, store.state)
        assertEquals(previous, store.saved)
        assertEquals(0, store.alarms)
    }
    @Test fun `moving one recurring day splits while retaining the other days and undo removes split`() = runTest {
        val store = FakeStore(initial)
        val useCase = ApplyScheduleUseCase(store) { 10_000 }
        useCase.apply("default", initial, listOf(ScheduleChange.MoveTask(12, setOf(DayOfWeek.MONDAY), LocalTime.of(19, 0))), "l1")
        assertEquals(2, store.state.tasks.size)
        assertEquals(task.daysOfWeek - DayCodec.toCalendar(DayOfWeek.MONDAY), store.state.tasks.single { it.id == 12L }.daysOfWeek)
        assertTrue(store.state.tasks.single { it.id != 12L }.id > Int.MAX_VALUE)
        useCase.undo("default")
        assertEquals(initial, store.state)
    }
    @Test fun `focus resize applies only selected day and undo restores original duration`() = runTest {
        val long = initial.copy(tasks = listOf(task.copy(focusDurationMinutes = 60)))
        val store = FakeStore(long)
        val useCase = ApplyScheduleUseCase(store) { 10_000 }
        useCase.apply("default", long, listOf(ScheduleChange.ResizeTask(12, setOf(DayOfWeek.MONDAY), 30)), "l1")
        assertEquals(30, store.state.tasks.single { it.id != 12L }.focusDurationMinutes)
        assertEquals(60, store.state.tasks.single { it.id == 12L }.focusDurationMinutes)
        useCase.undo("default")
        assertEquals(long, store.state)
    }
    @Test fun `stale draft and undo cannot overwrite newer changes`() = runTest {
        val store = FakeStore(initial)
        val useCase = ApplyScheduleUseCase(store) { 10_000 }
        useCase.saveAnchors("default", initial, anchors.copy(commuteMinutes = 30))
        assertTrue(runCatching { useCase.saveAnchors("default", initial, anchors) }.isFailure)
        store.state = store.state.copy(tasks = listOf(task.copy(name = "Updated")))
        assertTrue(runCatching { useCase.undo("default") }.isFailure)
        assertEquals("Updated", store.state.tasks.single().name)
    }
    @Test fun `expired snapshot is not restored`() = runTest {
        val store = FakeStore(initial)
        var clock = 10_000L
        val useCase = ApplyScheduleUseCase(store) { clock }
        useCase.saveAnchors("default", initial, anchors.copy(commuteMinutes = 30))
        clock += 604_800_001
        assertTrue(runCatching { useCase.undo("default") }.isFailure)
        assertEquals(30, store.state.anchors.commuteMinutes)
    }
    @Test fun `suggestion introducing overlap rejected before writing snapshot`() = runTest {
        val crowded = initial.copy(tasks = listOf(task, task.copy(id = 13, hour = 19, minute = 0)))
        val store = FakeStore(crowded)
        assertTrue(runCatching { ApplyScheduleUseCase(store).apply("default", crowded,
            listOf(ScheduleChange.MoveTask(12, days, LocalTime.of(19, 0))), "l1") }.isFailure)
        assertNull(store.saved)
        assertEquals(crowded, store.state)
    }
    @Test fun `profile scope is validated before any write`() = runTest {
        val store = FakeStore(initial)
        assertTrue(runCatching { ApplyScheduleUseCase(store).saveAnchors("another", initial, anchors) }.isFailure)
        assertNull(store.saved)
    }
}
