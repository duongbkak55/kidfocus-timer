package com.kidfocus.timer.domain.schedule

import com.kidfocus.timer.domain.model.ScheduledTask
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ScheduleState(val tasks: List<ScheduledTask>, val anchors: ScheduleAnchors)
data class ScheduleSnapshot(val state: ScheduleState, val applied: ScheduleState, val savedAtMillis: Long)

/** Storage must commit all task mutations in one transaction, compensating anchors on failure. */
interface ScheduleStore {
    suspend fun read(profileId: String): ScheduleState
    suspend fun snapshot(profileId: String): ScheduleSnapshot?
    suspend fun saveSnapshot(profileId: String, snapshot: ScheduleSnapshot?)
    suspend fun replace(profileId: String, expected: ScheduleState, state: ScheduleState)
    fun reschedule(before: List<ScheduledTask>, after: List<ScheduledTask>)
}

class ApplyScheduleUseCase(private val store: ScheduleStore, private val now: () -> Long = System::currentTimeMillis) {
    private val mutex = Mutex()

    suspend fun apply(profileId: String, expected: ScheduleState, changes: List<ScheduleChange>, ageBand: String): Unit = mutex.withLock {
        require(changes.isNotEmpty())
        val current = store.read(profileId)
        check(current == expected) { "Schedule changed; refresh before applying" }
        val updated = prepare(profileId, current, changes, ageBand)
        commit(profileId, current, updated, takeSnapshot = true)
    }

    /** No snapshots, storage or alarms: the very same validation used at commit. */
    fun previewAdvice(profileId: String, current: ScheduleState, changes: List<ScheduleChange>, ageBand: String): ScheduleState =
        prepare(profileId, current, changes, ageBand, advice = true)

    suspend fun applyAdvice(profileId: String, expected: ScheduleState, changes: List<ScheduleChange>, ageBand: String): Unit = mutex.withLock {
        check(store.read(profileId) == expected) { "Schedule changed; refresh before applying" }
        val updated = previewAdvice(profileId, expected, changes, ageBand)
        commit(profileId, expected, updated, takeSnapshot = true)
    }

    private fun prepare(profileId: String, current: ScheduleState, changes: List<ScheduleChange>, ageBand: String, advice: Boolean = false): ScheduleState {
        require(changes.isNotEmpty())
        if (advice) require(changes.none { it is ScheduleChange.AddTask || it is ScheduleChange.SetAnchors })
        fun checkAnchorEdits(times: List<Map<java.time.DayOfWeek, java.time.LocalTime>>) {
            require(times.flatMap { it.entries }.groupBy { it.key }.values.all { rows -> rows.map { it.value }.distinct().size == 1 })
        }
        checkAnchorEdits(changes.filterIsInstance<ScheduleChange.SetBed>().map { it.times })
        checkAnchorEdits(changes.filterIsInstance<ScheduleChange.SetWake>().map { it.times })
        var tasks = current.tasks
        var anchors = current.anchors
        changes.filterNot { it is ScheduleChange.TaskChange }.forEach { change ->
            when (change) {
                is ScheduleChange.AddTask -> {
                    require(change.task.id > 0 && tasks.none { it.id == change.task.id })
                    require(change.task.name.isNotBlank() && change.task.name.length <= 60)
                    require(change.task.focusDurationMinutes in 5..120 && change.task.breakDurationMinutes == 0)
                    tasks = tasks + change.task
                }
                is ScheduleChange.SetAnchors -> anchors = change.anchors
                is ScheduleChange.SetBed -> { require(change.times.isNotEmpty()); anchors = anchors.copy(bed = anchors.bed + change.times) }
                is ScheduleChange.SetWake -> { require(change.times.isNotEmpty()); anchors = anchors.copy(wake = anchors.wake + change.times) }
                is ScheduleChange.TaskChange -> error("Unreachable")
            }
        }
        // Apply MOVE + RESIZE to the same occurrence without losing the original reference
        // after a recurring task is split. Preserve the original id on unchanged days.
        changes.filterIsInstance<ScheduleChange.TaskChange>().groupBy { it.taskId }.forEach { (id, edits) ->
            val task = tasks.single { it.id == id }
            edits.forEach { require(it.days.isNotEmpty() && DayCodec.fromCalendar(task.daysOfWeek).containsAll(it.days)) }
            if (advice && edits.any { it is ScheduleChange.RemoveTask }) {
                require(task.taskType.category != com.kidfocus.timer.domain.model.TaskCategory.STUDY) { "STUDY_PROTECTED" }
            }
            edits.forEachIndexed { index, first -> edits.drop(index + 1).filter { second -> first.days.intersect(second.days).isNotEmpty() }.forEach { second ->
                require((first !is ScheduleChange.RemoveTask && second !is ScheduleChange.RemoveTask) || (first is ScheduleChange.RemoveTask && second is ScheduleChange.RemoveTask))
                if (first is ScheduleChange.MoveTask && second is ScheduleChange.MoveTask) require(first.start == second.start)
                if (first is ScheduleChange.ResizeTask && second is ScheduleChange.ResizeTask) require(first.durationMinutes == second.durationMinutes)
            } }
            val occurrences = task.daysOfWeek.mapNotNull { day ->
                var updated: ScheduledTask? = task.copy(daysOfWeek = setOf(day))
                edits.filter { DayCodec.fromCalendar(day) in it.days }.forEach { change ->
                    when (change) {
                        is ScheduleChange.RemoveTask -> updated = null
                        is ScheduleChange.MoveTask -> updated = checkNotNull(updated).copy(hour = change.start.hour, minute = change.start.minute)
                        is ScheduleChange.ResizeTask -> updated = checkNotNull(updated).copy(focusDurationMinutes = change.durationMinutes)
                    }
                }
                updated
            }
            val groups = occurrences.groupBy { it.copy(daysOfWeek = emptySet()) }.map { (value, rows) -> value.copy(daysOfWeek = rows.flatMap { it.daysOfWeek }.toSet()) }
                .sortedByDescending { it.hour == task.hour && it.minute == task.minute && it.focusDurationMinutes == task.focusDurationMinutes }
            tasks = tasks.filterNot { it.id == id } + groups.mapIndexed { index, row -> if (index == 0) row else row.copy(id = ScheduleIds.newId()) }
        }
        val updated = ScheduleState(tasks.sortedBy { it.id }, anchors)
        validate(profileId, updated)
        val importOnly = changes.all { it is ScheduleChange.AddTask || it is ScheduleChange.SetAnchors }
        require(importOnly || changes.none { it is ScheduleChange.AddTask || it is ScheduleChange.SetAnchors })
        val advisor = ScheduleAdvisor()
        fun highCounts(state: ScheduleState) = advisor.advise(state.tasks, state.anchors, ageBand)
            .filter { it.severity == Severity.HIGH }.flatMap { finding -> finding.days.map { finding.ruleId to it } }
            .groupingBy { it }.eachCount()
        val beforeHigh = highCounts(current)
        if (advice) {
            require(updated.anchors.school == current.anchors.school) { "SCHOOL_PROTECTED" }
            changes.forEach { change ->
                when (change) {
                    is ScheduleChange.MoveTask, is ScheduleChange.ResizeTask -> {
                        val edit = change as ScheduleChange.TaskChange
                        val original = current.tasks.single { it.id == edit.taskId }
                        edit.days.forEach { day ->
                            val time = (change as? ScheduleChange.MoveTask)?.start ?: java.time.LocalTime.of(original.hour, original.minute)
                            val duration = (change as? ScheduleChange.ResizeTask)?.durationMinutes ?: original.focusDurationMinutes
                            require(!touchesSchool(current.anchors, day, time.minutes(), duration + original.breakDurationMinutes)) { "SCHOOL_PROTECTED" }
                        }
                    }
                    is ScheduleChange.SetBed -> change.times.forEach { (day, _) ->
                        require(current.anchors.bed[day] != null)
                        require(!touchesSchool(current.anchors, day, checkNotNull(updated.anchors.bedMinute(day)), 1)) { "SCHOOL_PROTECTED" }
                    }
                    is ScheduleChange.SetWake -> change.times.forEach { (day, time) ->
                        require(current.anchors.wake[day] != null)
                        require(!touchesSchool(current.anchors, day, time.minutes(), 1)) { "SCHOOL_PROTECTED" }
                    }
                    else -> Unit
                }
            }
        }
        require(importOnly || highCounts(updated).all { (key, count) -> count <= (beforeHigh[key] ?: 0) }) { "HIGH_INCREASED" }
        return updated
    }

