package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import kotlin.math.abs

enum class NoteTag { DAYTIME_SLEEPY, HARD_TO_WAKE, TANTRUM_EVENING, LONG_HOMEWORK, LITTLE_PLAY }
data class RoutineStat(val name: String, val lateOrMissedLast7: Int)
data class ScheduleAdvice(val summary: String, val proposals: List<Map<String, Any?>>, val tags: Set<NoteTag>)
enum class AdviceRejection { INVALID, HIGH_INCREASED, SCHOOL_PROTECTED, STUDY_PROTECTED }
data class AdviceRow(
    val index: Int,
    val reason: String,
    val changes: List<ScheduleChange> = emptyList(),
    val rejection: AdviceRejection? = null,
    val fixed: Set<RuleId> = emptySet(),
    val requiresPlan: Boolean = false,
) {
    val removes: Boolean get() = changes.any { it is ScheduleChange.RemoveTask }
}

typealias AdviceRefs = Map<String, Map<DayOfWeek, Long>>

object ScheduleAdviceReview {
    fun refs(state: ScheduleState): AdviceRefs = state.tasks.filter { it.enabled }.take(60).mapIndexed { index, task ->
        "t$index" to DayCodec.fromCalendar(task.daysOfWeek).associateWith { task.id }
    }.toMap()

    fun review(advice: ScheduleAdvice, refs: AdviceRefs, profileId: String, state: ScheduleState, ageBand: String, useCase: ApplyScheduleUseCase): List<AdviceRow> {
        require(advice.summary.length <= 600 && advice.proposals.size <= 10)
        val advisor = ScheduleAdvisor()
        val before = advisor.advise(state.tasks, state.anchors, ageBand)
        return advice.proposals.mapIndexed { index, raw ->
            val reason = (raw["reason"] as? String).orEmpty().take(200)
            try {
                val changes = changes(raw, refs)
                val afterState = useCase.previewAdvice(profileId, state, changes, ageBand)
                val after = advisor.advise(afterState.tasks, afterState.anchors, ageBand)
                val fixed = before.filter { old ->
                    val related = after.filter { it.ruleId == old.ruleId && it.days == old.days }
                    related.size < before.count { it.ruleId == old.ruleId && it.days == old.days } ||
                        (old.ruleId == RuleId.SLEEP_SHORT && related.any { (it.params["actual"] ?: 0) > (old.params["actual"] ?: 0) })
                }.map { it.ruleId }.toSet()
                val far = changes.any { change ->
                    val times = when (change) { is ScheduleChange.SetBed -> state.anchors.bed to change.times; is ScheduleChange.SetWake -> state.anchors.wake to change.times; else -> null }
                    times?.let { (current, targets) -> targets.any { (day, target) -> abs(timeDifference(checkNotNull(current[day]), target)) > SchedulePlan.DIRECT_LIMIT_MINUTES } } ?: false
                }
                AdviceRow(index, reason, changes, fixed = fixed, requiresPlan = far)
            } catch (error: Exception) {
                val rejection = AdviceRejection.entries.firstOrNull { it.name == error.message } ?: AdviceRejection.INVALID
                AdviceRow(index, reason, rejection = rejection)
            }
        }
    }

