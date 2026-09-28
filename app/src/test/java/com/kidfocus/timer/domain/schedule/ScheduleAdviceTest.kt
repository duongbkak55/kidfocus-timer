package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import java.time.DayOfWeek.*
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScheduleAdviceTest {
    private val task = ScheduledTask(12, TaskType.HOMEWORK, "Bài tập", "", 20, 30, setOf(2, 3), 30, 0)
    private val anchors = ScheduleAnchors(DayOfWeekAll.associateWith { LocalTime.of(6, 15) }, DayOfWeekAll.associateWith { LocalTime.of(21, 15) })
    private val initial = ScheduleState(listOf(task), anchors)
    private val store = FakeStore(initial)
    private val useCase = ApplyScheduleUseCase(store) { 1_000 }
    private fun move(ref: String = "t0", time: String = "19:30", days: List<String> = listOf("MON")) =
        mapOf<String, Any?>("op" to "MOVE", "taskRef" to ref, "days" to days, "start" to time, "reason" to "Dời sớm", "fixes" to listOf("LATE_HOMEWORK"))
    private fun review(state: ScheduleState = initial, rows: List<Map<String, Any?>>) = ScheduleAdviceReview.review(ScheduleAdvice("Tóm tắt", rows, emptySet()), ScheduleAdviceReview.refs(state), "default", state, "l1", useCase)

    @Test fun `dry run filters invalid references fields new high school and protected study without writing`() {
        val occupied = initial.copy(tasks = initial.tasks + task.copy(id = 13, hour = 18, minute = 0), anchors = anchors.copy(school = listOf(Block(setOf(MONDAY), LocalTime.of(7, 0), LocalTime.of(16, 0), "Trường"))))
        val rows = review(occupied, listOf(move(), move("t999"), move(time = "18:00"), move(time = "08:00"), move() - "start",
            mapOf("op" to "REMOVE", "taskRef" to "t0", "days" to listOf("MON"), "reason" to "Xoá", "fixes" to emptyList<String>())))
        assertNull(rows[0].rejection)
        assertTrue(RuleId.LATE_HOMEWORK in rows[0].fixed)
        assertEquals(AdviceRejection.INVALID, rows[1].rejection)
        assertEquals(AdviceRejection.HIGH_INCREASED, rows[2].rejection)
        assertNotNull(rows[3].rejection)
        assertEquals(AdviceRejection.INVALID, rows[4].rejection)
        assertEquals(AdviceRejection.STUDY_PROTECTED, rows[5].rejection)
        assertNull(store.saved); assertEquals(0, store.transactions)
    }
    @Test fun `combined selected rows revalidate even when individual proposals are valid`() {
        val second = task.copy(id = 13, hour = 18, minute = 0)
        val state = initial.copy(tasks = listOf(task, second))
        val rows = review(state, listOf(move(time = "19:00"), move("t1", "19:00")))
        assertTrue(rows.all { it.rejection == null })
        assertTrue(runCatching { useCase.previewAdvice("default", state, rows.flatMap { it.changes }, "l1") }.isFailure)
        assertNull(store.saved)
    }
    @Test fun `conflicting selected times cannot silently overwrite each other`() {
        val rows = review(rows = listOf(move(time = "19:00"), move(time = "19:30")))
        assertTrue(rows.all { it.rejection == null })
        assertTrue(runCatching { useCase.previewAdvice("default", initial, rows.flatMap { it.changes }, "l1") }.isFailure)
        assertTrue(runCatching { useCase.previewAdvice("default", initial, listOf(ScheduleChange.SetBed(mapOf(MONDAY to LocalTime.of(21, 0))), ScheduleChange.SetBed(mapOf(MONDAY to LocalTime.of(21, 10)))), "l1") }.isFailure)
        assertNull(store.saved)
    }
    @Test fun `multi-row move resize remove and wake use one commit with exact undo`() = runTest {
        val tv = task.copy(id = 13, taskType = TaskType.TV_TIME, hour = 17, minute = 0)
        val before = initial.copy(tasks = listOf(task, tv))
        store.state = before
        val changes = listOf(ScheduleChange.MoveTask(12, setOf(MONDAY), LocalTime.of(19, 0)), ScheduleChange.ResizeTask(12, setOf(MONDAY), 20),
            ScheduleChange.RemoveTask(13, setOf(MONDAY)), ScheduleChange.SetWake(mapOf(TUESDAY to LocalTime.of(6, 30))))
        useCase.applyAdvice("default", before, changes, "l1")
        assertEquals(1, store.transactions)
        val moved = store.state.tasks.single { it.taskType == TaskType.HOMEWORK && 2 in it.daysOfWeek }
        assertEquals(19, moved.hour); assertEquals(20, moved.focusDurationMinutes)
        assertEquals(setOf(3), store.state.tasks.single { it.id == 13L }.daysOfWeek)
        useCase.undo("default"); assertEquals(before, store.state)
    }
    @Test fun `remaining proposals keep references after split individual apply`() = runTest {
        val refs = ScheduleAdviceReview.refs(initial)
        val move = ScheduleAdviceReview.changes(move(), refs)
        useCase.applyAdvice("default", initial, move, "l1")
        val rebased = ScheduleAdviceReview.rebase(refs, initial, store.state, move)
        val resize = mapOf<String, Any?>("op" to "RESIZE", "taskRef" to "t0", "days" to listOf("MON"), "durationMin" to 15, "reason" to "Ngắn hơn", "fixes" to listOf("FOCUS_TOO_LONG"))
        val row = ScheduleAdviceReview.review(ScheduleAdvice("", listOf(resize), emptySet()), rebased, "default", store.state, "l1", useCase).single()
        assertNull(row.rejection)
        useCase.applyAdvice("default", store.state, row.changes, "l1")
        assertEquals(15, store.state.tasks.single { 2 in it.daysOfWeek }.focusDurationMinutes)
        assertEquals(30, store.state.tasks.single { 3 in it.daysOfWeek }.focusDurationMinutes)
    }
    @Test fun `ADVISE cannot import anchors or move task into existing school conflict`() {
        assertTrue(runCatching { useCase.previewAdvice("default", initial, listOf(ScheduleChange.SetAnchors(anchors)), "l1") }.isFailure)
        val school = anchors.copy(school = listOf(Block(setOf(SUNDAY), LocalTime.of(23, 0), LocalTime.of(8, 0), "Trường")))
        val state = initial.copy(tasks = listOf(task.copy(hour = 7, minute = 0)), anchors = school)
        val row = review(state, listOf(move(time = "07:30"))).single()
        assertEquals(AdviceRejection.SCHOOL_PROTECTED, row.rejection)
    }
    @Test fun `after-midnight bed checks next calendar days fixed school block`() {
        val midnight = initial.copy(anchors = anchors.copy(bed = mapOf(MONDAY to LocalTime.of(23, 30)), school = listOf(Block(setOf(TUESDAY), LocalTime.MIDNIGHT, LocalTime.of(2, 0), "Trường"))))
        val change = mapOf<String, Any?>("op" to "SET_BED", "days" to listOf("MON"), "start" to "00:30", "reason" to "Giờ ngủ", "fixes" to listOf("SLEEP_SHORT"))
        assertEquals(AdviceRejection.SCHOOL_PROTECTED, review(midnight, listOf(change)).single().rejection)
    }
    @Test fun `compact payload omits ids photos school labels and sends fixed routine counts`() {
        val state = initial.copy(tasks = listOf(task.copy(id = 987654321234567, photoUri = "private")), anchors = anchors.copy(school = listOf(Block(setOf(MONDAY), LocalTime.of(7, 0), LocalTime.of(12, 0), "PRIVATE SCHOOL"))))
        val payload = ScheduleAdvicePayload.build(state, ScheduleAdviceReview.refs(state), ScheduleAdvisor().advise(state.tasks, state.anchors, "l1"), listOf(RoutineStat("Sáng", 3)), "l1", "2026-09-28", "vi-VN", "Ghi chú", setOf(NoteTag.HARD_TO_WAKE), "request-id")
        val serialized = payload.toString()
        assertFalse(serialized.contains("987654321234567")); assertFalse(serialized.contains("private")); assertFalse(serialized.contains("PRIVATE SCHOOL")); assertFalse(serialized.contains("profile"))
        assertTrue(serialized.contains("lateOrMissedLast7=3")); assertTrue(serialized.contains("ref=t0"))
    }
    private class FakeStore(var state: ScheduleState) : ScheduleStore {
        var saved: ScheduleSnapshot? = null
        var transactions = 0
        override suspend fun read(profileId: String) = state
        override suspend fun snapshot(profileId: String) = saved
        override suspend fun saveSnapshot(profileId: String, snapshot: ScheduleSnapshot?) { saved = snapshot }
        override suspend fun replace(profileId: String, expected: ScheduleState, state: ScheduleState) { check(this.state == expected); this.state = state; transactions++ }
        override fun reschedule(before: List<ScheduledTask>, after: List<ScheduledTask>) {}
    }
    companion object { private val DayOfWeekAll = java.time.DayOfWeek.entries.toSet() }
}
