package com.kidfocus.timer.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kidfocus.timer.domain.daylog.*
import java.time.LocalDate

@Entity(tableName = "day_log_entries", indices = [Index(value = ["profile_id", "date"])])
data class DayLogEntryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
    val date: String,
    @ColumnInfo(name = "task_id") val taskId: Long?,
    val name: String,
    val category: String,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int?,
    val source: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    val deleted: Boolean,
) {
    fun toEntry() = DayLogEntry(id, profileId, LocalDate.parse(date), taskId, name,
        DayLogCategory.valueOf(category), startMinute, endMinute, DayLogSource.valueOf(source), createdAt, updatedAt, deleted)
    companion object {
        fun fromEntry(e: DayLogEntry) = DayLogEntryEntity(e.id, e.profileId, e.date.toString(), e.taskId,
            e.name, e.category.name, e.startMinute, e.endMinute, e.source.name, e.createdAt, e.updatedAt, e.deleted)
    }
}
