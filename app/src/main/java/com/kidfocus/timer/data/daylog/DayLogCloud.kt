package com.kidfocus.timer.data.daylog

import com.kidfocus.timer.domain.daylog.*
import java.time.LocalDate

fun DayLogEntry.toDayLogCloud(): Map<String, Any?> = mapOf(
    "profile_id" to profileId, "date" to date.toString(), "task_id" to taskId, "name" to name,
    "category" to category.name, "start_minute" to startMinute, "end_minute" to endMinute,
    "source" to source.name, "created_at" to createdAt, "updated_at" to updatedAt, "deleted" to deleted,
)

fun dayLogFromCloud(id: String, data: Map<String, Any?>?): DayLogEntry? = runCatching {
    val d = requireNotNull(data)
    fun number(key: String): Long = (d[key] as Number).let {
        val value = it.toLong(); require(it.toDouble().isFinite() && it.toDouble() == value.toDouble()); value
    }
    DayLogEntry(id, d["profile_id"] as String, LocalDate.parse(d["date"] as String),
        if (d["task_id"] == null) null else number("task_id"), d["name"] as String,
        DayLogCategory.valueOf(d["category"] as String), number("start_minute").toInt(),
        if (d["end_minute"] == null) null else number("end_minute").toInt(),
        DayLogSource.valueOf(d["source"] as String), number("created_at"), number("updated_at"), d["deleted"] as Boolean)
        .also { it.validate(); require(number("start_minute") in 0..1439 && (d["end_minute"] == null || number("end_minute") in 0..1439)) }
}.getOrNull()