    private fun touchesSchool(anchors: ScheduleAnchors, day: java.time.DayOfWeek, startMinute: Int, duration: Int): Boolean =
        intervalsForDay(emptyList(), anchors, day).any { it.school != null && startMinute < it.end && it.start < startMinute + duration }

    suspend fun saveAnchors(profileId: String, expected: ScheduleState, anchors: ScheduleAnchors): Unit = mutex.withLock {
        check(store.read(profileId) == expected)
        val updated = expected.copy(anchors = anchors)
        validate(profileId, updated)
        commit(profileId, expected, updated, takeSnapshot = true)
    }

    suspend fun undo(profileId: String): Unit = mutex.withLock {
        val snapshot = checkNotNull(store.snapshot(profileId)) { "No previous schedule" }
        require(now() - snapshot.savedAtMillis in 0..7 * 24 * 60 * 60 * 1000L) { "Snapshot expired" }
        val current = store.read(profileId)
        check(current == snapshot.applied) { "Schedule changed since apply; cannot overwrite newer edits" }
        validate(profileId, snapshot.state)
        commit(profileId, current, snapshot.state, takeSnapshot = false)
        store.saveSnapshot(profileId, null)
    }

    private suspend fun commit(profileId: String, before: ScheduleState, after: ScheduleState, takeSnapshot: Boolean) {
        val previous = store.snapshot(profileId)
        if (takeSnapshot) store.saveSnapshot(profileId, ScheduleSnapshot(before, after, now()))
        try {
            store.replace(profileId, before, after)
        } catch (error: Exception) {
            if (takeSnapshot) kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { store.saveSnapshot(profileId, previous) }
            throw error
        }
        store.reschedule(before.tasks, after.tasks)
    }

    private fun validate(profileId: String, state: ScheduleState) {
        state.anchors.validate()
        require(state.tasks.map { it.id }.distinct().size == state.tasks.size)
        state.tasks.forEach {
            require(it.id > 0 && it.childProfileId == profileId && it.hour in 0..23 && it.minute in 0..59)
            require(it.focusDurationMinutes in 1..120 && it.breakDurationMinutes in 0..30)
            require(it.daysOfWeek.isNotEmpty())
            DayCodec.fromCalendar(it.daysOfWeek)
        }
    }
}
