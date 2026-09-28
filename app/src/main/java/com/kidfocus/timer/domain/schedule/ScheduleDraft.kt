package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import java.time.DayOfWeek
import java.time.LocalTime

enum class DraftKind { TASK, WAKE, BED, SCHOOL }

data class DraftItem(
    val key: String,
    val kind: DraftKind,
    val days: Set<DayOfWeek>,
    val start: LocalTime,
    val name: String = "",
    val taskType: TaskType = TaskType.CUSTOM,
    val emoji: String = "",
    val durationMin: Int = 30,
    val end: LocalTime? = null,
    val confidence: Double = 1.0,
    val source: String = "",
    val selected: Boolean = confidence >= 0.6,
    val taskId: Long = 0,
) {
    fun validate() {
        require(days.isNotEmpty() && confidence.isFinite() && confidence in 0.0..1.0)
        if (kind == DraftKind.TASK) require(taskId > 0 && name.isNotBlank() && name.length <= 60 && durationMin in 5..120)
        if (kind == DraftKind.SCHOOL) require(name.isNotBlank() && name.length <= 60 && end != null && end != start)
    }
    fun toTask(profileId: String): ScheduledTask {
        require(kind == DraftKind.TASK)
        validate()
        return ScheduledTask(id = taskId, taskType = taskType, name = name, emoji = emoji,
            hour = start.hour, minute = start.minute, daysOfWeek = DayCodec.toCalendar(days),
            focusDurationMinutes = durationMin, breakDurationMinutes = 0,
            childProfileId = profileId, isCustom = taskType == TaskType.CUSTOM)
    }
}

data class ScheduleDraft(val items: List<DraftItem>, val questions: List<String>) {
    fun changes(profileId: String, before: ScheduleState): List<ScheduleChange> {
        // Two edited hour rows must never silently overwrite each other.
        listOf(DraftKind.WAKE, DraftKind.BED).forEach { kind ->
            val times = items.filter { it.selected && it.kind == kind }.flatMap { item -> item.days.map { it to item.start } }.groupBy { it.first }
            require(times.values.all { values -> values.map { it.second }.distinct().size == 1 }) { "Conflicting anchor times" }
        }
        var anchors = before.anchors
        val additions = mutableListOf<ScheduleChange>()
        items.filter { it.selected }.forEach { item ->
            item.validate()
            when (item.kind) {
                DraftKind.TASK -> additions += ScheduleChange.AddTask(item.toTask(profileId))
                DraftKind.WAKE -> anchors = anchors.copy(wake = anchors.wake + item.days.associateWith { item.start })
                DraftKind.BED -> anchors = anchors.copy(bed = anchors.bed + item.days.associateWith { item.start })
                DraftKind.SCHOOL -> anchors = anchors.copy(school = (anchors.school + Block(item.days, item.start, checkNotNull(item.end), item.name)).distinct())
            }
        }
        if (anchors != before.anchors) additions += ScheduleChange.SetAnchors(anchors)
        return additions
    }
    fun merge(profileId: String, before: ScheduleState): ScheduleState {
        var merged = before
        changes(profileId, before).forEach { change ->
            merged = when (change) {
                is ScheduleChange.AddTask -> merged.copy(tasks = merged.tasks + change.task)
                is ScheduleChange.SetAnchors -> merged.copy(anchors = change.anchors)
                else -> error("Unexpected import change")
            }
        }
        return merged.copy(tasks = merged.tasks.sortedBy { it.id })
    }
    fun warnings(profileId: String, before: ScheduleState, ageBand: String): Map<String, Set<RuleId>> {
        fun counts(state: ScheduleState) = ScheduleAdvisor().advise(state.tasks, state.anchors, ageBand)
            .filter { it.ruleId == RuleId.OVERLAP || it.ruleId == RuleId.SLEEP_SHORT }
            .map { it.copy(suggestion = null) }.groupingBy { it }.eachCount()
        val baseline = counts(before)
        val merged = merge(profileId, before)
        val introduced = counts(merged).filter { (finding, count) -> count > (baseline[finding] ?: 0) }.keys
        // Run the advisor twice, rather than once per row (up to 74 rows). This keeps
        // a 30-task import into a full existing schedule usable on modest phones.
        val intervals = DayOfWeek.entries.associateWith { intervalsForDay(merged.tasks, merged.anchors, it) }
        return items.filter { it.selected }.associate { item ->
            val school = if (item.kind == DraftKind.SCHOOL) Block(item.days, item.start, checkNotNull(item.end), item.name) else null
            item.key to introduced.filter { finding ->
                when (item.kind) {
                    DraftKind.TASK -> item.taskId in finding.taskIds
                    DraftKind.BED -> finding.ruleId == RuleId.SLEEP_SHORT && finding.days.any { it in item.days && before.anchors.bed[it] != item.start }
                    DraftKind.WAKE -> finding.ruleId == RuleId.SLEEP_SHORT && finding.days.any { it.plus(1) in item.days && before.anchors.wake[it.plus(1)] != item.start }
                    DraftKind.SCHOOL -> school !in before.anchors.school && finding.ruleId == RuleId.OVERLAP && finding.taskIds.size == 1 && finding.days.any { day ->
                        val rows = intervals.getValue(day)
                        rows.filter { it.school == school }.any { block -> rows.any { task ->
                            task.taskId in finding.taskIds && block.start < task.end && task.start < block.end &&
                                maxOf(block.start, task.start) < 1440 && minOf(block.end, task.end) > 0
                        } }
                    }
                }
            }.map { it.ruleId }.toSet()
        }
    }

}

