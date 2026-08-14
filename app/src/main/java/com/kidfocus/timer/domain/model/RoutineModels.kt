package com.kidfocus.timer.domain.model

import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class RoutineStatus {
    PENDING,
    ON_TIME,
    LATE,
    MISSED,
}

data class TodayRoutine(
    val routine: RoutineEntity,
    val occurrenceDate: LocalDate,
    val deadlineMillis: Long,
    val status: RoutineStatus,
    val completedAtMillis: Long? = null,
) {
    val isCompleted: Boolean get() = status == RoutineStatus.ON_TIME || status == RoutineStatus.LATE
}

object RoutineTime {
    const val WEEKDAYS_MASK = 0b0011111
    const val WEEKEND_MASK = 0b1100000
    const val EVERY_DAY_MASK = WEEKDAYS_MASK or WEEKEND_MASK

    fun dayBit(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    fun deadlineMillis(
        date: LocalDate,
        deadlineMinutes: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long = date.atStartOfDay()
        .plusMinutes(deadlineMinutes.toLong())
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()

    fun toTodayRoutine(
        routine: RoutineEntity,
        date: LocalDate,
        completion: RoutineCompletionEntity?,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): TodayRoutine {
        val deadline = deadlineMillis(date, routine.deadlineMinutes, zoneId)
        val status = when {
            completion?.status == RoutineStatus.ON_TIME.name -> RoutineStatus.ON_TIME
            completion != null -> RoutineStatus.LATE
            nowMillis > deadline -> RoutineStatus.MISSED
            else -> RoutineStatus.PENDING
        }
        return TodayRoutine(
            routine = routine,
            occurrenceDate = date,
            deadlineMillis = deadline,
            status = status,
            completedAtMillis = completion?.completedAtMillis,
        )
    }

    fun dateAt(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()

    fun nextReminder(
        routine: RoutineEntity,
        notBeforeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): RoutineOccurrence? {
        val fromDate = dateAt(notBeforeMillis, zoneId)
        for (offset in 0..7L) {
            val date = fromDate.plusDays(offset)
            if ((routine.repeatDaysMask and dayBit(date.dayOfWeek)) == 0) continue
            val deadline = deadlineMillis(date, routine.deadlineMinutes, zoneId)
            val reminder = deadline - routine.reminderMinutesBefore * 60_000L
            if (reminder > notBeforeMillis) {
                return RoutineOccurrence(date, deadline, reminder)
            }
        }
        return null
    }
}

data class RoutineOccurrence(
    val date: LocalDate,
    val deadlineMillis: Long,
    val reminderMillis: Long,
)

/** A lightweight, schema-free summary derived from routine completion history. */
data class RoutineWeeklySummary(
    val weekStart: LocalDate,
    val completedCount: Int,
    val onTimeCount: Int,
    val lateCount: Int,
    val currentStreakDays: Int,
    val totalCompletedCount: Int,
) {
    val unlockedStickerCount: Int
        get() = STICKER_MILESTONES.count { totalCompletedCount >= it }

    companion object {
        val STICKER_MILESTONES = listOf(1, 3, 5, 10, 20, 50)
    }
}

object RoutineInsights {
    fun weeklySummary(
        completions: List<RoutineCompletionEntity>,
        today: LocalDate,
        totalCompletedCount: Int = completions.size,
    ): RoutineWeeklySummary {
        val weekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val completionDates = completions.mapNotNull { completion ->
            runCatching { LocalDate.parse(completion.occurrenceDate) }.getOrNull()
        }
        val datedCompletions = completions.mapNotNull { completion ->
            runCatching { LocalDate.parse(completion.occurrenceDate) }
                .getOrNull()
                ?.let { it to completion }
        }
        val thisWeek = datedCompletions.filter { (date, _) -> date in weekStart..today }

        val uniqueCompletions = thisWeek
            .distinctBy { (date, completion) -> completion.routineId to date }
            .map { it.second }
        val completedDates = completionDates.toSet()
        var streakCursor = if (today in completedDates) today else today.minusDays(1)
        var streak = 0
        while (streakCursor in completedDates) {
            streak += 1
            streakCursor = streakCursor.minusDays(1)
        }

        return RoutineWeeklySummary(
            weekStart = weekStart,
            completedCount = uniqueCompletions.size,
            onTimeCount = uniqueCompletions.count { it.status == RoutineStatus.ON_TIME.name },
            lateCount = uniqueCompletions.count { it.status == RoutineStatus.LATE.name },
            currentStreakDays = streak,
            totalCompletedCount = totalCompletedCount,
        )
    }
}
