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
) {
    val timeFormatted: String get() = "%02d:%02d".format(hour, minute)

    val daysLabel: String get() {
        if (daysOfWeek == TaskType.ALL_DAYS) return "Hàng ngày"
        if (daysOfWeek == TaskType.WEEKDAYS) return "Thứ 2 – Thứ 6"
        if (daysOfWeek == TaskType.WEEKEND) return "Thứ 7 – CN"
        val names = mapOf(2 to "T2", 3 to "T3", 4 to "T4", 5 to "T5", 6 to "T6", 7 to "T7", 1 to "CN")
        return (2..7).plus(1).filter { it in daysOfWeek }.mapNotNull { names[it] }.joinToString(", ")
    }
}
