package com.kidfocus.timer.domain.model

import java.util.Calendar

enum class TaskType(
    val displayName: String,
    val emoji: String,
    val category: TaskCategory,
    val defaultHour: Int,
    val defaultMinute: Int,
    val defaultFocusMinutes: Int,
    val defaultBreakMinutes: Int,
    val defaultDays: Set<Int>,
) {

    // ---- Học tập -------------------------------------------------------------------------------

    MORNING_STUDY(
        displayName = "Học buổi sáng",
        emoji = "📖",
        category = TaskCategory.STUDY,
        defaultHour = 8, defaultMinute = 0,
        defaultFocusMinutes = 45, defaultBreakMinutes = 10,
        defaultDays = WEEKDAYS,
    ),
    AFTERNOON_STUDY(
        displayName = "Học buổi chiều",
        emoji = "✏️",
        category = TaskCategory.STUDY,
        defaultHour = 14, defaultMinute = 0,
        defaultFocusMinutes = 45, defaultBreakMinutes = 10,
        defaultDays = WEEKDAYS,
    ),
    HOMEWORK(
        displayName = "Làm bài tập",
        emoji = "📝",
        category = TaskCategory.STUDY,
        defaultHour = 17, defaultMinute = 30,
        defaultFocusMinutes = 60, defaultBreakMinutes = 15,
        defaultDays = WEEKDAYS,
    ),
    READING(
        displayName = "Đọc sách",
        emoji = "📚",
        category = TaskCategory.STUDY,
        defaultHour = 20, defaultMinute = 0,
        defaultFocusMinutes = 30, defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),
    WEEKEND_STUDY(
        displayName = "Học cuối tuần",
        emoji = "🌟",
        category = TaskCategory.STUDY,
        defaultHour = 9, defaultMinute = 0,
        defaultFocusMinutes = 60, defaultBreakMinutes = 15,
        defaultDays = WEEKEND,
    ),
    MUSIC_PRACTICE(
        displayName = "Học nhạc / Tập đàn",
        emoji = "🎵",
        category = TaskCategory.STUDY,
        defaultHour = 16, defaultMinute = 0,
        defaultFocusMinutes = 30, defaultBreakMinutes = 5,
        defaultDays = setOf(Calendar.TUESDAY, Calendar.THURSDAY),
    ),

    // ---- Vệ sinh & Sức khỏe ------------------------------------------------------------------

    BATH(
        displayName = "Tắm rửa",
        emoji = "🛁",
        category = TaskCategory.HYGIENE,
        defaultHour = 19, defaultMinute = 0,
        defaultFocusMinutes = 15, defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),
    BRUSH_TEETH(
        displayName = "Đánh răng buổi tối",
        emoji = "🪥",
        category = TaskCategory.HYGIENE,
        defaultHour = 21, defaultMinute = 0,
        defaultFocusMinutes = 5, defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),
    EXERCISE(
        displayName = "Tập thể dục",
        emoji = "🏃",
        category = TaskCategory.HYGIENE,
        defaultHour = 6, defaultMinute = 30,
        defaultFocusMinutes = 30, defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),
    SLEEP(
        displayName = "Chuẩn bị đi ngủ",
        emoji = "😴",
        category = TaskCategory.HYGIENE,
        defaultHour = 21, defaultMinute = 30,
        defaultFocusMinutes = 15, defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),

    // ---- Sinh hoạt hàng ngày -----------------------------------------------------------------

    MAKE_BED(
        displayName = "Dọn giường",
        emoji = "🛏️",
        category = TaskCategory.CHORES,
        defaultHour = 7, defaultMinute = 0,
        defaultFocusMinutes = 10, defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),
    CLEAN_ROOM(
        displayName = "Dọn phòng",
        emoji = "🧹",
        category = TaskCategory.CHORES,
        defaultHour = 9, defaultMinute = 0,
        defaultFocusMinutes = 30, defaultBreakMinutes = 5,
        defaultDays = WEEKEND,
    ),
    WASH_DISHES(
        displayName = "Rửa bát",
        emoji = "🍽️",
        category = TaskCategory.CHORES,
        defaultHour = 18, defaultMinute = 30,
        defaultFocusMinutes = 15, defaultBreakMinutes = 5,
        defaultDays = WEEKDAYS,
    ),

    // ---- Giải trí ----------------------------------------------------------------------------

    GAME_TIME(
        displayName = "Chơi game",
        emoji = "🎮",
        category = TaskCategory.ENTERTAINMENT,
        defaultHour = 16, defaultMinute = 0,
        defaultFocusMinutes = 30, defaultBreakMinutes = 5,
        defaultDays = WEEKDAYS,
    ),
    TV_TIME(
        displayName = "Xem TV / YouTube",
        emoji = "📺",
        category = TaskCategory.ENTERTAINMENT,
        defaultHour = 17, defaultMinute = 0,
        defaultFocusMinutes = 30, defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),
    OUTDOOR_PLAY(
        displayName = "Vui chơi ngoài trời",
        emoji = "🌳",
        category = TaskCategory.ENTERTAINMENT,
        defaultHour = 16, defaultMinute = 30,
        defaultFocusMinutes = 45, defaultBreakMinutes = 5,
        defaultDays = ALL_DAYS,
    ),
    ART(
        displayName = "Vẽ & Sáng tạo",
        emoji = "🎨",
        category = TaskCategory.ENTERTAINMENT,
        defaultHour = 15, defaultMinute = 0,
        defaultFocusMinutes = 45, defaultBreakMinutes = 10,
        defaultDays = WEEKEND,
    ),

    // ---- Tùy chỉnh ---------------------------------------------------------------------------

    CUSTOM(
        displayName = "Tự tạo",
        emoji = "⭐",
        category = TaskCategory.STUDY,
        defaultHour = 9, defaultMinute = 0,
        defaultFocusMinutes = 25, defaultBreakMinutes = 5,
        defaultDays = WEEKDAYS,
    );

    companion object {
        val WEEKDAYS = setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        val WEEKEND = setOf(Calendar.SATURDAY, Calendar.SUNDAY)
        val ALL_DAYS = WEEKDAYS + WEEKEND

        /** All predefined types grouped by category (excludes CUSTOM). */
        fun byCategory(): Map<TaskCategory, List<TaskType>> =
            TaskCategory.entries.associateWith { cat ->
                entries.filter { it != CUSTOM && it.category == cat }
            }
    }
}
