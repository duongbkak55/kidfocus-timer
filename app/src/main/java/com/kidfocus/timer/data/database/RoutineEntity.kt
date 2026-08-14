package com.kidfocus.timer.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A recurring, wall-clock routine configured by a parent. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val emoji: String = "⭐",
    @ColumnInfo(name = "deadline_minutes")
    val deadlineMinutes: Int,
    @ColumnInfo(name = "repeat_days_mask")
    val repeatDaysMask: Int,
    @ColumnInfo(name = "reminder_minutes_before")
    val reminderMinutesBefore: Int = 15,
    val enabled: Boolean = true,
    @ColumnInfo(name = "linked_timer_minutes")
    val linkedTimerMinutes: Int? = null,
    @ColumnInfo(name = "created_at_millis")
    val createdAtMillis: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "child_profile_id")
    val childProfileId: String = ChildProfileEntity.DEFAULT_ID,
    @ColumnInfo(name = "photo_uri")
    val photoUri: String? = null,
)

/** The completion result for one routine on one calendar date. */
@Entity(
    tableName = "routine_completions",
    foreignKeys = [
        ForeignKey(
            entity = RoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routine_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["routine_id"]),
        Index(value = ["routine_id", "occurrence_date"], unique = true),
    ],
)
data class RoutineCompletionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "routine_id")
    val routineId: Long,
    @ColumnInfo(name = "occurrence_date")
    val occurrenceDate: String,
    @ColumnInfo(name = "scheduled_deadline_millis")
    val scheduledDeadlineMillis: Long,
    @ColumnInfo(name = "completed_at_millis")
    val completedAtMillis: Long,
    val status: String,
)
