package com.kidfocus.timer.domain.model

data class ScheduledTask(
    val id: Long = 0,
    val taskType: TaskType,
    val name: String,
    val emoji: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: Set<Int>,
    val focusDurationMinutes: Int,
    val breakDurationMinutes: Int,
    val enabled: Boolean = true,
    val isCustom: Boolean = false,
    val childProfileId: String = "default",
    val photoUri: String? = null,
) {
    val timeFormatted: String get() = "%02d:%02d".format(hour, minute)
}
