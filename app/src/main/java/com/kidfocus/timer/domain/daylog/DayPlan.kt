package com.kidfocus.timer.domain.daylog

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.schedule.*
import java.time.LocalDate
import kotlin.math.abs

/** Derived from the same recurring schedule for every date, never stored per week. */
data class DayPlanItem(val key: String, val date: LocalDate, val taskId: Long?, val name: String,
    val category: DayLogCategory, val startMinute: Int, val durationMinutes: Int?)

fun planForDate(date: LocalDate, tasks: List<ScheduledTask>, anchors: ScheduleAnchors): List<DayPlanItem> {
    val day = date.dayOfWeek
    val result = tasks.filter { it.enabled && DayCodec.toCalendar(day) in it.daysOfWeek }.map { task ->
        DayPlanItem("task:${task.id}", date, task.id, task.name,
            DayLogCategory.valueOf(task.taskType.category.name), task.hour * 60 + task.minute, task.focusDurationMinutes)
    }.toMutableList()
    anchors.wake[day]?.let { result += DayPlanItem("wake", date, null, "WAKE", DayLogCategory.WAKE, it.minutes(), 0) }
    for (base in listOf(date.minusDays(1), date)) {
        val bed = anchors.bedMinute(base.dayOfWeek) ?: continue
        val actualDate = base.plusDays((bed / 1440).toLong())
        if (actualDate == date) {
            val wake = anchors.wake[base.dayOfWeek.plus(1)]?.minutes()?.plus(1440)
            result += DayPlanItem("sleep:$base", date, null, "SLEEP", DayLogCategory.SLEEP,
                bed % 1440, wake?.minus(bed)?.takeIf { it >= 0 })
        }
    }
    anchors.school.filter { day in it.days }.forEachIndexed { i, b ->
        result += DayPlanItem("school:$i", date, null, b.label, DayLogCategory.SCHOOL,
            b.start.minutes(), Math.floorMod(b.end.minutes() - b.start.minutes(), 1440))
    }
    return result.sortedBy { it.startMinute }
}

enum class DayComparisonStatus { ON_TIME, LATE, EARLY, MISSED, NO_DATA }
data class PlanComparison(val plan: DayPlanItem, val actual: DayLogEntry?, val status: DayComparisonStatus,
    val startDelta: Int? = null, val durationDelta: Int? = null, val actualDuration: Int? = null)
data class DayComparison(val date: LocalDate, val hasData: Boolean, val rows: List<PlanComparison>, val incidental: List<DayLogEntry>)
data class WeekComparison(val weekStart: LocalDate, val days: List<DayComparison>, val summary: WeekSummary)
data class WeekSummary(val recordedDays: Int, val completedPercent: Int?, val averageStartDelay: Double?,
    val averageActualBed: Double?, val averagePlannedBed: Double?, val sleepDeficit: Int?, val biggestDrifts: List<PlanComparison>)

