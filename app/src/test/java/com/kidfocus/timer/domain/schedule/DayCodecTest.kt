package com.kidfocus.timer.domain.schedule

import java.time.DayOfWeek
import org.junit.Assert.*
import org.junit.Test

class DayCodecTest {
    @Test fun `all seven days round trip Calendar and routine mask`() {
        DayOfWeek.entries.forEach { day ->
            assertEquals(day, DayCodec.fromCalendar(DayCodec.toCalendar(day)))
            assertEquals(setOf(day), DayCodec.fromMask(DayCodec.toMask(setOf(day))))
            assertEquals(setOf(day), DayCodec.fromCalendar(DayCodec.toCalendar(setOf(day))))
        }
        assertEquals(1, DayCodec.toCalendar(DayOfWeek.SUNDAY))
        assertEquals(2, DayCodec.toCalendar(DayOfWeek.MONDAY))
        assertEquals(64, DayCodec.toMask(setOf(DayOfWeek.SUNDAY)))
    }
    @Test fun `every subset survives mask conversion`() {
        (0..127).forEach { assertEquals(it, DayCodec.toMask(DayCodec.fromMask(it))) }
    }
    @Test fun `invalid values are rejected`() {
        assertTrue(runCatching { DayCodec.fromCalendar(0) }.isFailure)
        assertTrue(runCatching { DayCodec.fromCalendar(8) }.isFailure)
        assertTrue(runCatching { DayCodec.fromMask(128) }.isFailure)
    }
    @Test fun `large IDs are unique within a millisecond burst`() {
        val ids = (1..2000).map { ScheduleIds.newId() }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it > Int.MAX_VALUE })
    }
}
