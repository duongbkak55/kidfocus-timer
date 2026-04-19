package com.kidfocus.timer.domain.usecase

import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.data.repository.SessionRepository
import javax.inject.Inject

/**
 * Use case for recording a completed focus or break session into Room.
 *
 * @param sessionRepository Persistence layer for session history.
 */
class RecordSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    /**
     * Persists a completed session.
     *
     * @param durationSeconds Total seconds the session lasted.
     * @param isFocus True for a focus session, false for a break.
     * @param timestampMillis Wall-clock time when the session ended (defaults to now).
     */
    suspend operator fun invoke(
        durationSeconds: Int,
        isFocus: Boolean,
        timestampMillis: Long = System.currentTimeMillis(),
    ) {
        if (durationSeconds <= 0) return
        val entity = SessionEntity(
            durationSeconds = durationSeconds,
            isFocus = isFocus,
            timestampMillis = timestampMillis,
        )
        sessionRepository.insertSession(entity)
        val cutoffMillis = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        sessionRepository.pruneOldSessions(cutoffMillis)
    }
}
