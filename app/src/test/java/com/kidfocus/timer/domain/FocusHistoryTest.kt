package com.kidfocus.timer.domain

import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.domain.model.buildFocusHistory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusHistoryTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private fun at(day: Int, hour: Int, minute: Int) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test fun midnightBoundaryGroupsByLocalDayAndWeekStartsMonday() {
        val sessions = listOf(
            SessionEntity(id = 1, durationSeconds = 600, isFocus = true,
                timestampMillis = at(10, 23, 59), childProfileId = "be-na"),
            SessionEntity(id = 2, durationSeconds = 1200, isFocus = true,
                timestampMillis = at(11, 0, 1), childProfileId = "be-na"),
            SessionEntity(id = 3, durationSeconds = 3600, isFocus = true,
                timestampMillis = at(11, 8, 0), childProfileId = "another"),
        )
        val result = buildFocusHistory(sessions, emptyList(), emptyList(), "be-na",
            LocalDate.of(2026, 10, 11), zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 11), LocalDate.of(2026, 10, 10)),
            result.days.map { it.date })
        assertEquals(LocalDate.of(2026, 10, 5), result.bars.first().date)
        assertEquals(10, result.bars[5].minutes)
        assertEquals(20, result.bars[6].minutes)
        assertEquals(30, result.weekMinutes)
        assertEquals(2, result.weekSessions)
    }

    @Test fun stoppedEarlyKeepsItsStatusAndRoutineChecksAppearForSameProfile() {
        val routine = RoutineEntity(id = 8, title = "Đánh răng", deadlineMinutes = 1200,
            repeatDaysMask = 127, childProfileId = "be-na")
        val result = buildFocusHistory(
            listOf(SessionEntity(id = 4, durationSeconds = 300, isFocus = true,
                timestampMillis = at(11, 18, 0), childProfileId = "be-na", completed = false)),
            listOf(routine),
            listOf(RoutineCompletionEntity(routineId = 8, occurrenceDate = "2026-10-11",
                scheduledDeadlineMillis = at(11, 20, 0), completedAtMillis = at(11, 19, 0),
                status = "ON_TIME")),
            "be-na", LocalDate.of(2026, 10, 11), zone,
        )
        assertEquals(2, result.days.single().rows.size)
        assertFalse(result.days.single().rows.first { !it.routine }.completed)
        assertTrue(result.days.single().rows.first { it.routine }.completed)
        assertEquals("Đánh răng", result.days.single().rows.first { it.routine }.title)
    }

    @Test fun routineCheckOffBelongsToItsOccurrenceDayEvenAfterMidnight() {
        val routine = RoutineEntity(id = 8, title = "Đánh răng", deadlineMinutes = 1430,
            repeatDaysMask = 127, childProfileId = "be-na")
        val completion = RoutineCompletionEntity(routineId = 8, occurrenceDate = "2026-10-10",
            scheduledDeadlineMillis = at(10, 23, 50), completedAtMillis = at(11, 0, 5),
            status = "LATE")
        val result = buildFocusHistory(emptyList(), listOf(routine), listOf(completion),
            "be-na", LocalDate.of(2026, 10, 11), zone)
        assertEquals(LocalDate.of(2026, 10, 10), result.days.single().date)
    }
}
