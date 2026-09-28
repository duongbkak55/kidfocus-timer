package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskCategory
import com.kidfocus.timer.domain.model.TaskType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

enum class RuleId { SLEEP_SHORT, SOCIAL_JETLAG, SCREEN_BEFORE_BED, OVERLAP, LATE_HOMEWORK, FOCUS_TOO_LONG, NO_FREE_TIME, MORNING_LATE_PATTERN }
enum class Severity { HIGH, MEDIUM, LOW }

sealed interface ScheduleChange {
    data class AddTask(val task: ScheduledTask) : ScheduleChange
    data class SetAnchors(val anchors: ScheduleAnchors) : ScheduleChange
    data class SetBed(val times: Map<DayOfWeek, LocalTime>) : ScheduleChange
    data class SetWake(val times: Map<DayOfWeek, LocalTime>) : ScheduleChange
    sealed interface TaskChange : ScheduleChange {
        val taskId: Long
        val days: Set<DayOfWeek>
    }
    data class RemoveTask(override val taskId: Long, override val days: Set<DayOfWeek>) : TaskChange
    data class MoveTask(override val taskId: Long, override val days: Set<DayOfWeek>, val start: LocalTime) : TaskChange
    data class ResizeTask(override val taskId: Long, override val days: Set<DayOfWeek>, val durationMinutes: Int) : TaskChange
}

data class Finding(
    val ruleId: RuleId,
    val severity: Severity,
    val days: Set<DayOfWeek>,
    val taskIds: Set<Long> = emptySet(),
    val params: Map<String, Int> = emptyMap(),
    val suggestion: ScheduleChange? = null,
)

data class RoutinePattern(val id: Long, val days: Set<DayOfWeek>, val deadlineMinutes: Int, val createdDate: LocalDate)
data class RoutineObservation(val routineId: Long, val date: LocalDate, val status: String)

internal data class ScheduleInterval(val start: Int, val end: Int, val taskId: Long? = null, val school: Block? = null, val occurrenceDay: DayOfWeek? = null)

/** Repeated intervals include yesterday and tomorrow for midnight and Sunday/Monday edges. */
internal fun intervalsForDay(tasks: List<ScheduledTask>, anchors: ScheduleAnchors, day: DayOfWeek): List<ScheduleInterval> = buildList {
    for (offset in -1..1) {
        val occurrenceDay = day.plus(offset.toLong())
        tasks.filter { it.enabled && DayCodec.toCalendar(occurrenceDay) in it.daysOfWeek }.forEach {
            val start = offset * 1440 + it.hour * 60 + it.minute
            add(ScheduleInterval(start, start + it.focusDurationMinutes + it.breakDurationMinutes, it.id, occurrenceDay = occurrenceDay))
        }
        anchors.school.filter { occurrenceDay in it.days }.forEach {
            val start = offset * 1440 + it.start.minutes()
            var end = offset * 1440 + it.end.minutes()
            if (end <= start) end += 1440
            add(ScheduleInterval(start - anchors.commuteMinutes, end + anchors.commuteMinutes, school = it, occurrenceDay = occurrenceDay))
        }
    }
}

