package com.kidfocus.timer.data.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.domain.schedule.Block
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import com.kidfocus.timer.domain.schedule.ScheduleSnapshot
import com.kidfocus.timer.domain.schedule.ScheduleState
import java.time.DayOfWeek
import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

object ScheduleJson {
    fun anchorsMap(anchors: ScheduleAnchors): Map<String, Any> = mapOf(
        "wake" to anchors.wake.mapKeys { it.key.name }.mapValues { it.value.toString() },
        "bed" to anchors.bed.mapKeys { it.key.name }.mapValues { it.value.toString() },
        "school" to anchors.school.map { mapOf("days" to it.days.map(DayOfWeek::name), "start" to it.start.toString(), "end" to it.end.toString(), "label" to it.label) },
        "commuteMinutes" to anchors.commuteMinutes,
    )

    fun anchorsFromMap(map: Map<*, *>): ScheduleAnchors {
        fun times(key: String) = (map[key] as? Map<*, *>).orEmpty().entries.associate {
            DayOfWeek.valueOf(it.key as String) to LocalTime.parse(it.value as String)
        }
        return ScheduleAnchors(times("wake"), times("bed"), (map["school"] as? List<*>).orEmpty().map {
            val block = it as Map<*, *>
            Block((block["days"] as List<*>).map { day -> DayOfWeek.valueOf(day as String) }.toSet(),
                LocalTime.parse(block["start"] as String), LocalTime.parse(block["end"] as String), block["label"] as String)
        }, (map["commuteMinutes"] as? Number)?.toInt() ?: 0).also { it.validate() }
    }

    fun encodeAnchors(anchors: ScheduleAnchors): String = JSONObject(anchorsMap(anchors)).toString()
    fun decodeAnchors(json: String): ScheduleAnchors = anchorsFromMap(JSONObject(json).asMap())

    fun encodeSnapshot(snapshot: ScheduleSnapshot): String = JSONObject().apply {
        put("savedAtMillis", snapshot.savedAtMillis)
        put("state", stateJson(snapshot.state))
        put("applied", stateJson(snapshot.applied))
    }.toString()

    fun decodeSnapshot(json: String): ScheduleSnapshot = JSONObject(json).let {
        ScheduleSnapshot(readState(it.getJSONObject("state")), readState(it.getJSONObject("applied")), it.getLong("savedAtMillis"))
    }

    private fun stateJson(state: ScheduleState) = JSONObject().apply {
        put("anchors", JSONObject(anchorsMap(state.anchors)))
        put("tasks", JSONArray(state.tasks.map { task -> JSONObject().apply {
            put("id", task.id); put("taskType", task.taskType.name); put("name", task.name); put("emoji", task.emoji)
            put("hour", task.hour); put("minute", task.minute); put("days", JSONArray(task.daysOfWeek.toList()))
            put("focus", task.focusDurationMinutes); put("break", task.breakDurationMinutes)
            put("enabled", task.enabled); put("isCustom", task.isCustom); put("profile", task.childProfileId)
            put("photo", task.photoUri ?: JSONObject.NULL)
        } }))
    }

    private fun readState(json: JSONObject): ScheduleState {
        val tasks = json.getJSONArray("tasks")
        return ScheduleState((0 until tasks.length()).map { index ->
            val task = tasks.getJSONObject(index)
            val days = task.getJSONArray("days")
            ScheduledTask(task.getLong("id"), TaskType.valueOf(task.getString("taskType")), task.getString("name"), task.getString("emoji"),
                task.getInt("hour"), task.getInt("minute"), (0 until days.length()).map { days.getInt(it) }.toSet(),
                task.getInt("focus"), task.getInt("break"), task.getBoolean("enabled"), task.getBoolean("isCustom"), task.getString("profile"),
                if (task.isNull("photo")) null else task.getString("photo"))
        }, decodeAnchors(json.getJSONObject("anchors").toString()))
    }

    private fun JSONObject.asMap(): Map<String, Any?> = keys().asSequence().associateWith { key -> unwrap(get(key)) }
    private fun unwrap(value: Any?): Any? = when (value) {
        is JSONObject -> value.asMap()
        is JSONArray -> (0 until value.length()).map { unwrap(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}
