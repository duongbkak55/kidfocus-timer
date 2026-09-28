package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.data.schedule.SchedulePlanJson
import java.time.DayOfWeek.*
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class SchedulePlanTest {
    private val today = LocalDate.of(2026, 9, 28)
    private val anchors = ScheduleAnchors(bed = mapOf(MONDAY to LocalTime.of(22, 30), TUESDAY to LocalTime.of(22, 15)), wake = mapOf(MONDAY to LocalTime.of(6, 0)))
    @Test fun `15 minute steps every 3 calendar days reach exact target across different initial hours`() {
        var plan = SchedulePlan.create(PlanKind.BED, setOf(MONDAY, TUESDAY), LocalTime.of(21, 30), anchors, today)
        assertEquals(4, plan.totalSteps)
        assertEquals(LocalTime.of(22, 15), plan.timesAt(1)[MONDAY]); assertEquals(LocalTime.of(22, 0), plan.timesAt(1)[TUESDAY])
        assertTrue(plan.due(today)); assertFalse(plan.due(today.minusDays(1)))
        assertEquals(plan, SchedulePlanJson.fromMap(SchedulePlanJson.toMap(plan)))
        var hours = anchors
        for (step in 1..4) {
            assertTrue(plan.matches(hours))
            hours = hours.copy(bed = hours.bed + plan.timesAt(step))
            val next = plan.advanced(today.plusDays((step - 1) * 3L))
            if (step < 4) { plan = next!!; assertEquals(step + 1, plan.nextStep); assertFalse(plan.due(today.plusDays((step - 1) * 3L))) }
            else assertNull(next)
        }
        assertTrue(hours.bed.values.all { it == LocalTime.of(21, 30) })
    }
    @Test fun `midnight uses short time distance and late apply waits 3 more days without catchup`() {
        val midnight = anchors.copy(bed = mapOf(SUNDAY to LocalTime.of(0, 15)))
        val plan = SchedulePlan.create(PlanKind.BED, setOf(SUNDAY), LocalTime.of(23, 15), midnight, today)
        assertEquals(4, plan.totalSteps)
        assertEquals(LocalTime.MIDNIGHT, plan.timesAt(1)[SUNDAY])
        assertEquals(LocalTime.of(23, 45), plan.timesAt(2)[SUNDAY])
        assertEquals(today.plusDays(13), plan.advanced(today.plusDays(10))!!.nextOn)
    }
    @Test fun `wake plans manual edit mismatch unrelated anchors and final fractional step`() {
        val plan = SchedulePlan.create(PlanKind.WAKE, setOf(MONDAY), LocalTime.of(6, 40), anchors, today)
        assertEquals(3, plan.totalSteps); assertEquals(LocalTime.of(6, 40), plan.timesAt(3)[MONDAY])
        assertTrue(plan.matches(anchors.copy(bed = emptyMap())))
        assertFalse(plan.matches(anchors.copy(wake = mapOf(MONDAY to LocalTime.of(6, 1)))))
        assertTrue(runCatching { SchedulePlan.create(PlanKind.BED, setOf(SUNDAY), LocalTime.of(21, 0), anchors, today) }.isFailure)
    }
}
