package com.kidfocus.timer.domain.daylog

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.schedule.*
import java.time.LocalDate
import kotlin.math.roundToInt

data class ActualTaskStat(val taskId: Long, val completed: Int, val overrun: Int, val missed: Int, val averageDelayMin: Int?)
data class ActualScheduleStats(val recordedDays: Int = 0, val bedLateDays: Int = 0, val tasks: List<ActualTaskStat> = emptyList()) {
    fun payload(refs: AdviceRefs): Map<String, Any> {
        val byId = refs.flatMap { (ref, days) -> days.values.map { it to ref } }.toMap()
        return mapOf("recordedDays" to recordedDays, "bedLateDays" to bedLateDays, "tasks" to tasks.mapNotNull { t ->
            byId[t.taskId]?.let { ref -> mapOf("taskRef" to ref, "completed" to t.completed, "overrun" to t.overrun,
                "missed" to t.missed, "averageDelayMin" to t.averageDelayMin) }
        })
    }
}
data class ActualScheduleResult(val findings: List<Finding>, val stats: ActualScheduleStats)

/** Seven calendar days; only recorded days and due occurrences can count as missed. */
object ActualScheduleAdvisor {
    fun evaluate(tasks: List<ScheduledTask>, anchors: ScheduleAnchors, entries: List<DayLogEntry>,
        today: LocalDate = LocalDate.now(), nowMinute: Int = 1439): ActualScheduleResult {
        val days = compareWeek(today.minusDays(6), tasks, anchors, entries, today, nowMinute).days.filter { it.hasData }
        val rows = days.flatMap { it.rows }
        val lateBeds = rows.filter { it.plan.category == DayLogCategory.SLEEP && (it.startDelta ?: 0) >= 30 }
        val badBedDates = lateBeds.map { it.plan.date }.distinct()
        val findings = mutableListOf<Finding>()
        if (badBedDates.size >= 3) findings += Finding(RuleId.BED_DRIFT, Severity.HIGH,
            badBedDates.map { it.dayOfWeek }.toSet(), params = mapOf("count" to badBedDates.size))
        val stats = rows.filter { it.plan.taskId != null }.groupBy { it.plan.taskId!! }.map { (id, occurrences) ->
            val completed = occurrences.filter { it.actualDuration != null }
            val overrun = completed.filter { it.plan.durationMinutes != null && it.plan.durationMinutes > 0 &&
                it.actualDuration!! * 2 > it.plan.durationMinutes * 3 }
            val missed = occurrences.filter { it.status == DayComparisonStatus.MISSED }
            if (overrun.size >= 3) findings += Finding(RuleId.TASK_OVERRUN, Severity.MEDIUM,
                overrun.map { it.plan.date.dayOfWeek }.toSet(), setOf(id), mapOf("count" to overrun.size))
            if (missed.size >= 3) findings += Finding(RuleId.OFTEN_SKIPPED, Severity.MEDIUM,
                missed.map { it.plan.date.dayOfWeek }.toSet(), setOf(id), mapOf("count" to missed.size))
            ActualTaskStat(id, completed.size, overrun.size, missed.size,
                occurrences.mapNotNull { it.startDelta?.coerceAtLeast(0) }.takeIf { it.isNotEmpty() }?.average()?.roundToInt())
        }
        return ActualScheduleResult(findings, ActualScheduleStats(days.size, badBedDates.size, stats))
    }
}
