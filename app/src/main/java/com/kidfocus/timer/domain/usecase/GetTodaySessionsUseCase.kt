package com.kidfocus.timer.domain.usecase

import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.data.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject

/**
 * Use case that returns all sessions recorded on today's calendar date.
 *
 * Uses midnight of the current day as the lower timestamp bound so that
 * sessions from previous days are excluded.
 */
class GetTodaySessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    /**
     * Returns a [Flow] that emits the list of today's [SessionEntity] instances,
     * re-emitting whenever a new session is inserted.
     */
    operator fun invoke(): Flow<List<SessionEntity>> {
        val startOfDay = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return sessionRepository.getSessionsSince(startOfDay)
    }
}
