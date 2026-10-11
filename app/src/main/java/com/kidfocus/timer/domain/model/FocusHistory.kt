package com.kidfocus.timer.domain.model

import com.kidfocus.timer.data.database.RoutineCompletionEntity
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.data.database.SessionEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FocusHistoryBar(val date: LocalDate, val minutes: Int)

data class FocusHistoryRow(
    val id: Long?,
    val timestampMillis: Long,
    val title: String?,
    val durationSeconds: Int?,
    val completed: Boolean,
    val routine: Boolean,
    val occurrenceDate: LocalDate? = null,
)

data class FocusHistoryDay(val date: LocalDate, val rows: List<FocusHistoryRow>)

data class FocusHistory(
    val bars: List<FocusHistoryBar>,
    val weekMinutes: Int,
    val weekSessions: Int,
    val days: List<FocusHistoryDay>,
)

/** Calendar-day grouping uses the device's local zone, including midnight boundaries. */
fun buildFocusHistory(
    sessions: List<SessionEntity>,
    routines: List<RoutineEntity>,
    completions: List<RoutineCompletionEntity>,
    profileId: String,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): FocusHistory {
    val focus = sessions.filter { it.isFocus && it.childProfileId == profileId }
    val routineTitles = routines.filter { it.childProfileId == profileId }.associateBy { it.id }
    val rows = focus.map { session ->
        FocusHistoryRow(session.id, session.timestampMillis, session.taskTitle,
            session.durationSeconds, session.completed, false)
    } + completions.mapNotNull { completion ->
        val routine = routineTitles[completion.routineId] ?: return@mapNotNull null
        FocusHistoryRow(null, completion.completedAtMillis, routine.title, null, true, true,
            runCatching { LocalDate.parse(completion.occurrenceDate) }.getOrNull())
    }
    val firstDay = today.minusDays((today.dayOfWeek.value - 1).toLong())
    val bars = (0L..6L).map { offset ->
        val date = firstDay.plusDays(offset)
        FocusHistoryBar(date, focus.filter { it.timestampMillis.localDate(zone) == date }
            .sumOf { it.durationMinutes })
    }
    return FocusHistory(
        bars = bars,
        weekMinutes = bars.sumOf { it.minutes },
        weekSessions = focus.count { it.timestampMillis.localDate(zone) in firstDay..firstDay.plusDays(6) },
        days = rows.groupBy { it.occurrenceDate ?: it.timestampMillis.localDate(zone) }
            .toSortedMap(reverseOrder())
            .map { (date, grouped) -> FocusHistoryDay(date, grouped.sortedByDescending { it.timestampMillis }) },
    )
}

private fun Long.localDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