class ScheduleAdvisor {
    fun advise(
        tasks: List<ScheduledTask>,
        anchors: ScheduleAnchors,
        ageBand: String,
        routines: List<RoutinePattern> = emptyList(),
        completions: List<RoutineObservation> = emptyList(),
        today: LocalDate = LocalDate.now(),
        nowMinute: Int = LocalTime.now().minutes(),
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        val minSleep = ScheduleThresholds.sleepMinutes.getOrElse(ageBand) { ScheduleThresholds.sleepMinutes.getValue("4-5") }
        val maxFocus = ScheduleThresholds.focusMinutes.getOrElse(ageBand) { ScheduleThresholds.focusMinutes.getValue("4-5") }
        val enabled = tasks.filter { it.enabled }
        DayOfWeek.entries.forEach { day ->
            val bed = anchors.bedMinute(day)
            val nextWake = anchors.wake[day.plus(1)]?.minutes()?.plus(1440)
            if (bed != null && nextWake != null && nextWake - bed < minSleep) {
                findings += Finding(RuleId.SLEEP_SHORT, Severity.HIGH, setOf(day),
                    params = mapOf("actual" to (nextWake - bed).coerceAtLeast(0), "minimum" to minSleep),
                    suggestion = if (nextWake - minSleep >= (anchors.wake[day]?.minutes() ?: 0))
                        ScheduleChange.SetBed(mapOf(day to timeAtMinute(nextWake - minSleep))) else null)
            }
            val intervals = intervalsForDay(enabled, anchors, day)
            val dayTasks = enabled.filter { DayCodec.toCalendar(day) in it.daysOfWeek }
            dayTasks.forEach { task ->
                if (task.focusDurationMinutes > maxFocus) {
                    findings += Finding(RuleId.FOCUS_TOO_LONG, Severity.LOW, setOf(day), setOf(task.id), mapOf("maximum" to maxFocus),
                        ScheduleChange.ResizeTask(task.id, setOf(day), maxFocus))
                }
            }
            if (bed != null) {
                val wake = anchors.wake[day]?.minutes() ?: 0
                intervals.filter { it.taskId != null && it.start >= wake && it.start < (nextWake ?: 1440) }.forEach { interval ->
                    val task = enabled.first { it.id == interval.taskId }
                    val focusEnd = interval.start + task.focusDurationMinutes
                    if (task.taskType.category == TaskCategory.STUDY && focusEnd > bed - ScheduleThresholds.BEFORE_BED_MINUTES) {
                        val target = minOf(bed - ScheduleThresholds.HOMEWORK_START_LEAD_MINUTES, bed - ScheduleThresholds.BEFORE_BED_MINUTES - task.focusDurationMinutes)
                        findings += Finding(RuleId.LATE_HOMEWORK, Severity.MEDIUM, setOf(day), setOf(task.id),
                            suggestion = if (interval.occurrenceDay == day && target in wake..1439)
                                ScheduleChange.MoveTask(task.id, setOf(day), timeAtMinute(target)) else null)
                    }
                }
            }
            if (bed != null) {
                intervals.filter { it.taskId != null && it.start < bed && it.end > bed - ScheduleThresholds.BEFORE_BED_MINUTES }.forEach { interval ->
                    val task = enabled.first { it.id == interval.taskId }
                    if (task.taskType in SCREEN_TASK_TYPES) {
                        val target = bed - ScheduleThresholds.BEFORE_BED_MINUTES - task.focusDurationMinutes - task.breakDurationMinutes
                        val wake = anchors.wake[day]?.minutes() ?: 0
                        findings += Finding(RuleId.SCREEN_BEFORE_BED, Severity.MEDIUM, setOf(day), setOf(task.id),
                            suggestion = if (interval.occurrenceDay == day && target in wake..1439)
                                ScheduleChange.MoveTask(task.id, setOf(day), timeAtMinute(target)) else null)
                    }
                }
            }
            val visible = intervals.filter { it.start < 1440 && it.end > 0 }
            visible.forEachIndexed { index, a ->
                visible.drop(index + 1).filter { b ->
                    (a.taskId != null || b.taskId != null) && a.start < b.end && b.start < a.end &&
                        maxOf(a.start, b.start) < 1440 && minOf(a.end, b.end) > 0
                }.forEach { b ->
                    findings += Finding(RuleId.OVERLAP, Severity.HIGH, setOf(day), setOfNotNull(a.taskId, b.taskId))
                }
            }
            val wake = anchors.wake[day]?.minutes()
            if (wake != null && bed != null) {
                var cursor: Int = wake
                var largestGap = 0
                intervals.filter { it.start < bed && it.end > wake }.sortedBy { it.start }.forEach {
                    largestGap = maxOf(largestGap, it.start - cursor)
                    cursor = maxOf(cursor, it.end)
                }
                largestGap = maxOf(largestGap, bed - cursor)
                if (largestGap < ScheduleThresholds.FREE_TIME_MINUTES) {
                    findings += Finding(RuleId.NO_FREE_TIME, Severity.LOW, setOf(day))
                }
            }
        }
        val weekdayBeds = DayOfWeek.entries.take(5).mapNotNull(anchors::bedMinute)
        val weekendBeds = DayOfWeek.entries.drop(5).mapNotNull(anchors::bedMinute)
        if (weekdayBeds.isNotEmpty() && weekendBeds.isNotEmpty()) {
            val difference = abs(weekdayBeds.average() - weekendBeds.average())
            if (difference > ScheduleThresholds.SOCIAL_JETLAG_MINUTES) {
                findings += Finding(RuleId.SOCIAL_JETLAG, Severity.MEDIUM, DayOfWeek.entries.toSet(), params = mapOf("difference" to kotlin.math.ceil(difference).toInt()))
            }
        }
        val recent = completions.filter { it.date in today.minusDays(ScheduleThresholds.HISTORY_DAYS - 1)..today }.associateBy { it.routineId to it.date }
        val trackedRoutineIds = recent.values.mapTo(mutableSetOf()) { it.routineId }
        routines.filter { it.id in trackedRoutineIds && it.deadlineMinutes < ScheduleThresholds.MORNING_END_MINUTES }.forEach { routine ->
            val badDates = (0 until ScheduleThresholds.MORNING_PATTERN_DAYS).map { today.minusDays(it) }.filter { date ->
                if (date < routine.createdDate || date.dayOfWeek !in routine.days) return@filter false
                val status = recent[routine.id to date]?.status
                status == "LATE" || status == "MISSED" ||
                    (status == null && (date < today || nowMinute > routine.deadlineMinutes))
            }
            if (badDates.size >= ScheduleThresholds.MORNING_BAD_DAYS) {
                findings += Finding(RuleId.MORNING_LATE_PATTERN, Severity.HIGH, badDates.map { it.dayOfWeek }.toSet(),
                    params = mapOf("count" to badDates.size))
            }
        }
        return findings.distinct().sortedBy { it.severity.ordinal }
    }

    companion object {
        val SCREEN_TASK_TYPES = setOf(TaskType.LEARNING_GAMES, TaskType.GAME_TIME, TaskType.TV_TIME)
    }
}
