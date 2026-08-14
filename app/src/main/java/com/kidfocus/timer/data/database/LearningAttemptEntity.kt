package com.kidfocus.timer.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One learning activity. A UUID makes Firestore uploads idempotent across retries. */
@Entity(
    tableName = "learning_attempts",
    indices = [
        Index(value = ["owner_uid", "created_at_millis"]),
        Index(value = ["owner_uid", "synced"]),
    ],
)
data class LearningAttemptEntity(
    @PrimaryKey
    val id: String,
    @ColumnInfo(name = "owner_uid")
    val ownerUid: String?,
    @ColumnInfo(name = "game_id")
    val gameId: String,
    @ColumnInfo(name = "age_band")
    val ageBand: String,
    val score: Int?,
    @ColumnInfo(name = "total_questions")
    val totalQuestions: Int?,
    @ColumnInfo(name = "duration_millis")
    val durationMillis: Long,
    val completed: Boolean,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long,
    val synced: Boolean,
    @ColumnInfo(name = "child_profile_id")
    val childProfileId: String = ChildProfileEntity.DEFAULT_ID,
)
