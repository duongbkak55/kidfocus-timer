package com.kidfocus.timer.domain.daylog

import java.time.LocalDate
import java.util.UUID

enum class DayLogSource { TIMER, ROUTINE, MANUAL, AI }
enum class DayLogCategory { STUDY, HYGIENE, CHORES, ENTERTAINMENT, SCHOOL, SLEEP, WAKE, ROUTINE, OTHER }

data class DayLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val date: LocalDate,
    val taskId: Long? = null,
    val name: String,
    val category: DayLogCategory,
    val startMinute: Int,
    val endMinute: Int? = null,
    val source: DayLogSource,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val deleted: Boolean = false,
) {
    fun validate() {
        require(UUID.fromString(id).toString() == id)
        require(profileId.isNotBlank() && profileId.length <= 64)
        require(name.isNotBlank() && name.length <= 80)
        require(startMinute in 0..1439 && (endMinute == null || endMinute in 0..1439))
        require(taskId == null || taskId > 0)
        require(createdAt > 0 && updatedAt >= createdAt)
    }
    val durationMinutes: Int? get() = endMinute?.let { if (it < startMinute) it + 1440 - startMinute else it - startMinute }
}

/** Equal-version deletes win; other ties use a stable field ordering so devices converge. */
fun newerDayLog(local: DayLogEntry?, incoming: DayLogEntry): DayLogEntry = when {
    local == null || incoming.updatedAt > local.updatedAt -> incoming
    incoming.updatedAt == local.updatedAt && incoming.deleted && !local.deleted -> incoming
    incoming.updatedAt == local.updatedAt && incoming.deleted == local.deleted && incoming.toString() > local.toString() -> incoming
    else -> local
}
