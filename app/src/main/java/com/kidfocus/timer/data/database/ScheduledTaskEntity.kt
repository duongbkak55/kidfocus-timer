package com.kidfocus.timer.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskType: String,
    val name: String,
    val emoji: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: String, // comma-separated ints, e.g. "2,3,4,5,6"
    val focusDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val enabled: Boolean,
    val isCustom: Boolean,
) {
    fun toDomain() = ScheduledTask(
        id = id,
        taskType = TaskType.valueOf(taskType),
        name = name,
        emoji = emoji,
        hour = hour,
        minute = minute,
        daysOfWeek = daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
        focusDurationMinutes = focusDurationMinutes,
        breakDurationMinutes = breakDurationMinutes,
        enabled = enabled,
        isCustom = isCustom,
    )

    companion object {
        fun fromDomain(task: ScheduledTask) = ScheduledTaskEntity(
            id = task.id,
            taskType = task.taskType.name,
            name = task.name,
            emoji = task.emoji,
            hour = task.hour,
            minute = task.minute,
            daysOfWeek = task.daysOfWeek.joinToString(","),
            focusDurationMinutes = task.focusDurationMinutes,
            breakDurationMinutes = task.breakDurationMinutes,
            enabled = task.enabled,
            isCustom = task.isCustom,
        )
    }
}
