package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.data.repository.RoutineRepository
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.domain.model.RoutineTime
import com.kidfocus.timer.domain.model.RoutineInsights
import com.kidfocus.timer.domain.model.RoutineWeeklySummary
import com.kidfocus.timer.domain.model.TodayRoutine
import com.kidfocus.timer.service.RoutineAlarmScheduler
import com.kidfocus.timer.service.RoutineNotificationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class RoutineViewModel @Inject constructor(
    private val repository: RoutineRepository,
    private val scheduler: RoutineAlarmScheduler,
    private val notifications: RoutineNotificationManager,
    childProfileRepository: ChildProfileRepository,
) : ViewModel() {
    private val activeProfileId = childProfileRepository.activeProfileId.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        "default",
    )
    private val nowMillis = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(30_000L)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    private val currentDate = nowMillis
        .map { RoutineTime.dateAt(it) }
        .distinctUntilChanged()

    val allRoutines: StateFlow<List<RoutineEntity>> = combine(
        repository.observeAll(),
        activeProfileId,
    ) { rows, profileId -> rows.filter { it.childProfileId == profileId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todayRoutines: StateFlow<List<TodayRoutine>> = currentDate.flatMapLatest { date ->
        combine(
            repository.observeForDate(date),
            repository.observeCompletions(date),
            nowMillis,
            activeProfileId,
        ) { routines, completions, now, profileId ->
            val completionsByRoutine = completions.associateBy { it.routineId }
            routines.filter { it.childProfileId == profileId }.map { routine ->
                RoutineTime.toTodayRoutine(
                    routine = routine,
                    date = date,
                    completion = completionsByRoutine[routine.id],
                    nowMillis = now,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val weeklySummary: StateFlow<RoutineWeeklySummary> = currentDate.flatMapLatest { date ->
        activeProfileId.flatMapLatest { profileId ->
            combine(
                repository.observeCompletionsSince(profileId, date.minusWeeks(8)),
                repository.observeCompletionCount(profileId),
            ) { completions, totalCount ->
                RoutineInsights.weeklySummary(completions, date, totalCount)
            }
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        RoutineWeeklySummary(
            weekStart = LocalDate.now(),
            completedCount = 0,
            onTimeCount = 0,
            lateCount = 0,
            currentStreakDays = 0,
            totalCompletedCount = 0,
        ),
    )

    fun save(routine: RoutineEntity, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val saved = repository.save(
                routine.copy(
                    childProfileId = routine.childProfileId.takeIf { routine.id != 0L }
                        ?: activeProfileId.value,
                )
            )
            scheduler.schedule(saved)
            onSaved()
        }
    }

    fun addPreset(routines: List<RoutineEntity>, onSaved: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val saved = repository.savePreset(
                routines.map { it.copy(childProfileId = activeProfileId.value) }
            )
            saved.forEach(scheduler::schedule)
            onSaved(saved.size)
        }
    }

    fun setEnabled(routine: RoutineEntity, enabled: Boolean) {
        save(routine.copy(enabled = enabled))
    }

    fun delete(routine: RoutineEntity) {
        viewModelScope.launch {
            scheduler.cancel(routine.id)
            notifications.cancel(routine.id)
            repository.delete(routine)
        }
    }

    fun complete(todayRoutine: TodayRoutine) {
        viewModelScope.launch {
            repository.complete(todayRoutine.routine, todayRoutine.occurrenceDate)
            notifications.cancel(todayRoutine.routine.id)
            scheduleAfterToday(todayRoutine.routine)
        }
    }

    fun rescheduleAll() {
        viewModelScope.launch {
            repository.getEnabled().forEach(scheduler::schedule)
        }
    }

    private fun scheduleAfterToday(routine: RoutineEntity) {
        val tomorrow = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduler.schedule(routine, tomorrow)
    }
}
