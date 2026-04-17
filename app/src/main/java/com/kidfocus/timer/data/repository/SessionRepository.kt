package com.kidfocus.timer.data.repository

import com.kidfocus.timer.data.database.SessionDao
import com.kidfocus.timer.data.database.SessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for session history backed by Room via [SessionDao].
 */
@Singleton
class SessionRepository @Inject constructor(
    private val sessionDao: SessionDao,
) {
    /**
     * Inserts [session] into the database.
     */
    suspend fun insertSession(session: SessionEntity) =
        sessionDao.insertSession(session)

    /**
     * Returns a [Flow] of all sessions with timestamp >= [sinceMillis], newest first.
     */
    fun getSessionsSince(sinceMillis: Long): Flow<List<SessionEntity>> =
        sessionDao.getSessionsSince(sinceMillis)

    /**
     * Returns a [Flow] of focus-only sessions since [sinceMillis].
     */
    fun getFocusSessionsSince(sinceMillis: Long): Flow<List<SessionEntity>> =
        sessionDao.getFocusSessionsSince(sinceMillis)

    /**
     * Returns the total number of focus sessions ever recorded.
     */
    suspend fun getTotalFocusCount(): Int = sessionDao.getTotalFocusSessionCount()

    /**
     * Deletes all sessions older than [beforeMillis].
     */
    suspend fun pruneOldSessions(beforeMillis: Long) =
        sessionDao.deleteSessionsBefore(beforeMillis)

    /**
     * Wipes the entire session history. Intended for use in tests or a factory-reset flow.
     */
    suspend fun clearAll() = sessionDao.deleteAllSessions()
}
