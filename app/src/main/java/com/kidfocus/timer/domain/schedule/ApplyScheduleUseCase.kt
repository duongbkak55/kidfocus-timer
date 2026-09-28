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
        var tasks = current.tasks
        var anchors = current.anchors
        changes.forEach { change ->
            when (change) {
                is ScheduleChange.AddTask -> {
                    require(change.task.id > 0 && tasks.none { it.id == change.task.id })
                    require(change.task.name.isNotBlank() && change.task.name.length <= 60)
                    require(change.task.focusDurationMinutes in 5..120 && change.task.breakDurationMinutes == 0)
                    tasks = tasks + change.task
                }
                is ScheduleChange.SetAnchors -> anchors = change.anchors
                is ScheduleChange.SetBed -> anchors = anchors.copy(bed = anchors.bed + change.times)
                is ScheduleChange.TaskChange -> {
                    val task = tasks.single { it.id == change.taskId }
                    val selected = DayCodec.toCalendar(change.days)
                    require(selected.isNotEmpty() && task.daysOfWeek.containsAll(selected))
                    val changed = when (change) {
                        is ScheduleChange.MoveTask -> task.copy(hour = change.start.hour, minute = change.start.minute, daysOfWeek = selected)
                        is ScheduleChange.ResizeTask -> task.copy(focusDurationMinutes = change.durationMinutes, daysOfWeek = selected)
                    }
                    val remaining = task.daysOfWeek - selected
                    tasks = tasks.filterNot { it.id == task.id } + if (remaining.isEmpty()) listOf(changed)
                        else listOf(task.copy(daysOfWeek = remaining), changed.copy(id = ScheduleIds.newId()))
                }
            }
        }
        val updated = ScheduleState(tasks.sortedBy { it.id }, anchors)
        validate(profileId, updated)
        // W2 imports are parent-reviewed in preview. AddTask/SetAnchors may introduce
        // high findings; display warnings there and let the parent choose. W1 offline
        // suggestions still enforce the original guard. Never mix the two flows.
        val importOnly = changes.all { it is ScheduleChange.AddTask || it is ScheduleChange.SetAnchors }
        require(importOnly || changes.none { it is ScheduleChange.AddTask || it is ScheduleChange.SetAnchors })
        // Offline proposals must not add high-severity violations.
        val advisor = ScheduleAdvisor()
        fun highCounts(state: ScheduleState) = advisor.advise(state.tasks, state.anchors, ageBand)
            .filter { it.severity == Severity.HIGH }.flatMap { finding -> finding.days.map { finding.ruleId to it } }
            .groupingBy { it }.eachCount()
        val beforeHigh = highCounts(current)
        val afterHigh = highCounts(updated)
        require(importOnly || afterHigh.all { (key, count) -> count <= (beforeHigh[key] ?: 0) }) { "Suggestion introduces a high-severity conflict" }
        commit(profileId, current, updated, takeSnapshot = true)
    }

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
