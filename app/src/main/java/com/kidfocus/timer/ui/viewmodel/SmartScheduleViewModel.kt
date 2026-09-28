package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.data.repository.RoutineRepository
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.data.schedule.RoomScheduleStore
import com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository
import com.kidfocus.timer.domain.model.RoutineTime
import com.kidfocus.timer.domain.schedule.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class SmartScheduleUiState(val profile: ChildProfileEntity, val schedule: ScheduleState, val findings: List<Finding>, val canUndo: Boolean)
enum class ScheduleEvent { SAVED, RESTORED, ERROR }

@HiltViewModel
class SmartScheduleViewModel @Inject constructor(
    profiles: ChildProfileRepository,
    tasks: ScheduledTaskRepository,
    routines: RoutineRepository,
    anchors: ScheduleAnchorsRepository,
    private val applySchedule: ApplyScheduleUseCase,
    private val store: RoomScheduleStore,
) : ViewModel() {
    val busy = MutableStateFlow(false)
    private val _events = MutableSharedFlow<ScheduleEvent>()
    val events = _events.asSharedFlow()
    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }
    val state = profiles.activeProfile.flatMapLatest { profile ->
        combine(tasks.allTasks, anchors.observe(profile.id), routines.observeAll(),
            routines.observeCompletionsSince(profile.id, LocalDate.now().minusDays(ScheduleThresholds.HISTORY_DAYS - 1)), clock) { rows, hours, routineRows, completions, now ->
            val scoped = rows.filter { it.childProfileId == profile.id }.sortedBy { it.id }
            val patterns = routineRows.filter { it.childProfileId == profile.id && it.enabled }.map {
                RoutinePattern(it.id, DayCodec.fromMask(it.repeatDaysMask), it.deadlineMinutes, RoutineTime.dateAt(it.createdAtMillis))
            }
            val observations = completions.mapNotNull {
                runCatching { RoutineObservation(it.routineId, LocalDate.parse(it.occurrenceDate), it.status) }.getOrNull()
            }
            val schedule = ScheduleState(scoped, hours)
            val snapshot = store.snapshot(profile.id)
            SmartScheduleUiState(profile, schedule, ScheduleAdvisor().advise(scoped, hours, profile.ageBand, patterns, observations, now.toLocalDate(), now.toLocalTime().minutes()),
                snapshot != null && snapshot.applied == schedule && System.currentTimeMillis() - snapshot.savedAtMillis in 0..604_800_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun apply(finding: Finding) {
        val current = state.value ?: return
        val change = finding.suggestion ?: return
        runAction(ScheduleEvent.SAVED) { applySchedule.apply(current.profile.id, current.schedule, listOf(change), current.profile.ageBand) }
    }
    fun saveAnchors(anchors: ScheduleAnchors) {
        val current = state.value ?: return
        runAction(ScheduleEvent.SAVED) { applySchedule.saveAnchors(current.profile.id, current.schedule, anchors) }
    }
    fun undo() {
        val profileId = state.value?.profile?.id ?: return
        runAction(ScheduleEvent.RESTORED) { applySchedule.undo(profileId) }
    }
    private fun runAction(event: ScheduleEvent, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        viewModelScope.launch {
            try {
                action()
                _events.emit(event)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _events.emit(ScheduleEvent.ERROR)
            } finally {
                busy.value = false
            }
        }
    }
}