    fun changes(raw: Map<String, Any?>, refs: AdviceRefs): List<ScheduleChange> {
        val op = raw["op"] as String
        val taskOp = op in setOf("MOVE", "RESIZE", "REMOVE")
        val required = setOf("op", "days", "reason", "fixes") + (if (taskOp) setOf("taskRef") else emptySet()) +
            (if (op in setOf("MOVE", "SET_BED", "SET_WAKE")) setOf("start") else emptySet()) + (if (op == "RESIZE") setOf("durationMin") else emptySet())
        require(raw.keys == required && (raw["reason"] as String).length in 1..200)
        val dayNames = raw["days"] as List<*>
        val days = dayNames.map { value -> DayOfWeek.entries.single { it.name.take(3) == value } }.toSet()
        require(days.isNotEmpty() && days.size == dayNames.size)
        val fixes = raw["fixes"] as List<*>
        require(fixes.size <= 8 && fixes.distinct().size == fixes.size && fixes.all { it in RuleId.entries.map(RuleId::name) })
        fun start(): LocalTime {
            val value = raw["start"] as String
            require(Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$").matches(value))
            return LocalTime.parse(value)
        }
        if (op == "SET_BED") return listOf(ScheduleChange.SetBed(days.associateWith { start() }))
        if (op == "SET_WAKE") return listOf(ScheduleChange.SetWake(days.associateWith { start() }))
        require(taskOp)
        val taskDays = checkNotNull(refs[raw["taskRef"]])
        return days.groupBy { checkNotNull(taskDays[it]) }.map { (id, selected) -> when (op) {
            "MOVE" -> ScheduleChange.MoveTask(id, selected.toSet(), start())
            "RESIZE" -> {
                val duration = raw["durationMin"] as Number
                require(duration.toDouble() == duration.toInt().toDouble() && duration.toInt() in 1..120)
                ScheduleChange.ResizeTask(id, selected.toSet(), duration.toInt())
            }
            else -> ScheduleChange.RemoveTask(id, selected.toSet())
        } }
    }

    /** Keep references for remaining proposals after individual applies split repeating tasks. */
    fun rebase(refs: AdviceRefs, before: ScheduleState, after: ScheduleState, changes: List<ScheduleChange>): AdviceRefs = refs.mapValues { (_, days) ->
        days.mapNotNull { (day, id) ->
            var expected: ScheduledTask? = before.tasks.find { it.id == id }
            changes.filterIsInstance<ScheduleChange.TaskChange>().filter { it.taskId == id && day in it.days }.forEach { change ->
                expected = when (change) {
                    is ScheduleChange.RemoveTask -> null
                    is ScheduleChange.MoveTask -> expected?.copy(hour = change.start.hour, minute = change.start.minute)
                    is ScheduleChange.ResizeTask -> expected?.copy(focusDurationMinutes = change.durationMinutes)
                }
            }
            val template = expected ?: return@mapNotNull null
            val match = after.tasks.singleOrNull { candidate ->
                DayCodec.toCalendar(day) in candidate.daysOfWeek && candidate.copy(id = template.id, daysOfWeek = template.daysOfWeek) == template &&
                    (candidate.id == id || before.tasks.none { it.id == candidate.id })
            } ?: return@mapNotNull null
            day to match.id
        }.toMap()
    }
}

object ScheduleAdvicePayload {
    fun build(state: ScheduleState, refs: AdviceRefs, findings: List<Finding>, routines: List<RoutineStat>, ageBand: String, today: String, locale: String, note: String, tags: Set<NoteTag>, requestId: String): Map<String, Any> {
        require(note.length <= 500)
        val taskRefs = refs.flatMap { (ref, days) -> days.values.map { it to ref } }.toMap()
        fun days(value: Set<DayOfWeek>) = value.sortedBy { it.value }.map { it.name.take(3) }
        return mapOf("mode" to "ADVISE", "requestId" to requestId, "ageBand" to (ageBand.takeIf { it in ScheduleThresholds.sleepMinutes } ?: "4-5"), "today" to today, "locale" to locale,
            "tasks" to refs.map { (ref, ids) ->
                val task = state.tasks.single { it.id == ids.values.first() }
                mapOf("ref" to ref, "name" to task.name.take(60), "taskType" to task.taskType.name, "days" to days(ids.keys),
                    "start" to "%02d:%02d".format(Locale.ROOT, task.hour, task.minute), "durationMin" to task.focusDurationMinutes + task.breakDurationMinutes)
            },
            "anchors" to mapOf("wake" to state.anchors.wake.mapKeys { it.key.name.take(3) }.mapValues { it.value.toString() },
                "bed" to state.anchors.bed.mapKeys { it.key.name.take(3) }.mapValues { it.value.toString() },
                "school" to state.anchors.school.take(30).map { mapOf("days" to days(it.days), "start" to it.start.toString(), "end" to it.end.toString()) }),
            "findings" to findings.take(1000).map { mapOf("ruleId" to it.ruleId.name, "severity" to it.severity.name, "days" to days(it.days),
                "taskRefs" to it.taskIds.mapNotNull(taskRefs::get).distinct(), "params" to it.params) },
            "routineStats" to routines.take(60).map { mapOf("name" to it.name.take(60), "lateOrMissedLast7" to it.lateOrMissedLast7) }, "note" to note, "noteTags" to tags.map { it.name })
    }
    fun advice(raw: Map<*, *>): ScheduleAdvice {
        require(raw.keys == setOf("summary", "proposals", "tags"))
        val summary = raw["summary"] as String
        val proposals = raw["proposals"] as List<*>
        require(summary.length <= 600 && proposals.size <= 10)
        val tags = raw["tags"] as List<*>
        require(tags.size <= NoteTag.entries.size && tags.distinct().size == tags.size)
        return ScheduleAdvice(summary, proposals.map { (it as? Map<*, *>)?.entries?.associate { row -> row.key.toString() to row.value } ?: emptyMap() },
            tags.map { NoteTag.valueOf(it as String) }.toSet())
    }
}
