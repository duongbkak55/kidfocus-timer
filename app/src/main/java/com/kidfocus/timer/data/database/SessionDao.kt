package com.kidfocus.timer.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for [SessionEntity].
 */
@Dao
interface SessionDao {

    /**
     * Inserts a session record. Ignores conflicts (should not occur with auto-increment keys).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSession(session: SessionEntity)

    /**
     * Returns all sessions with [timestampMillis] >= [sinceMillis], ordered newest first.
     * Emits a new list whenever the underlying table changes.
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE timestamp_millis >= :sinceMillis
        ORDER BY timestamp_millis DESC
        """
    )
    fun getSessionsSince(sinceMillis: Long): Flow<List<SessionEntity>>

    /**
     * Returns all focus sessions with [timestampMillis] >= [sinceMillis].
     */
    @Query(
        """
        SELECT * FROM sessions
        WHERE timestamp_millis >= :sinceMillis AND is_focus = 1
        ORDER BY timestamp_millis DESC
        """
    )
    fun getFocusSessionsSince(sinceMillis: Long): Flow<List<SessionEntity>>

    /**
     * Returns the total number of focus sessions ever recorded.
     */
    @Query("SELECT COUNT(*) FROM sessions WHERE is_focus = 1")
    suspend fun getTotalFocusSessionCount(): Int

    /**
     * Deletes all sessions older than [beforeMillis]. Useful for cleanup.
     */
    @Query("DELETE FROM sessions WHERE timestamp_millis < :beforeMillis")
    suspend fun deleteSessionsBefore(beforeMillis: Long)

    /**
     * Deletes all session records permanently.
     */
    @Query("DELETE FROM sessions")
    suspend fun deleteAllSessions()
}
