package com.kidfocus.timer.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningAttemptDao {
    @Upsert
    suspend fun upsert(attempt: LearningAttemptEntity)

    @Upsert
    suspend fun upsertAll(attempts: List<LearningAttemptEntity>)

    @Query(
        """
        SELECT * FROM learning_attempts
        WHERE (:ownerUid IS NULL AND owner_uid IS NULL)
           OR (:ownerUid IS NOT NULL AND owner_uid = :ownerUid)
        ORDER BY created_at_millis DESC
        """,
    )
    fun observeForOwner(ownerUid: String?): Flow<List<LearningAttemptEntity>>

    @Query(
        """
        SELECT * FROM learning_attempts
        WHERE child_profile_id = :profileId
          AND ((:ownerUid IS NULL AND owner_uid IS NULL)
            OR (:ownerUid IS NOT NULL AND owner_uid = :ownerUid))
        ORDER BY created_at_millis DESC
        """,
    )
    fun observeForOwnerAndProfile(ownerUid: String?, profileId: String): Flow<List<LearningAttemptEntity>>

    @Query(
        """
        SELECT * FROM learning_attempts
        WHERE synced = 0 AND (owner_uid IS NULL OR owner_uid = :ownerUid)
        ORDER BY created_at_millis ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingForOwner(ownerUid: String, limit: Int = 400): List<LearningAttemptEntity>

    @Query("UPDATE learning_attempts SET owner_uid = :ownerUid WHERE owner_uid IS NULL")
    suspend fun claimOfflineAttempts(ownerUid: String)

    @Query("UPDATE learning_attempts SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)
}
