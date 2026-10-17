package com.kidfocus.timer.data.cloud

import com.kidfocus.timer.domain.schedule.ScheduleIds

/** Kotlin port of shared/sync/sync-v2.js. Keep its coercions and conflict policy in lockstep. */
internal object SyncV2Model {
    const val SCHEMA_VERSION = 2
    val collections = listOf("profiles", "tasks", "routines", "sessions", "completions")
    private val ageBands = setOf("2-3", "4-5", "5-6", "l1", "l2", "l3")
    private val statuses = setOf("PENDING", "ON_TIME", "LATE", "MISSED")
    private const val MAX_SAFE = 9_007_199_254_740_991L

    data class Doc(val collection: String, val id: String, val data: Map<String, Any?>)
    data class Parsed(val id: String, val entity: Map<String, Any?>, val updatedAtMillis: Long,
        val deviceId: String, val deleted: Boolean)
    data class Migration(val docs: List<Doc>, val settings: Map<String, Any?>?)

    fun newId(): Long = ScheduleIds.newId()
    private fun number(value: Any?, fallback: Long = 0): Long = when (value) {
        is Number -> value.toDouble().takeIf { it.isFinite() }?.toLong() ?: fallback
        else -> fallback
    }
    private fun text(value: Any?, fallback: String = "") = value as? String ?: fallback
    private fun flag(value: Any?, fallback: Boolean = false) = value as? Boolean ?: fallback
    private fun bounded(value: Any?, fallback: Long, min: Long, max: Long) = number(value, fallback).coerceIn(min, max)
    private fun positiveId(value: Any?): Long? = number(value).takeIf { it in 1..MAX_SAFE && value is Number &&
        value.toDouble() == it.toDouble() }
    private fun nonnegative(value: Any?, fallback: Long = 0) = number(value, fallback).coerceAtLeast(0)
    private fun utf16CodePointPrefix(value: String, limit: Int): String {
        val count = value.codePointCount(0, value.length).coerceAtMost(limit)
        return value.substring(0, value.offsetByCodePoints(0, count))
    }

    fun normalizeDays(value: Any?): String = text(value).split(',').mapNotNull { part ->
        Regex("^\\s*([+-]?\\d+)").find(part)?.groupValues?.get(1)?.toIntOrNull()
    }.filter { it in 1..7 }.distinct().sorted().joinToString(",")

    fun parse(collection: String, source: Map<String, Any?>): Map<String, Any?>? {
        val m = source
        val row: Map<String, Any?> = when (collection) {
            "tasks" -> mapOf(
                "id" to positiveId(m["id"]), "taskType" to text(m["taskType"]),
                "name" to text(m["name"]).trim(), "emoji" to text(m["emoji"], "📚"),
                "hour" to bounded(m["hour"], 0, 0, 23), "minute" to bounded(m["minute"], 0, 0, 59),
                "daysOfWeek" to normalizeDays(m["daysOfWeek"]),
                "focusDurationMinutes" to bounded(m["focusDurationMinutes"], 25, 5, 120),
                "breakDurationMinutes" to bounded(m["breakDurationMinutes"], 5, 1, 30),
                "enabled" to flag(m["enabled"], true), "isCustom" to flag(m["isCustom"]),
                "childProfileId" to text(m["childProfileId"], "default"),
            ).takeIf { it["id"] != null && (it["name"] as String).isNotEmpty() }
            "routines" -> mapOf(
                "id" to positiveId(m["id"]), "title" to text(m["title"]).trim(),
                "emoji" to text(m["emoji"], "⭐"),
                "deadlineMinutes" to bounded(m["deadlineMinutes"], 0, 0, 1439),
                "repeatDaysMask" to bounded(m["repeatDaysMask"], 127, 1, 127),
                "reminderMinutesBefore" to bounded(m["reminderMinutesBefore"], 15, 0, 180),
                "enabled" to flag(m["enabled"], true),
                "linkedTimerMinutes" to m["linkedTimerMinutes"]?.let { bounded(it, 25, 5, 120) },
                "createdAtMillis" to nonnegative(m["createdAtMillis"]),
                "childProfileId" to text(m["childProfileId"], "default"),
            ).takeIf { it["id"] != null && (it["title"] as String).isNotEmpty() }
            "completions" -> mapOf(
                "routineId" to positiveId(m["routineId"]), "occurrenceDate" to text(m["occurrenceDate"]),
                "scheduledDeadlineMillis" to nonnegative(m["scheduledDeadlineMillis"]),
                "completedAtMillis" to nonnegative(m["completedAtMillis"]),
                "status" to text(m["status"]).takeIf(statuses::contains),
            ).takeIf { it["routineId"] != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(it["occurrenceDate"] as String) && it["status"] != null }
            "sessions" -> mapOf(
                "id" to positiveId(m["id"]), "durationSeconds" to nonnegative(m["durationSeconds"]),
                "isFocus" to flag(m["isFocus"]), "timestampMillis" to nonnegative(m["timestampMillis"]),
                "childProfileId" to text(m["childProfileId"], "default"),
                "taskId" to (m["taskId"] as? String)?.takeIf { it.isNotBlank() },
                "taskTitle" to (m["taskTitle"] as? String)?.takeIf { it.isNotBlank() },
                "completed" to flag(m["completed"], true),
            ).takeIf { it["id"] != null && (it["timestampMillis"] as Long) > 0 }
            "profiles" -> mapOf(
                "id" to text(m["id"]).take(64), "name" to text(m["name"]).trim().take(40),
                "avatarEmoji" to utf16CodePointPrefix(text(m["avatarEmoji"], "🐣"), 8),
                "ageBand" to text(m["ageBand"]).takeIf(ageBands::contains).orEmpty().ifEmpty { "4-5" },
                "createdAtMillis" to nonnegative(m["createdAtMillis"]), "archived" to flag(m["archived"]),
            ).takeIf { (it["id"] as String).isNotEmpty() && (it["name"] as String).isNotEmpty() && !(it["id"] as String).contains('/') }
            else -> throw IllegalArgumentException("unknown collection $collection")
        } ?: return null
        return row
    }

