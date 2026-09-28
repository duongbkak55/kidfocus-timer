package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.TaskType
import java.time.DayOfWeek.*
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleDraftTest {
    private fun raw(confidence: Double = 0.8, type: String = "CUSTOM"): Map<String, Any?> = mapOf(
        "anchors" to mapOf("wake" to mapOf("TUE" to "06:15"), "bed" to mapOf("MON" to "21:30"),
            "school" to listOf(mapOf("days" to listOf("MON", "FRI"), "start" to "07:15", "end" to "11:30", "label" to "Ở trường"))),
        "tasks" to listOf(mapOf("name" to "Học Anh", "taskType" to type, "emoji" to "⭐", "days" to listOf("TUE", "THU"),
            "start" to "18:00", "durationMin" to 60, "confidence" to confidence, "source" to "T3 T5 học Anh 6 giờ tối")),
        "questions" to listOf("Thứ 7 dậy lúc nào?"),
    )
    @Test fun `maps callable draft to scoped tasks and preserves anchors commute and school`() {
        val draft = ScheduleDraftMapper.fromMap(raw()) { 100_000_001 }
        val before = ScheduleState(emptyList(), ScheduleAnchors(wake = mapOf(MONDAY to LocalTime.of(6, 0)), commuteMinutes = 20))
        val after = draft.merge("child-2", before)
        val task = after.tasks.single()
        assertEquals(100_000_001L, task.id)
        assertEquals("child-2", task.childProfileId)
        assertEquals(setOf(3, 5), task.daysOfWeek)
        assertEquals(60, task.focusDurationMinutes)
        assertEquals(0, task.breakDurationMinutes)
        assertTrue(task.isCustom)
        assertEquals(20, after.anchors.commuteMinutes)
        assertEquals(LocalTime.of(6, 0), after.anchors.wake[MONDAY])
        assertEquals(LocalTime.of(6, 15), after.anchors.wake[TUESDAY])
        assertEquals(LocalTime.of(21, 30), after.anchors.bed[MONDAY])
        assertEquals(setOf(MONDAY, FRIDAY), after.anchors.school.single().days)
        assertEquals(listOf("Thứ 7 dậy lúc nào?"), draft.questions)
    }
    @Test fun `low confidence is unselected at boundary and type mapping uses enum or CUSTOM`() {
        assertFalse(ScheduleDraftMapper.fromMap(raw(0.599)).items.last().selected)
        assertTrue(ScheduleDraftMapper.fromMap(raw(0.6)).items.last().selected)
        assertEquals(TaskType.CUSTOM, ScheduleDraftMapper.fromMap(raw(type = "UNKNOWN")).items.last().taskType)
        val reading = ScheduleDraftMapper.fromMap(raw(type = "READING")).items.last().toTask("default")
        assertEquals(TaskType.READING, reading.taskType)
        assertFalse(reading.isCustom)
    }
    @Test fun `unchecked entries do not mutate anchors or create tasks`() {
        val before = ScheduleState(emptyList(), ScheduleAnchors())
        val draft = ScheduleDraftMapper.fromMap(raw()).let { it.copy(items = it.items.map { item -> item.copy(selected = false) }) }
        assertEquals(before, draft.merge("default", before))
        assertTrue(draft.changes("default", before).isEmpty())
    }
    @Test fun `invalid duration days and missing server field rejected`() {
        val task = (raw()["tasks"] as List<*>).single() as Map<*, *>
        for (bad in listOf(task + ("durationMin" to 4), task + ("durationMin" to 10.5), task + ("days" to listOf("T2")),
            task + ("start" to "24:00"), task + ("confidence" to 1.1), task.filterKeys { it != "confidence" })) {
            assertTrue(runCatching { ScheduleDraftMapper.fromMap(raw() + ("tasks" to listOf(bad))) }.isFailure)
        }
    }
    @Test fun `preview flags new overlap for both tasks and drops warning when one is unchecked`() {
        val original = ScheduleDraftMapper.fromMap(raw()).items.last().copy(taskId = 100L, days = setOf(TUESDAY))
        val second = original.copy(key = "task:2", taskId = 101)
        val draft = ScheduleDraft(listOf(original, second), emptyList())
        val before = ScheduleState(emptyList(), ScheduleAnchors())
        val warnings = draft.warnings("default", before, "l1")
        assertEquals(setOf(RuleId.OVERLAP), warnings[original.key])
        assertEquals(setOf(RuleId.OVERLAP), warnings[second.key])
        assertTrue(draft.copy(items = listOf(original, second.copy(selected = false))).warnings("default", before, "l1").values.all { it.isEmpty() })
    }
    @Test fun `preview flags school overlap and next day sleep short without blaming unchanged findings`() {
        val task = ScheduleDraftMapper.fromMap(raw()).items.last().copy(taskId = 100L, days = setOf(MONDAY), start = LocalTime.of(8, 0))
        val bed = DraftItem("bed", DraftKind.BED, setOf(SUNDAY), LocalTime.of(22, 30))
        val before = ScheduleState(emptyList(), ScheduleAnchors(wake = mapOf(MONDAY to LocalTime.of(6, 15)),
            school = listOf(Block(setOf(MONDAY), LocalTime.of(7, 0), LocalTime.of(10, 0), "School"))))
        val warnings = ScheduleDraft(listOf(task, bed), emptyList()).warnings("default", before, "l1")
        assertEquals(setOf(RuleId.OVERLAP), warnings[task.key])
        assertEquals(setOf(RuleId.SLEEP_SHORT), warnings[bed.key])
        val withOldShortSleep = before.copy(anchors = before.anchors.copy(bed = mapOf(SUNDAY to bed.start)))
        assertTrue(ScheduleDraft(listOf(bed), emptyList()).warnings("default", withOldShortSleep, "l1").values.all { it.isEmpty() })
    }
    @Test fun `sleep warning attributed to imported wake of following day`() {
        val wake = DraftItem("wake", DraftKind.WAKE, setOf(MONDAY), LocalTime.of(6, 0))
        val before = ScheduleState(emptyList(), ScheduleAnchors(bed = mapOf(SUNDAY to LocalTime.of(22, 0))))
        assertEquals(setOf(RuleId.SLEEP_SHORT), ScheduleDraft(listOf(wake), emptyList()).warnings("default", before, "l1")["wake"])
    }
    @Test fun `edited anchor rows with different times on the same day cannot silently overwrite`() {
        val wake = DraftItem("wake-one", DraftKind.WAKE, setOf(MONDAY), LocalTime.of(6, 0))
        val second = wake.copy(key = "wake-two", start = LocalTime.of(7, 0))
        val draft = ScheduleDraft(listOf(wake, second), emptyList())
        val before = ScheduleState(emptyList(), ScheduleAnchors())
        assertTrue(runCatching { draft.changes("default", before) }.isFailure)
        assertEquals(LocalTime.of(6, 0), draft.copy(items = listOf(wake, second.copy(selected = false))).merge("default", before).anchors.wake[MONDAY])
    }

    @Test fun `new school crossing Sunday midnight flags its Monday task overlap`() {
        val task = ScheduleDraftMapper.fromMap(raw()).items.last().copy(taskId = 100L, days = setOf(MONDAY), start = LocalTime.of(0, 10), durationMin = 15).toTask("default")
        val school = DraftItem("school", DraftKind.SCHOOL, setOf(SUNDAY), LocalTime.of(23, 30), name = "School", end = LocalTime.of(0, 30))
        val before = ScheduleState(listOf(task), ScheduleAnchors())
        assertEquals(setOf(RuleId.OVERLAP), ScheduleDraft(listOf(school), emptyList()).warnings("default", before, "l1")[school.key])
    }
    @Test fun `unchanged wake row does not get blamed for a new late bed`() {
        val wake = DraftItem("wake", DraftKind.WAKE, setOf(MONDAY), LocalTime.of(6, 15))
        val bed = DraftItem("bed", DraftKind.BED, setOf(SUNDAY), LocalTime.of(22, 30))
        val before = ScheduleState(emptyList(), ScheduleAnchors(wake = mapOf(MONDAY to wake.start)))
        val warnings = ScheduleDraft(listOf(wake, bed), emptyList()).warnings("default", before, "l1")
        assertTrue(warnings.getValue(wake.key).isEmpty())
        assertEquals(setOf(RuleId.SLEEP_SHORT), warnings[bed.key])
    }

}
