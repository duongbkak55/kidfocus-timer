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
    open suspend fun addAiBatch(rows: List<DayLogEntryEntity>) {
        require(rows.isNotEmpty() && rows.size <= 30 && rows.map { it.id }.distinct().size == rows.size)
        rows.forEach { require(get(it.id) == null && it.source == "AI" && !it.deleted); it.toEntry().validate() }
        rows.forEach { put(it) }
    }
    @Transaction
    open suspend fun undoAiBatch(expected: List<DayLogEntryEntity>) {
        val current = expected.map { get(it.id) }
        check(current == expected && expected.all { it.source == "AI" && !it.deleted }) { "STALE" }
        expected.forEach { put(it.copy(deleted = true, updatedAt = maxOf(System.currentTimeMillis(), it.updatedAt + 1))) }
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