    fun docId(collection: String, entity: Map<String, Any?>): String = when (collection) {
        "profiles" -> entity["id"].toString()
        "completions" -> "${entity["routineId"]}_${entity["occurrenceDate"]}"
        else -> entity["id"].toString()
    }

    fun toDoc(collection: String, entity: Map<String, Any?>, nowMillis: Long, deviceId: String,
        deleted: Boolean = false): Doc? {
        val parsed = parse(collection, entity) ?: return null
        return Doc(collection, docId(collection, parsed), parsed + mapOf(
            "updatedAtMillis" to nowMillis, "deviceId" to deviceId,
            "deleted" to deleted, "schemaVersion" to SCHEMA_VERSION,
        ))
    }

    fun fromDoc(collection: String, id: String, data: Map<String, Any?>): Parsed? {
        if (collection !in collections || number(data["schemaVersion"]) != SCHEMA_VERSION.toLong()) return null
        val entity = parse(collection, data) ?: return null
        if (docId(collection, entity) != id) return null
        return Parsed(id, entity, nonnegative(data["updatedAtMillis"]), text(data["deviceId"]), flag(data["deleted"]))
    }

    fun pickWinner(collection: String, local: Parsed?, remote: Parsed?): String {
        if (local == null) return "remote"
        if (remote == null) return "local"
        if (collection == "completions" && !local.deleted && !remote.deleted) {
            val a = local.entity; val b = remote.entity
            val aOn = a["status"] == "ON_TIME"; val bOn = b["status"] == "ON_TIME"
            if (aOn != bOn) return if (aOn) "local" else "remote"
            val aAt = number(a["completedAtMillis"]); val bAt = number(b["completedAtMillis"])
            if (aAt != bAt) return if (aAt < bAt) "local" else "remote"
        }
        if (local.updatedAtMillis != remote.updatedAtMillis)
            return if (local.updatedAtMillis > remote.updatedAtMillis) "local" else "remote"
        return if (remote.deviceId > local.deviceId) "remote" else "local"
    }

    fun parseSettings(m: Map<String, Any?>): Map<String, Any?> = buildMap {
        put("focusDurationMinutes", bounded(m["focusDurationMinutes"], 25, 5, 120))
        put("breakDurationMinutes", bounded(m["breakDurationMinutes"], 5, 1, 30))
        put("dailyGoalMinutes", bounded(m["dailyGoalMinutes"], 60, 30, 240))
        put("appTheme", text(m["appTheme"], "DEFAULT"))
        put("soundEnabled", flag(m["soundEnabled"], true))
        put("vibrationEnabled", flag(m["vibrationEnabled"], true))
        put("learningAgeBand", text(m["learningAgeBand"]).takeIf(ageBands::contains) ?: "4-5")
        put("calmModeEnabled", flag(m["calmModeEnabled"]))
        put("allowChildExtendFocus", flag(m["allowChildExtendFocus"]))
        put("maxExtraFocusMinutes", number(m["maxExtraFocusMinutes"]))
        if (m["scheduleAnchors"] is Map<*, *>) put("scheduleAnchors", m["scheduleAnchors"])
        if (m["schedulePlans"] is Map<*, *>) put("schedulePlans", m["schedulePlans"])
    }

    fun migrateV1(snapshot: Map<String, Any?>, nowMillis: Long, deviceId: String): Migration {
        require(number(snapshot["schemaVersion"]) == 1L) { "not a v1 snapshot" }
        val at = nonnegative(snapshot["updatedAtMillis"], nowMillis)
        val keys = mapOf("profiles" to "childProfiles", "tasks" to "scheduledTasks",
            "routines" to "routines", "sessions" to "sessions", "completions" to "routineCompletions")
        val docs = mutableListOf<Doc>(); val routineIds = mutableSetOf<Long>()
        for (collection in collections) {
            val seen = linkedMapOf<String, Doc>()
            for (raw in snapshot[keys[collection]] as? List<*> ?: emptyList<Any>()) {
                val row = (raw as? Map<*, *>)?.entries?.associate { (key, value) -> key.toString() to value } ?: continue
                if (collection == "completions" && positiveId(row["routineId"]) !in routineIds) continue
                val doc = toDoc(collection, row, at, "migration-v1") ?: continue
                if (collection == "routines") routineIds.add(doc.data["id"] as Long)
                val prior = seen[doc.id]
                if (collection == "completions" && prior != null) {
                    val a = fromDoc(collection, prior.id, prior.data)!!
                    val b = fromDoc(collection, doc.id, doc.data)!!
                    if (pickWinner(collection, a, b) != "remote") continue
                }
                seen[doc.id] = doc
            }
            docs.addAll(seen.values)
        }
        val settings = (snapshot["settings"] as? Map<*, *>)?.entries
            ?.associate { (key, value) -> key.toString() to value }
            ?.let { parseSettings(it) + mapOf("updatedAtMillis" to at, "deviceId" to deviceId, "schemaVersion" to SCHEMA_VERSION) }
        return Migration(docs, settings)
    }
}
