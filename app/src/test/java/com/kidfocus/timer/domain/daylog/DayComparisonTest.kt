package com.kidfocus.timer.domain.daylog

import com.kidfocus.timer.domain.model.*
import com.kidfocus.timer.domain.schedule.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.Calendar

class DayComparisonTest {
    private val monday = LocalDate.parse("2026-09-28")
    private val task = ScheduledTask(id = 42, taskType = TaskType.HOMEWORK, name = "Homework", emoji = "x", hour = 18, minute = 0,
        daysOfWeek = setOf(Calendar.MONDAY), focusDurationMinutes = 30, breakDurationMinutes = 5)
    private fun entry(start: Int, end: Int?, date: LocalDate = monday, taskId: Long? = 42, category: DayLogCategory = DayLogCategory.STUDY) =
        DayLogEntry(profileId = "default", date = date, taskId = taskId, name = "Homework", category = category,
            startMinute = start, endMinute = end, source = DayLogSource.MANUAL, createdAt = 1000)
    private fun week(rows: List<DayLogEntry>, tasks: List<ScheduledTask> = listOf(task), anchors: ScheduleAnchors = ScheduleAnchors()) =
        compareWeek(monday, tasks, anchors, rows, today = monday.plusWeeks(1))

    @Test fun equalVersionEditsConvergeAndDeletesAlwaysWin() {
        val a = entry(600,630)
        val b = a.copy(name = "Other")
        assertEquals(newerDayLog(a,b),newerDayLog(b,a))
        assertTrue(newerDayLog(a,b.copy(deleted=true)).deleted)
        assertTrue(newerDayLog(b.copy(deleted=true),a).deleted)
    }
    @Test fun unrecordedDaysAreNeverMissedOrInTheDenominator() {
        val result = week(emptyList())
        assertEquals(0, result.summary.recordedDays)
        assertNull(result.summary.completedPercent)
        assertEquals(DayComparisonStatus.NO_DATA, result.days[0].rows[0].status)
    }
    @Test fun toleranceIncludesBothTenMinuteEdges() {
        for (delta in listOf(-10, 0, 10)) assertEquals(DayComparisonStatus.ON_TIME, week(listOf(entry(1080 + delta, 1110 + delta))).days[0].rows[0].status)
        assertEquals(DayComparisonStatus.LATE, week(listOf(entry(1091, 1121))).days[0].rows[0].status)
        assertEquals(DayComparisonStatus.EARLY, week(listOf(entry(1069, 1099))).days[0].rows[0].status)
    }
    @Test fun durationIsComparedToFocusAndMissingOnlyOnRecordedDays() {
        assertEquals(15, week(listOf(entry(1080, 1125))).days[0].rows[0].durationDelta)
        assertEquals(-15, week(listOf(entry(1080, 1095))).days[0].rows[0].durationDelta)
        val result = week(listOf(entry(600, 610, taskId = null)))
        assertEquals(DayComparisonStatus.MISSED, result.days[0].rows[0].status)
        assertEquals(1, result.days[0].incidental.size)
        assertEquals(0, result.summary.completedPercent)
    }
    @Test fun openTimerIsNotCompletedAndTombstonesDoNotCountAsData() {
        assertEquals(0, week(listOf(entry(1080, null))).summary.completedPercent)
        assertEquals(0, week(listOf(entry(1080, 1110).copy(deleted = true))).summary.recordedDays)
    }
    @Test fun futureTasksOnRecordedTodayAreNotMissed() {
        val result = compareWeek(monday, listOf(task), ScheduleAnchors(), listOf(entry(600, 610, taskId = null)), monday, 700)
        assertEquals(DayComparisonStatus.NO_DATA, result.days[0].rows[0].status)
    }
    @Test fun recurringPlanDoesNotChangeWhenViewingAnotherWeek() {
        val t = task.copy(daysOfWeek = setOf(Calendar.TUESDAY, Calendar.THURSDAY), hour = 23, minute = 50, focusDurationMinutes = 30)
        val first = planForDate(monday.plusDays(1), listOf(t), ScheduleAnchors())
        val next = planForDate(monday.plusDays(8), listOf(t), ScheduleAnchors())
        assertEquals(first.map { it.copy(date = next[0].date) }, next)
        assertEquals(1430, first.single().startMinute); assertEquals(30, first.single().durationMinutes)
        assertTrue(planForDate(monday, listOf(t), ScheduleAnchors()).isEmpty())
    }
    @Test fun sundayLateBedBelongsToMondayMorningAcrossWeekBoundary() {
        val a = ScheduleAnchors(bed = mapOf(DayOfWeek.SUNDAY to LocalTime.of(0,30)), wake = mapOf(DayOfWeek.MONDAY to LocalTime.of(6,15)))
        val row = planForDate(monday, emptyList(), a).first { it.category == DayLogCategory.SLEEP }
        assertEquals(30, row.startMinute); assertEquals(345, row.durationMinutes)
        assertEquals(monday, row.date)
    }
    @Test fun sleepCanPairWithNextDaysActualWakeAndSummaryExcludesUnrecordedDays() {
        val a = ScheduleAnchors(bed = mapOf(DayOfWeek.MONDAY to LocalTime.of(23,30)), wake = mapOf(DayOfWeek.TUESDAY to LocalTime.of(6,15)))
        val sleep = entry(1410, null, taskId = null, category = DayLogCategory.SLEEP)
        val wake = entry(360,360,monday.plusDays(1),null,DayLogCategory.WAKE)
        val result = week(listOf(sleep,wake), emptyList(), a)
        assertEquals(15, result.summary.sleepDeficit)
        assertEquals(2, result.summary.recordedDays)
        assertEquals(1410.0, result.summary.averageActualBed!!, 0.01)
    }
    @Test fun midnightLateStartMatchesThePreviousDaysTask() {
        val t = task.copy(hour = 23, minute = 55)
        val result = week(listOf(entry(5,35,monday.plusDays(1))), listOf(t))
        assertEquals(10, result.days[0].rows[0].startDelta)
        assertEquals(DayComparisonStatus.ON_TIME, result.days[0].rows[0].status)
    }
    @Test fun incidentalEntriesAndLargestDifferencesAreStable() {
        val second = task.copy(id = 43, hour = 20)
        val rows = listOf(entry(1095,1150),entry(1180,1210).copy(taskId = 43),entry(600,610,taskId = null))
        val result = week(rows,listOf(task,second))
        assertEquals(1,result.days[0].incidental.size)
        assertEquals(42L,result.summary.biggestDrifts.first().plan.taskId)
        assertEquals(7.5,result.summary.averageStartDelay!!,0.01)
    }
}