/** Validate the callable response again on Android; never turn incomplete fields into defaults. */
object ScheduleDraftMapper {
    private val dayNames = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    fun fromMap(raw: Map<String, Any?>, newId: () -> Long = ScheduleIds::newId): ScheduleDraft {
        fun map(value: Any?): Map<*, *> = value as? Map<*, *> ?: error("AI_PARSE_FAILED")
        fun list(value: Any?): List<*> = value as? List<*> ?: error("AI_PARSE_FAILED")
        fun text(value: Any?, min: Int, max: Int): String {
            val string = value as? String ?: error("AI_PARSE_FAILED")
            require(string.trim().length >= min && string.length <= max)
            return string.trim()
        }
        fun time(value: Any?): LocalTime {
            val string = value as? String ?: error("AI_PARSE_FAILED")
            require(Regex("([01]\\d|2[0-3]):[0-5]\\d").matches(string))
            return LocalTime.parse(string)
        }
        fun days(value: Any?): Set<DayOfWeek> {
            val names = list(value)
            require(names.size in 1..7 && names.distinct().size == names.size)
            return names.map { name -> DayOfWeek.of(dayNames.indexOf(name).also { require(it >= 0) } + 1) }.toSet()
        }
        val anchors = map(raw["anchors"])
        val items = buildList {
            listOf("wake" to DraftKind.WAKE, "bed" to DraftKind.BED).forEach { (field, kind) ->
                map(anchors[field]).forEach { (day, value) ->
                    add(DraftItem("$field:$day", kind, days(listOf(day)), time(value)))
                }
            }
            val schools = list(anchors["school"])
            require(schools.size <= 30)
            schools.forEachIndexed { index, rawBlock ->
                val block = map(rawBlock)
                add(DraftItem("school:$index", DraftKind.SCHOOL, days(block["days"]), time(block["start"]),
                    name = text(block["label"], 1, 60), end = time(block["end"])))
            }
            val tasks = list(raw["tasks"])
            require(tasks.size <= 30)
            tasks.forEachIndexed { index, rawTask ->
                val task = map(rawTask)
                val duration = task["durationMin"] as? Number ?: error("AI_PARSE_FAILED")
                require(duration.toDouble() == duration.toInt().toDouble())
                val confidence = (task["confidence"] as? Number)?.toDouble() ?: error("AI_PARSE_FAILED")
                val type = text(task["taskType"], 1, 60)
                add(DraftItem("task:$index", DraftKind.TASK, days(task["days"]), time(task["start"]),
                    name = text(task["name"], 1, 60), taskType = TaskType.entries.find { it.name == type } ?: TaskType.CUSTOM,
                    emoji = text(task["emoji"], 0, 16), durationMin = duration.toInt(), confidence = confidence,
                    source = text(task["source"], 0, 2000), taskId = newId()))
            }
        }
        items.forEach { it.validate() }
        val questions = list(raw["questions"])
        require(questions.size <= 30)
        return ScheduleDraft(items, questions.map { text(it, 1, 300) })
    }
}
