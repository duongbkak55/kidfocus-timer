package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleAdvisorTest {
    private val monday = DayOfWeek.MONDAY
    private val sunday = DayOfWeek.SUNDAY
    private val today = LocalDate.of(2026, 9, 28)
    private val advisor = ScheduleAdvisor()
    private fun hours(wake: String = "06:15", bed: String = "21:15") = ScheduleAnchors(
        DayOfWeek.entries.associateWith { LocalTime.parse(wake) }, DayOfWeek.entries.associateWith { LocalTime.parse(bed) })
    private fun task(id: Long = 1, type: TaskType = TaskType.CUSTOM, start: String = "18:00", duration: Int = 30,
        days: Set<DayOfWeek> = setOf(monday), enabled: Boolean = true, breakMinutes: Int = 0): ScheduledTask {
        val time = LocalTime.parse(start)
        return ScheduledTask(id, type, "Task", "", time.hour, time.minute, DayCodec.toCalendar(days), duration, breakMinutes, enabled)
    }
    private fun findings(rule: RuleId, anchors: ScheduleAnchors = hours(), tasks: List<ScheduledTask> = emptyList()) =
        advisor.advise(tasks, anchors, "l1", today = today).filter { it.ruleId == rule }

    @Test fun `canonical weekday 22 30 bedtime yields high sleep shortage`() {
        val anchors = hours().copy(bed = DayOfWeek.entries.take(5).associateWith { LocalTime.of(22, 30) })
        val found = findings(RuleId.SLEEP_SHORT, anchors)
        assertEquals(5, found.size)
        assertTrue(found.all { it.severity == Severity.HIGH && it.params["actual"] == 465 })
        assertEquals(LocalTime.of(21, 15), (found.first().suggestion as ScheduleChange.SetBed).times[monday])
    }
    @Test fun `sleep at exact threshold passes and Sunday uses Monday wake`() {
        assertTrue(findings(RuleId.SLEEP_SHORT).isEmpty())
        val anchors = hours().copy(wake = mapOf(monday to LocalTime.of(6, 15)), bed = mapOf(sunday to LocalTime.of(22, 30)))
        assertEquals(setOf(sunday), findings(RuleId.SLEEP_SHORT, anchors).single().days)
    }
    @Test fun `after midnight bedtime and age thresholds`() {
        val anchors = hours(bed = "00:30")
        assertEquals(345, findings(RuleId.SLEEP_SHORT, anchors).first().params["actual"])
        listOf("2-3" to 660, "4-5" to 600, "l1" to 540, "l2" to 540, "l3" to 540).forEach { (age, min) ->
            assertEquals(min, advisor.advise(emptyList(), anchors, age).first { it.ruleId == RuleId.SLEEP_SHORT }.params["minimum"])
        }
    }
    @Test fun `jetlag over 60 minutes produces finding`() {
        val anchors = hours().copy(bed = hours().bed + mapOf(DayOfWeek.SATURDAY to LocalTime.of(22, 16), sunday to LocalTime.of(22, 16)))
        assertEquals(61, findings(RuleId.SOCIAL_JETLAG, anchors).single().params["difference"])
    }
    @Test fun `jetlag boundary and circular midnight are handled`() {
        val anchors = hours(bed = "23:30").copy(bed = hours(bed = "23:30").bed + mapOf(DayOfWeek.SATURDAY to LocalTime.of(0, 30), sunday to LocalTime.of(0, 30)))
        assertTrue(findings(RuleId.SOCIAL_JETLAG, anchors).isEmpty())
    }
    @Test fun `learning screen crossing midnight before bed is found`() {
        val screen = task(type = TaskType.LEARNING_GAMES, start = "23:50", duration = 30, days = setOf(sunday))
        assertTrue(findings(RuleId.SCREEN_BEFORE_BED, hours(bed = "00:30"), listOf(screen)).any { sunday in it.days })
    }
    @Test fun `screen ending exactly an hour before bed and disabled screen pass`() {
        val screen = task(type = TaskType.TV_TIME, start = "19:45", duration = 30)
        assertTrue(findings(RuleId.SCREEN_BEFORE_BED, tasks = listOf(screen, screen.copy(id = 2, hour = 21, enabled = false))).isEmpty())
    }
    @Test fun `overlap across Sunday Monday and school is found`() {
        val late = task(id = 1, start = "23:50", duration = 30, days = setOf(sunday))
        val early = task(id = 2, start = "00:05", days = setOf(monday))
        assertTrue(findings(RuleId.OVERLAP, tasks = listOf(late, early)).any { monday in it.days && it.taskIds == setOf(1L, 2L) })
        val school = hours().copy(school = listOf(Block(setOf(monday), LocalTime.of(7, 0), LocalTime.of(16, 0), "School")))
        assertEquals(setOf(1L), findings(RuleId.OVERLAP, school, listOf(task(start = "15:55"))).single().taskIds)
    }
    @Test fun `touching tasks do not overlap including break boundary`() {
        val a = task(start = "18:00", duration = 30, breakMinutes = 5)
        val b = task(id = 2, start = "18:35")
        assertTrue(findings(RuleId.OVERLAP, tasks = listOf(a, b)).isEmpty())
    }
    @Test fun `homework crossing midnight is late and suggested start covers long duration`() {
        val homework = task(type = TaskType.HOMEWORK, start = "23:40", duration = 60)
        val found = findings(RuleId.LATE_HOMEWORK, hours(bed = "00:30"), listOf(homework)).single()
        assertEquals(LocalTime.of(22, 30), (found.suggestion as ScheduleChange.MoveTask).start)
    }
    @Test fun `early Monday activity is checked against Sunday night without moving the wrong occurrence`() {
        val homework = task(start = "00:15", duration = 30, days = setOf(monday))
        val late = findings(RuleId.LATE_HOMEWORK, hours(bed = "00:30"), listOf(homework))
        assertTrue(late.any { sunday in it.days })
        assertNull(late.first { sunday in it.days }.suggestion)
    }
    @Test fun `homework ends at cutoff passes and suggestion removes late finding`() {
        assertTrue(findings(RuleId.LATE_HOMEWORK, tasks = listOf(task(start = "19:45"))).isEmpty())
        val homework = task(start = "20:30", duration = 60)
        val move = findings(RuleId.LATE_HOMEWORK, tasks = listOf(homework)).single().suggestion as ScheduleChange.MoveTask
        assertTrue(findings(RuleId.LATE_HOMEWORK, tasks = listOf(homework.copy(hour = move.start.hour, minute = move.start.minute))).isEmpty())
    }
    @Test fun `focus exceeds threshold by one minute fails for every age band`() {
        ScheduleThresholds.focusMinutes.forEach { (age, limit) ->
            assertTrue(advisor.advise(listOf(task(duration = limit + 1)), hours(), age).any { it.ruleId == RuleId.FOCUS_TOO_LONG })
        }
    }
    @Test fun `focus exact threshold and disabled long session pass`() {
        assertTrue(findings(RuleId.FOCUS_TOO_LONG, tasks = listOf(task(duration = 30), task(id = 2, duration = 90, enabled = false))).isEmpty())
    }
    @Test fun `test practice may run to 120 minutes without focus length finding`() {
        assertTrue(findings(RuleId.FOCUS_TOO_LONG, tasks = listOf(task(type = TaskType.TEST_PRACTICE, duration = 120))).isEmpty())
    }
    @Test fun `no free time outside school fails`() {
        val anchors = hours(wake = "07:00", bed = "20:00").copy(school = listOf(Block(setOf(monday), LocalTime.of(7, 30), LocalTime.of(19, 30), "School")))
        assertEquals(setOf(monday), findings(RuleId.NO_FREE_TIME, anchors).single().days)
    }
    @Test fun `60 minute gap passes and missing anchors do not invent free time findings`() {
        val anchors = hours(wake = "07:00", bed = "20:00").copy(school = listOf(Block(setOf(monday), LocalTime.of(8, 0), LocalTime.of(19, 30), "School")))
        assertTrue(findings(RuleId.NO_FREE_TIME, anchors).isEmpty())
        assertTrue(findings(RuleId.NO_FREE_TIME, ScheduleAnchors()).isEmpty())
    }
    private fun routine() = RoutinePattern(42, DayOfWeek.entries.toSet(), 7 * 60, today.minusDays(30))
    @Test fun `three late morning dates count but duplicate observations count once`() {
        val completions = (0..6L).map { RoutineObservation(42, today.minusDays(it), if (it < 3) "LATE" else "ON_TIME") }
        val found = advisor.advise(emptyList(), hours(), "l1", listOf(routine()), completions + completions.first(), today, 8 * 60)
        assertEquals(3, found.single { it.ruleId == RuleId.MORNING_LATE_PATTERN }.params["count"])
    }
    @Test fun `two late dates pass and older than seven days excluded`() {
        val completions = (0..13L).map { RoutineObservation(42, today.minusDays(it), if (it < 2 || it > 6) "LATE" else "ON_TIME") }
        assertTrue(advisor.advise(emptyList(), hours(), "l1", listOf(routine()), completions, today, 8 * 60).none { it.ruleId == RuleId.MORNING_LATE_PATTERN })
    }
    @Test fun `missing occurrences inferred only since creation and after deadline`() {
        val fresh = routine().copy(createdDate = today.minusDays(3))
        val completions = listOf(RoutineObservation(fresh.id, fresh.createdDate, "ON_TIME"))
        assertTrue(advisor.advise(emptyList(), hours(), "l1", listOf(fresh), completions, today, 6 * 60).none { it.ruleId == RuleId.MORNING_LATE_PATTERN })
        assertEquals(3, advisor.advise(emptyList(), hours(), "l1", listOf(fresh), completions, today, 8 * 60).single { it.ruleId == RuleId.MORNING_LATE_PATTERN }.params["count"])
    }

    @Test fun `morning routine with no completion does not infer seven missed days`() {
        val found = advisor.advise(emptyList(), hours(), "l1", listOf(routine()), today = today, nowMinute = 8 * 60)
        assertTrue(found.none { it.ruleId == RuleId.MORNING_LATE_PATTERN })
    }
    @Test fun `only this routine's completion within fourteen day window enables morning evaluation`() {
        val firstDay = RoutineObservation(42, today.minusDays(13), "ON_TIME")
        assertEquals(7, advisor.advise(emptyList(), hours(), "l1", listOf(routine()), listOf(firstDay), today, 8 * 60)
            .single { it.ruleId == RuleId.MORNING_LATE_PATTERN }.params["count"])
        listOf(firstDay.copy(date = today.minusDays(14)), firstDay.copy(date = today.plusDays(1)), firstDay.copy(routineId = 43)).forEach {
            assertTrue(advisor.advise(emptyList(), hours(), "l1", listOf(routine()), listOf(it), today, 8 * 60)
                .none { finding -> finding.ruleId == RuleId.MORNING_LATE_PATTERN })
        }
    }
    @Test fun `unknown age band falls back to 4-5 sleep and focus thresholds`() {
        val tasks = listOf(task(duration = 21))
        val anchors = hours(bed = "22:30")
        val expected = advisor.advise(tasks, anchors, "4-5", today = today)
        listOf("legacy", "", "unknown").forEach { ageBand ->
            val found = advisor.advise(tasks, anchors, ageBand, today = today)
            assertEquals(expected, found)
            assertEquals(600, found.first { it.ruleId == RuleId.SLEEP_SHORT }.params["minimum"])
            assertEquals(20, found.single { it.ruleId == RuleId.FOCUS_TOO_LONG }.params["maximum"])
        }
    }
}