fun compareWeek(weekStart: LocalDate, tasks: List<ScheduledTask>, anchors: ScheduleAnchors,
    entries: List<DayLogEntry>, today: LocalDate = LocalDate.now(), nowMinute: Int = 1439): WeekComparison {
    val alive = entries.filterNot { it.deleted }
    fun stamp(date: LocalDate, minute: Int) = date.toEpochDay() * 1440 + minute
    fun key(plan: DayPlanItem) = plan.date.toString() + ":" + plan.key
    val candidates = (-1L..7L).flatMap { planForDate(weekStart.plusDays(it), tasks, anchors) }
    fun compatible(entry: DayLogEntry, plan: DayPlanItem) = if (plan.taskId != null) entry.taskId == plan.taskId
        else entry.taskId == null && entry.category == plan.category &&
            (plan.category != DayLogCategory.SCHOOL || entry.name == plan.name)
    // Nearest dated occurrence handles a 23:55 plan started at 00:05 the next calendar day.
    val assignments = alive.mapNotNull { entry ->
        val plan = candidates.filter { compatible(entry, it) &&
            (entry.date == it.date ||
                (abs(stamp(entry.date, entry.startMinute) - stamp(it.date, it.startMinute)) <= 720 &&
                    ((entry.startMinute < 360 && it.startMinute >= 1080 && it.date == entry.date.minusDays(1)) ||
                     (entry.startMinute >= 1080 && it.startMinute < 360 && it.date == entry.date.plusDays(1))))) }
            .minByOrNull { abs(stamp(entry.date, entry.startMinute) - stamp(it.date, it.startMinute)) }
        plan?.let { entry.id to it }
    }.toMap()
    val matched = assignments.entries.groupBy { key(it.value) }.mapValues { (_, group) ->
        group.map { record -> alive.first { it.id == record.key } }.minWith(
            compareBy<DayLogEntry> { entry ->
                val plan = assignments.getValue(entry.id)
                abs(stamp(entry.date, entry.startMinute) - stamp(plan.date, plan.startMinute))
            }.thenBy { it.id })
    }
    val used = matched.values.mapTo(hashSetOf()) { it.id }
    val days = (0L..6).map { offset ->
        val date = weekStart.plusDays(offset)
        val actual = alive.filter { it.date == date }.sortedWith(compareBy({ it.startMinute }, { it.id }))
        val plans = planForDate(date, tasks, anchors)
        val hasData = actual.isNotEmpty() || plans.any { key(it) in matched }
        val rows = plans.map { plan ->
            val match = matched[key(plan)]
            val delta = match?.let { (stamp(it.date, it.startMinute) - stamp(plan.date, plan.startMinute)).toInt() }
            val duration = match?.let { e ->
                e.durationMinutes ?: if (e.category == DayLogCategory.SLEEP) {
                    val start = e.date.atStartOfDay().plusMinutes(e.startMinute.toLong())
                    alive.filter { it.category == DayLogCategory.WAKE }.map {
                        it.date.atStartOfDay().plusMinutes(it.startMinute.toLong())
                    }.filter { it > start && it <= start.plusDays(1) }.minOrNull()?.let {
                        java.time.Duration.between(start, it).toMinutes().toInt()
                    }
                } else null
            }
            val status = when {
                match == null && (!hasData || date > today || (date == today && plan.startMinute > nowMinute)) -> DayComparisonStatus.NO_DATA
                match == null -> DayComparisonStatus.MISSED
                abs(delta!!) <= 10 -> DayComparisonStatus.ON_TIME
                delta > 10 -> DayComparisonStatus.LATE
                else -> DayComparisonStatus.EARLY
            }
            PlanComparison(plan, match, status, delta,
                if (duration != null && plan.durationMinutes != null) duration - plan.durationMinutes else null, duration)
        }
        DayComparison(date, hasData, rows, actual.filter { it.id !in used })
    }
    val counted = days.filter { it.hasData }
    val rows = counted.flatMap { it.rows }.filter { it.status != DayComparisonStatus.NO_DATA }
    val completedRows = rows.filter { it.actual != null }
    val sleep = completedRows.filter { it.plan.category == DayLogCategory.SLEEP }
    val pairedSleep = sleep.filter { it.durationDelta != null }
    fun List<Int>.avg(): Double? = takeIf { it.isNotEmpty() }?.average()
    fun bed(minute: Int) = if (minute < 720) minute + 1440 else minute
    val summary = WeekSummary(counted.size,
        rows.takeIf { it.isNotEmpty() }?.let { r -> 100 * r.count { it.actual != null && it.actualDuration != null } / r.size },
        completedRows.map { maxOf(0, it.startDelta ?: 0) }.avg(),
        sleep.map { bed(it.actual!!.startMinute) }.avg(), sleep.map { bed(it.plan.startMinute) }.avg(),
        pairedSleep.takeIf { it.isNotEmpty() }?.sumOf { maxOf(0, -(it.durationDelta ?: 0)) },
        completedRows.filter { it.startDelta != 0 || (it.durationDelta ?: 0) != 0 }.sortedByDescending { abs(it.startDelta ?: 0) + abs(it.durationDelta ?: 0) }.take(3))
    return WeekComparison(weekStart, days, summary)
}
