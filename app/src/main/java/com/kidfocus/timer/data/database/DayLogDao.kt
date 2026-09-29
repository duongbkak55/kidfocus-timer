package com.kidfocus.timer.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.kidfocus.timer.domain.daylog.newerDayLog

@Dao
abstract class DayLogDao {
    @Query("SELECT * FROM day_log_entries WHERE profile_id = :profileId ORDER BY date, start_minute, id")
    abstract fun observeProfile(profileId: String): Flow<List<DayLogEntryEntity>>
    @Query("SELECT * FROM day_log_entries ORDER BY updated_at, id")
    abstract fun observeAll(): Flow<List<DayLogEntryEntity>>
    @Query("SELECT * FROM day_log_entries ORDER BY updated_at, id")
    abstract suspend fun getAll(): List<DayLogEntryEntity>
    @Query("SELECT * FROM day_log_entries WHERE id = :id")
    abstract suspend fun get(id: String): DayLogEntryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun put(row: DayLogEntryEntity)
    @Transaction
    open suspend fun merge(row: DayLogEntryEntity) {
        row.toEntry().validate()
        val old = get(row.id)
        if (newerDayLog(old?.toEntry(), row.toEntry()) != old?.toEntry()) put(row)
    }
    @Transaction
    open suspend fun edit(id: String, transform: (DayLogEntryEntity) -> DayLogEntryEntity) {
        val old = get(id) ?: return
        val row = transform(old)
        if (row == old) return
        row.toEntry().validate()
        require(row.id == old.id && row.profileId == old.profileId)
        put(row.copy(updatedAt = maxOf(row.updatedAt, old.updatedAt + 1)))
    }
}
