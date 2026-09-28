package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import java.time.DayOfWeek

sealed interface ScheduleTimelineItem {
    val start: Int
    val end: Int
    data class Task(val task: ScheduledTask, override val start: Int, override val end: Int) : ScheduleTimelineItem
    data class Anchor(val kind: Kind, override val start: Int, override val end: Int, val label: String? = null) : ScheduleTimelineItem
    data class Gap(override val start: Int, override val end: Int) : ScheduleTimelineItem
    enum class Kind { SLEEP, SCHOOL }
}

data class ScheduleTimeline(val start: Int, val end: Int, val items: List<ScheduleTimelineItem>)

fun buildScheduleTimeline(tasks: List<ScheduledTask>, anchors: ScheduleAnchors, day: DayOfWeek): ScheduleTimeline {
    val wake = anchors.wake[day]?.minutes()
    val bed = anchors.bedMinute(day)
    val start = minOf(wake ?: 360, 360)
    val end = maxOf(bed ?: 1320, 1320)
    val busy = mutableListOf<ScheduleTimelineItem>()
    val previousBed = anchors.bedMinute(day.minus(1))?.minus(1440)
    if (wake != null && previousBed != null && previousBed < wake) {
        busy += ScheduleTimelineItem.Anchor(ScheduleTimelineItem.Kind.SLEEP, previousBed, wake)
    }
    val nextWake = anchors.wake[day.plus(1)]?.minutes()?.plus(1440)
    if (bed != null && nextWake != null && bed < nextWake) {
        busy += ScheduleTimelineItem.Anchor(ScheduleTimelineItem.Kind.SLEEP, bed, nextWake)
    }
    intervalsForDay(tasks, anchors.copy(commuteMinutes = 0), day).filter { it.end > 0 && it.start < maxOf(end, 1440) }.forEach { interval ->
        if (interval.school != null) {
            busy += ScheduleTimelineItem.Anchor(ScheduleTimelineItem.Kind.SCHOOL, interval.start, interval.end, interval.school.label)
        } else {
            val task = tasks.first { it.id == interval.taskId }
            busy += ScheduleTimelineItem.Task(task, interval.start, interval.end)
        }
    }
    val sorted = busy.sortedBy { it.start }
    val gaps = mutableListOf<ScheduleTimelineItem.Gap>()
    var cursor = start
    sorted.forEach { item ->
        if (item.start - cursor >= ScheduleThresholds.FREE_TIME_MINUTES && cursor < end) gaps += ScheduleTimelineItem.Gap(cursor, minOf(item.start, end))
        cursor = maxOf(cursor, item.end)
    }
    if (end - cursor >= ScheduleThresholds.FREE_TIME_MINUTES) gaps += ScheduleTimelineItem.Gap(cursor, end)
    return ScheduleTimeline(start, end, (sorted + gaps).sortedBy { it.start })
}
