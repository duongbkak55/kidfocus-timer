package com.kidfocus.timer.data.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Durable per-account sync state, including tombstones and offline writes. */
@Entity(tableName = "sync_records", primaryKeys = ["ownerUid", "collection", "docId"])
data class SyncRecordEntity(
    val ownerUid: String,
    val collection: String,
    val docId: String,
    val payloadJson: String,
    val fingerprint: String,
    val updatedAtMillis: Long,
    val deviceId: String,
    val deleted: Boolean,
    val dirty: Boolean,
)

@Dao
interface SyncRecordDao {
    @Query("SELECT * FROM sync_records WHERE ownerUid = :uid")
    suspend fun forUser(uid: String): List<SyncRecordEntity>

    @Query("SELECT * FROM sync_records WHERE ownerUid = :uid AND collection = :collection AND docId = :id LIMIT 1")
    suspend fun get(uid: String, collection: String, id: String): SyncRecordEntity?

    @Query("SELECT * FROM sync_records WHERE ownerUid = :uid AND dirty = 1 ORDER BY updatedAtMillis, collection, docId")
    suspend fun dirty(uid: String): List<SyncRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SyncRecordEntity)
}
