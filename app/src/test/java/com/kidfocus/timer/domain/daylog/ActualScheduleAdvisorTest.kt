package com.kidfocus.timer.domain.daylog

import com.kidfocus.timer.domain.model.*
import com.kidfocus.timer.domain.schedule.*
import org.junit.Test
import org.junit.Assert.*
import java.time.*

class ActualScheduleAdvisorTest {
    private val today = LocalDate.parse("2026-09-28")
    private val task = ScheduledTask(42,TaskType.HOMEWORK,"Homework","x",19,0,(1..7).toSet(),30,5)
    private val anchors = ScheduleAnchors(bed=DayOfWeek.entries.associateWith { LocalTime.of(22,0) },wake=DayOfWeek.entries.associateWith { LocalTime.of(6,0) })
    private fun log(date:LocalDate,start:Int=1140,end:Int?=1170,category:DayLogCategory=DayLogCategory.STUDY,taskId:Long?=42) =
        DayLogEntry(profileId="child",date=date,taskId=taskId,name="Actual private activity",category=category,startMinute=start,endMinute=end,source=DayLogSource.AI,createdAt=1000)
    @Test fun bedtimeNeedsThreeRecordedDaysAtLeastThirtyMinutesLateAcrossWeekBoundary() {
        val bed=(1L..3L).map { log(today.minusDays(it),1350,null,DayLogCategory.SLEEP,null) }
        assertFalse(ActualScheduleAdvisor.evaluate(listOf(task),anchors,bed.take(2),today).findings.any { it.ruleId==RuleId.BED_DRIFT })
        val result=ActualScheduleAdvisor.evaluate(listOf(task),anchors,bed,today)
        assertEquals(Severity.HIGH,result.findings.single { it.ruleId==RuleId.BED_DRIFT }.severity)
        assertEquals(3,result.stats.bedLateDays);assertEquals(3,result.stats.recordedDays)
        assertFalse(ActualScheduleAdvisor.evaluate(listOf(task),anchors,bed.map { it.copy(startMinute=1349) },today).findings.any { it.ruleId==RuleId.BED_DRIFT })
    }
    @Test fun overnightBedtimeAndDeletedOrOldLogsRespectTheSevenDayWindow() {
        val overnight=(1L..3L).map { log(today.minusDays(it-1),30,null,DayLogCategory.SLEEP,null) }
        val result=ActualScheduleAdvisor.evaluate(emptyList(),anchors,overnight,today)
        assertEquals(3,result.stats.bedLateDays)
        assertTrue(result.findings.any { it.ruleId==RuleId.BED_DRIFT })
        assertTrue(ActualScheduleAdvisor.evaluate(emptyList(),anchors,overnight.map { it.copy(deleted=true) },today).findings.isEmpty())
        assertTrue(ActualScheduleAdvisor.evaluate(emptyList(),anchors,overnight.map { it.copy(date=it.date.minusDays(20)) },today).findings.isEmpty())
    }
    @Test fun overrunsAreStrictlyAbove150PercentAndCountOccurrencesRatherThanDuplicateEntries() {
        val rows=(1L..3L).map { log(today.minusDays(it),end=1186) }
        val result=ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),rows,today)
        assertEquals(Severity.MEDIUM,result.findings.single { it.ruleId==RuleId.TASK_OVERRUN }.severity)
        assertEquals(3,result.stats.tasks.single().overrun)
        assertTrue(ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),rows.map { it.copy(endMinute=1185) },today).findings.isEmpty())
        assertTrue(ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),rows.map { it.copy(endMinute=null) },today).findings.isEmpty())
        val repeated=rows.map { it.copy(date=today.minusDays(1)) }
        assertFalse(ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),repeated,today).findings.any { it.ruleId==RuleId.TASK_OVERRUN })
    }
    @Test fun voluntaryExtraTimeDoesNotCountAsTaskOverrun() {
        val rows=(1L..3L).map { log(today.minusDays(it),end=1186).copy(source=DayLogSource.TIMER) }
        val extensions=rows.associate { it.id to 20 }
        val result=ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),rows,today,
            extendedMinutesByLogId=extensions)
        assertEquals(0,result.stats.tasks.single().overrun)
        assertFalse(result.findings.any { it.ruleId==RuleId.TASK_OVERRUN })
        assertEquals(3,ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),rows,today,
            extendedMinutesByLogId=rows.associate { it.id to 15 }).stats.tasks.single().overrun)
        assertEquals(3,ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),rows.map { it.copy(source=DayLogSource.MANUAL) },today,
            extendedMinutesByLogId=extensions).stats.tasks.single().overrun)
    }
    @Test fun onlyDueTasksOnRecordedDaysCanBeSkippedAndPayloadContainsOnlyAggregateAliasStats() {
        assertTrue(ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),emptyList(),today).findings.isEmpty())
        val incidental=(1L..3L).map { log(today.minusDays(it),600,630,DayLogCategory.OTHER,null) }
        val result=ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),incidental,today)
        assertEquals(3,result.findings.single { it.ruleId==RuleId.OFTEN_SKIPPED }.params["count"])
        val pending=ActualScheduleAdvisor.evaluate(listOf(task),ScheduleAnchors(),listOf(log(today,600,630,DayLogCategory.OTHER,null)),today,600)
        assertEquals(0,pending.stats.tasks.single().missed)
        val payload=result.stats.payload(mapOf("t0" to DayOfWeek.entries.associateWith { 42L }))
        assertEquals(setOf("recordedDays","bedLateDays","tasks"),payload.keys)
        assertFalse(payload.toString().contains("private"));assertFalse(payload.toString().contains("child"));assertFalse(payload.toString().contains("2026"))
        assertEquals("t0",(payload["tasks"] as List<*>).map { it as Map<*,*> }.single()["taskRef"])
    }
}
