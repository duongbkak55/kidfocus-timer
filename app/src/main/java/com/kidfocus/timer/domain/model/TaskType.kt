package com.kidfocus.timer.domain.model

import java.util.Calendar

enum class TaskType(
    val displayName: String,
    val emoji: String,
    val defaultHour: Int,
    val defaultMinute: Int,
    val defaultFocusMinutes: Int,
    val defaultBreakMinutes: Int,
    val defaultDays: Set<Int>,
) {
    MORNING_STUDY(
        displayName = "Học buổi sáng",
        emoji = "📚",
        defaultHour = 8,
        defaultMinute = 0,
        defaultFocusMinutes = 45,
        defaultBreakMinutes = 10,
        defaultDays = WEEKDAYS,
    ),
    AFTERNOON_STUDY(
        displayName = "Học buổi chiều",
        emoji = "✏️",
        defaultHour = 14,
        defaultMinute = 0,
        defaultFocusMinutes = 45,
        defaultBreakMinutes = 10,
        defaultDays = WEEKDAYS,
    ),
    HOMEWORK(
        displayName = "Làm bài tập",
        emoji = "📝",
        defaultHour = 17,
        defaultMinute = 30,
        defaultFocusMinutes = 60,
        defaultBreakMinutes = 15,
        defaultDays = WEEKDAYS,
    ),
    READING(
        displayName = "Đọc sách",
        emoji = "📖",
        defaultHour = 20,
        defaultMinute = 0,
        defaultFocusMinutes = 30,
        defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),
    WEEKEND_STUDY(
        displayName = "Học cuối tuần",
        emoji = "🌟",
        defaultHour = 9,
        defaultMinute = 0,
        defaultFocusMinutes = 60,
        defaultBreakMinutes = 15,
        defaultDays = WEEKEND,
    ),
    CUSTOM(
        displayName = "Tự tạo",
        emoji = "⭐",
        defaultHour = 9,
        defaultMinute = 0,
        defaultFocusMinutes = 25,
        defaultBreakMinutes = 5,
        defaultDays = WEEKDAYS,
    );

    companion object {
        // Calendar day constants: SUNDAY=1, MONDAY=2, ..., SATURDAY=7
        val WEEKDAYS = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        val WEEKEND = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val ALL_DAYS = WEEKDAYS + WEEKEND
    }
}
