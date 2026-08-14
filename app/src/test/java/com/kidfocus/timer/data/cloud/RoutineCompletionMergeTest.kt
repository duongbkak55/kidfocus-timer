package com.kidfocus.timer.data.cloud

import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.domain.model.RoutineStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RoutineCompletionMergeTest {
    @Test
    fun `local on-time completion wins over remote late completion`() {
        val remote = completion(id = 8, status = RoutineStatus.LATE, at = 2_000)
        val local = completion(id = 3, status = RoutineStatus.ON_TIME, at = 1_000)

        val merged = mergeRoutineCompletions(
            remote = listOf(remote),
            local = listOf(local),
            validRoutineIds = setOf(42L),
        ).single()

        assertEquals(8L, merged.id)
        assertEquals(RoutineStatus.ON_TIME.name, merged.status)
        assertEquals(1_000L, merged.completedAtMillis)
    }

    @Test
    fun `local-only completion is retained with a fresh local id`() {
        val local = completion(id = 3, status = RoutineStatus.ON_TIME, at = 1_000)

        val merged = mergeRoutineCompletions(
            remote = emptyList(),
            local = listOf(local),
            validRoutineIds = setOf(42L),
        ).single()

        assertEquals(0L, merged.id)
        assertEquals(RoutineStatus.ON_TIME.name, merged.status)
    }

    private fun completion(
        id: Long,
        status: RoutineStatus,
        at: Long,
    ) = RoutineCompletionEntity(
        id = id,
        routineId = 42,
        occurrenceDate = "2026-08-14",
        scheduledDeadlineMillis = 1_500,
        completedAtMillis = at,
        status = status.name,
    )
}
