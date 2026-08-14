package com.kidfocus.timer.domain

import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.domain.model.RoutineStatus
import com.kidfocus.timer.domain.model.RoutineTime
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class RoutineTimeTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val monday = LocalDate.of(2026, 8, 10)

    @Test
    fun `repeat presets map to the expected days`() {
        assertEquals(0b0011111, RoutineTime.WEEKDAYS_MASK)
        assertEquals(0b1100000, RoutineTime.WEEKEND_MASK)
        assertEquals(0b1111111, RoutineTime.EVERY_DAY_MASK)
        assertEquals(0, RoutineTime.WEEKDAYS_MASK and RoutineTime.WEEKEND_MASK)
    }

    @Test
    fun `reminder is scheduled before today's deadline`() {
        val routine = routine(
            deadlineMinutes = 22 * 60,
            repeatMask = RoutineTime.dayBit(DayOfWeek.MONDAY),
            reminderMinutes = 30,
        )
        val now = monday.atTime(20, 0).atZone(zone).toInstant().toEpochMilli()

        val occurrence = RoutineTime.nextReminder(routine, now, zone)!!

        assertEquals(monday, occurrence.date)
        assertEquals(
            monday.atTime(21, 30).atZone(zone).toInstant().toEpochMilli(),
            occurrence.reminderMillis,
        )
    }

    @Test
    fun `passed reminder advances to next selected week`() {
        val routine = routine(
            deadlineMinutes = 22 * 60,
            repeatMask = RoutineTime.dayBit(DayOfWeek.MONDAY),
            reminderMinutes = 30,
        )
        val now = monday.atTime(21, 31).atZone(zone).toInstant().toEpochMilli()

        val occurrence = RoutineTime.nextReminder(routine, now, zone)!!

        assertEquals(monday.plusWeeks(1), occurrence.date)
    }

    @Test
    fun `unfinished routine becomes missed after deadline`() {
        val routine = routine(deadlineMinutes = 20 * 60)
        val now = monday.atTime(20, 1).atZone(zone).toInstant().toEpochMilli()

        val item = RoutineTime.toTodayRoutine(routine, monday, null, now, zone)

        assertEquals(RoutineStatus.MISSED, item.status)
    }

    @Test
    fun `completion status comes from stored occurrence`() {
        val routine = routine(deadlineMinutes = 20 * 60)
        val deadline = RoutineTime.deadlineMillis(monday, routine.deadlineMinutes, zone)
        val completion = RoutineCompletionEntity(
            routineId = routine.id,
            occurrenceDate = monday.toString(),
            scheduledDeadlineMillis = deadline,
            completedAtMillis = deadline - 1_000,
            status = RoutineStatus.ON_TIME.name,
        )

        val item = RoutineTime.toTodayRoutine(routine, monday, completion, deadline + 60_000, zone)

        assertEquals(RoutineStatus.ON_TIME, item.status)
    }

    private fun routine(
        deadlineMinutes: Int,
        repeatMask: Int = RoutineTime.EVERY_DAY_MASK,
        reminderMinutes: Int = 15,
    ) = RoutineEntity(
        id = 1L,
        title = "Đi ngủ",
        deadlineMinutes = deadlineMinutes,
        repeatDaysMask = repeatMask,
        reminderMinutesBefore = reminderMinutes,
    )
}
