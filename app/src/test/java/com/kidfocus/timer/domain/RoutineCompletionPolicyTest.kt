package com.kidfocus.timer.domain

import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineCompletionPolicy
import com.kidfocus.timer.domain.model.RoutineStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoutineCompletionPolicyTest {
    @Test
    fun `late retry cannot downgrade an on-time completion`() {
        val existing = completion(id = 7, status = RoutineStatus.ON_TIME, completedAt = 1_000)
        val retry = completion(status = RoutineStatus.LATE, completedAt = 2_000)

        assertNull(RoutineCompletionPolicy.better(existing, retry))
    }

    @Test
    fun `on-time result replaces a late result while preserving the database id`() {
        val existing = completion(id = 7, status = RoutineStatus.LATE, completedAt = 2_000)
        val candidate = completion(status = RoutineStatus.ON_TIME, completedAt = 1_000)

        assertEquals(7L, RoutineCompletionPolicy.better(existing, candidate)?.id)
        assertEquals(RoutineStatus.ON_TIME.name, RoutineCompletionPolicy.better(existing, candidate)?.status)
    }

    @Test
    fun `later duplicate of the same status is ignored`() {
        val existing = completion(id = 7, status = RoutineStatus.LATE, completedAt = 2_000)
        val retry = completion(status = RoutineStatus.LATE, completedAt = 3_000)

        assertNull(RoutineCompletionPolicy.better(existing, retry))
    }

    private fun completion(
        id: Long = 0,
        status: RoutineStatus,
        completedAt: Long,
    ) = RoutineCompletionEntity(
        id = id,
        routineId = 42,
        occurrenceDate = "2026-08-10",
        scheduledDeadlineMillis = 1_500,
        completedAtMillis = completedAt,
        status = status.name,
    )
}
