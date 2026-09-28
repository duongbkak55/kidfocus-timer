package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.data.schedule.ScheduleJson
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleAnchorsMapTest {
    @Test fun `cloud map round trip preserves weekday midnight and school blocks`() {
        val anchors = ScheduleAnchors(mapOf(DayOfWeek.MONDAY to LocalTime.of(6, 15)), mapOf(DayOfWeek.SUNDAY to LocalTime.of(0, 30)),
            listOf(Block(setOf(DayOfWeek.MONDAY), LocalTime.of(7, 15), LocalTime.of(16, 30), "School")), 20)
        assertEquals(anchors, ScheduleJson.anchorsFromMap(ScheduleJson.anchorsMap(anchors)))
    }
    @Test fun `invalid days time and commute rejected`() {
        assertTrue(runCatching { ScheduleJson.anchorsFromMap(mapOf("wake" to mapOf("BAD" to "06:15"))) }.isFailure)
        assertTrue(runCatching { ScheduleJson.anchorsFromMap(mapOf("bed" to mapOf("MONDAY" to "24:01"))) }.isFailure)
        assertTrue(runCatching { ScheduleJson.anchorsFromMap(mapOf("commuteMinutes" to 181)) }.isFailure)
    }
}
