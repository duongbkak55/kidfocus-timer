package com.kidfocus.timer.domain.schedule

import java.time.DayOfWeek
import java.time.LocalTime

data class ScheduleAnchors(
    val wake: Map<DayOfWeek, LocalTime> = emptyMap(),
    val bed: Map<DayOfWeek, LocalTime> = emptyMap(),
    val school: List<Block> = emptyList(),
    val commuteMinutes: Int = 0,
) {
    fun validate() {
        require(commuteMinutes in 0..180)
        school.forEach {
            require(it.days.isNotEmpty() && it.start != it.end && it.label.isNotBlank())
        }
    }

    /** Bed belongs to the waking day: MON 00:30 means Monday night, after midnight. */
    fun bedMinute(day: DayOfWeek): Int? = bed[day]?.minutes()?.let { minute ->
        if (minute < (wake[day]?.minutes() ?: 12 * 60)) minute + 1440 else minute
    }
}

data class Block(
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val end: LocalTime,
    val label: String,
)

fun LocalTime.minutes(): Int = hour * 60 + minute
fun timeAtMinute(minute: Int): LocalTime = LocalTime.ofSecondOfDay(Math.floorMod(minute, 1440) * 60L)

object ScheduleThresholds {
    val sleepMinutes = mapOf("2-3" to 660, "4-5" to 600, "l1" to 540, "l2" to 540, "l3" to 540)
    val focusMinutes = mapOf("2-3" to 15, "4-5" to 20, "l1" to 30, "l2" to 40, "l3" to 40)
    const val SOCIAL_JETLAG_MINUTES = 60
    const val BEFORE_BED_MINUTES = 60
    const val HOMEWORK_START_LEAD_MINUTES = 90
    const val MORNING_PATTERN_DAYS = 7L
    const val MORNING_BAD_DAYS = 3
    const val FREE_TIME_MINUTES = 60
    const val MORNING_END_MINUTES = 12 * 60
    const val HISTORY_DAYS = 14L
}
