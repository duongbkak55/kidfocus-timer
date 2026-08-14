package com.kidfocus.timer.data.database

import com.kidfocus.timer.domain.model.RoutineStatus

/** Deterministic conflict policy shared by local completion writes. */
object RoutineCompletionPolicy {
    fun better(
        existing: RoutineCompletionEntity?,
        candidate: RoutineCompletionEntity,
    ): RoutineCompletionEntity? {
        if (existing == null) return candidate
        val existingOnTime = existing.status == RoutineStatus.ON_TIME.name
        val candidateOnTime = candidate.status == RoutineStatus.ON_TIME.name
        return when {
            existingOnTime && !candidateOnTime -> null
            candidateOnTime && !existingOnTime -> candidate.copy(id = existing.id)
            candidate.completedAtMillis < existing.completedAtMillis -> candidate.copy(id = existing.id)
            else -> null
        }
    }
}
