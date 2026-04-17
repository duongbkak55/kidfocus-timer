package com.kidfocus.timer.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing one completed timer session.
 *
 * @property id Auto-generated primary key.
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
) {
    /** Duration expressed as whole minutes, rounded down. */
    val durationMinutes: Int get() = durationSeconds / 60
}
