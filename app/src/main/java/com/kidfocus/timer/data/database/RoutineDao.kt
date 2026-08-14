package com.kidfocus.timer.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY deadline_minutes ASC, id ASC")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines ORDER BY id ASC")
    suspend fun getAllForSync(): List<RoutineEntity>

    @Query("SELECT * FROM routine_completions ORDER BY id ASC")
    fun observeAllCompletions(): Flow<List<RoutineCompletionEntity>>

    @Query("SELECT * FROM routine_completions ORDER BY id ASC")
    suspend fun getAllCompletionsForSync(): List<RoutineCompletionEntity>

    @Query(
        """
        SELECT * FROM routines
        WHERE enabled = 1 AND (repeat_days_mask & :dayBit) != 0
        ORDER BY deadline_minutes ASC, id ASC
        """
    )
    fun observeForDay(dayBit: Int): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routine_completions WHERE occurrence_date = :date")
    fun observeCompletions(date: String): Flow<List<RoutineCompletionEntity>>

    @Query("SELECT * FROM routine_completions WHERE occurrence_date >= :date ORDER BY occurrence_date ASC, routine_id ASC")
    fun observeCompletionsSince(date: String): Flow<List<RoutineCompletionEntity>>

    @Query("SELECT COUNT(*) FROM routine_completions")
    fun observeCompletionCount(): Flow<Int>

    @Query(
        """
        SELECT routine_completions.* FROM routine_completions
        INNER JOIN routines ON routines.id = routine_completions.routine_id
        WHERE routines.child_profile_id = :profileId
          AND routine_completions.occurrence_date >= :date
        ORDER BY routine_completions.occurrence_date ASC, routine_completions.routine_id ASC
        """
    )
    fun observeCompletionsSinceForProfile(profileId: String, date: String): Flow<List<RoutineCompletionEntity>>

    @Query(
        """
        SELECT COUNT(*) FROM routine_completions
        INNER JOIN routines ON routines.id = routine_completions.routine_id
        WHERE routines.child_profile_id = :profileId
        """
    )
    fun observeCompletionCountForProfile(profileId: String): Flow<Int>

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RoutineEntity?

    @Query("SELECT * FROM routines WHERE enabled = 1")
    suspend fun getEnabled(): List<RoutineEntity>

    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM routines
            WHERE lower(title) = lower(:title)
              AND deadline_minutes = :deadlineMinutes
              AND repeat_days_mask = :repeatDaysMask
              AND child_profile_id = :childProfileId
        )
        """
    )
    suspend fun presetExists(
        title: String,
        deadlineMinutes: Int,
        repeatDaysMask: Int,
        childProfileId: String,
    ): Boolean

    /** Inserts a preset atomically and re-checks duplicates inside the transaction. */
    @Transaction
    suspend fun insertPreset(routines: List<RoutineEntity>): List<RoutineEntity> {
        val inserted = mutableListOf<RoutineEntity>()
        routines.forEach { routine ->
            if (!presetExists(
                    routine.title,
                    routine.deadlineMinutes,
                    routine.repeatDaysMask,
                    routine.childProfileId,
                )
            ) {
                inserted += routine.copy(id = insert(routine))
            }
        }
        return inserted
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routines: List<RoutineEntity>)

    @Update
    suspend fun update(routine: RoutineEntity)

    @Delete
    suspend fun delete(routine: RoutineEntity)

    @Query("SELECT * FROM routine_completions WHERE routine_id = :routineId AND occurrence_date = :date LIMIT 1")
    suspend fun getCompletion(routineId: Long, date: String): RoutineCompletionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCompletionRaw(completion: RoutineCompletionEntity)

    /** Idempotent merge: an existing ON_TIME result can never be downgraded to LATE. */
    @Transaction
    suspend fun saveBestCompletion(completion: RoutineCompletionEntity) {
        val existing = getCompletion(completion.routineId, completion.occurrenceDate)
        RoutineCompletionPolicy.better(existing, completion)?.let { saveCompletionRaw(it) }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCompletions(completions: List<RoutineCompletionEntity>)

    @Query("DELETE FROM routine_completions")
    suspend fun deleteAllCompletions()

    @Query("DELETE FROM routines")
    suspend fun deleteAllRoutines()
}
