package com.kidfocus.timer.domain

import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.domain.model.RoutineInsights
import com.kidfocus.timer.domain.model.RoutineStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RoutineInsightsTest {
    private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val monday = LocalDate.of(2026, 8, 10)

    @Test
    fun `weekly summary counts recorded completions without a schema change`() {
        val completions = listOf(
            completion(1, monday, RoutineStatus.ON_TIME),
            completion(2, monday, RoutineStatus.LATE),
            completion(2, monday.plusDays(1), RoutineStatus.ON_TIME),
        )

        val summary = RoutineInsights.weeklySummary(
            completions = completions,
            today = monday.plusDays(1),
            totalCompletedCount = completions.size,
        )

        assertEquals(3, summary.completedCount)
        assertEquals(2, summary.onTimeCount)
        assertEquals(1, summary.lateCount)
        assertEquals(2, summary.currentStreakDays)
        assertEquals(2, summary.unlockedStickerCount)
    }

    @Test
    fun `today without a completion keeps yesterday's streak`() {
        val completions = listOf(
            completion(1, monday, RoutineStatus.ON_TIME),
            completion(1, monday.plusDays(1), RoutineStatus.ON_TIME),
        )

        val summary = RoutineInsights.weeklySummary(
            completions = completions,
            today = monday.plusDays(2),
        )

        assertEquals(2, summary.currentStreakDays)
    }

    private fun completion(
        routineId: Long,
        date: LocalDate,
        status: RoutineStatus,
    ) = RoutineCompletionEntity(
        routineId = routineId,
        occurrenceDate = date.toString(),
        scheduledDeadlineMillis = date.atTime(20, 0).atZone(zone).toInstant().toEpochMilli(),
        completedAtMillis = date.atTime(19, 0).atZone(zone).toInstant().toEpochMilli(),
        status = status.name,
    )
}
