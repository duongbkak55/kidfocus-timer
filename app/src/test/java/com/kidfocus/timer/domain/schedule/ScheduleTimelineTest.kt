package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleTimelineTest {
    private val days = DayOfWeek.entries.toSet()
    @Test fun `dynamic bounds and read only school sleep exist without tasks`() {
        val anchors = ScheduleAnchors(days.associateWith { LocalTime.of(5, 30) }, days.associateWith { LocalTime.of(22, 30) },
            listOf(Block(days, LocalTime.of(7, 0), LocalTime.of(16, 0), "School")))
        val timeline = buildScheduleTimeline(emptyList(), anchors, DayOfWeek.MONDAY)
        assertEquals(330, timeline.start)
        assertEquals(1350, timeline.end)
        assertEquals(2, timeline.items.filterIsInstance<ScheduleTimelineItem.Anchor>().count { it.kind == ScheduleTimelineItem.Kind.SLEEP })
        assertTrue(timeline.items.filterIsInstance<ScheduleTimelineItem.Anchor>().any { it.kind == ScheduleTimelineItem.Kind.SCHOOL })
    }
    @Test fun `bed after midnight extends through next day and Sunday wraps Monday`() {
        val anchors = ScheduleAnchors(days.associateWith { LocalTime.of(6, 15) }, days.associateWith { LocalTime.of(0, 30) })
        val timeline = buildScheduleTimeline(emptyList(), anchors, DayOfWeek.SUNDAY)
        assertEquals(1470, timeline.end)
        assertTrue(timeline.items.filterIsInstance<ScheduleTimelineItem.Anchor>().any { it.start == 1470 && it.end == 1815 })
    }
    @Test fun `overlapping tasks do not produce false free slots`() {
        val a = ScheduledTask(1, TaskType.CUSTOM, "A", "", 8, 0, setOf(2), 120, 0)
        val b = a.copy(id = 2, hour = 8, minute = 30, focusDurationMinutes = 30)
        val timeline = buildScheduleTimeline(listOf(a, b), ScheduleAnchors(), DayOfWeek.MONDAY)
        assertTrue(timeline.items.filterIsInstance<ScheduleTimelineItem.Gap>().none { it.start in 480..599 })
    }
}
