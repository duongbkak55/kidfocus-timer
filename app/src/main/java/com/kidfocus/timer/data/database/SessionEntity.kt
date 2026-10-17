package com.kidfocus.timer.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing one finished or manually stopped timer session.
 *
 * @property id Explicit random sync-safe key for new sessions; legacy rows keep their original key.
 * @property durationSeconds How many seconds the session lasted.
 * @property isFocus True for focus sessions, false for break sessions.
 * @property timestampMillis Unix epoch milliseconds when the session ended.
 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,

    @ColumnInfo(name = "is_focus")
    val isFocus: Boolean,

    @ColumnInfo(name = "timestamp_millis")
    val timestampMillis: Long,

    @ColumnInfo(name = "child_profile_id")
    val childProfileId: String = ChildProfileEntity.DEFAULT_ID,
    @ColumnInfo(name = "scheduled_task_id")
    val scheduledTaskId: Long? = null,
    @ColumnInfo(name = "task_id")
    val taskId: String? = null,
    @ColumnInfo(name = "task_title")
    val taskTitle: String? = null,
    @ColumnInfo(name = "completed", defaultValue = "1")
    val completed: Boolean = true,
) {
    /** Duration expressed as whole minutes, rounded down. */
    val durationMinutes: Int get() = durationSeconds / 60
}
