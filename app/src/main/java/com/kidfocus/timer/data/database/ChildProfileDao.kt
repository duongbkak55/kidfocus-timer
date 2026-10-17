package com.kidfocus.timer.data.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ChildProfileDao {
    @Query("SELECT * FROM child_profiles WHERE archived = 0 ORDER BY created_at_millis ASC, id ASC")
    fun observeActive(): Flow<List<ChildProfileEntity>>

    @Query("SELECT * FROM child_profiles ORDER BY created_at_millis ASC, id ASC")
    fun observeAll(): Flow<List<ChildProfileEntity>>

    @Query("SELECT * FROM child_profiles ORDER BY created_at_millis ASC, id ASC")
    suspend fun getAllForSync(): List<ChildProfileEntity>

    @Query("SELECT * FROM child_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ChildProfileEntity?

    @Query("DELETE FROM child_profiles WHERE id = :id AND id != 'default'")
    suspend fun deleteById(id: String)

    @Upsert
    suspend fun upsert(profile: ChildProfileEntity)

    @Upsert
    suspend fun upsertAll(profiles: List<ChildProfileEntity>)
}
