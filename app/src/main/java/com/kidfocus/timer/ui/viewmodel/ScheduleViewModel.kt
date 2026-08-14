package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.alarm.AlarmScheduler
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduledTaskRepository,
    private val alarmScheduler: AlarmScheduler,
    childProfileRepository: ChildProfileRepository,
) : ViewModel() {

    private val activeProfileId = childProfileRepository.activeProfileId.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        "default",
    )

    val tasks = combine(repository.allTasks, activeProfileId) { rows, profileId ->
        rows.filter { it.childProfileId == profileId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun saveTask(task: ScheduledTask) {
        viewModelScope.launch {
            val scopedTask = task.copy(childProfileId = task.childProfileId.takeIf { task.id != 0L }
                ?: activeProfileId.value)
            val id = if (scopedTask.id == 0L) repository.save(scopedTask) else {
                repository.update(scopedTask)
                scopedTask.id
            }
            val saved = scopedTask.copy(id = id)
            if (saved.enabled) alarmScheduler.scheduleTask(saved)
            else alarmScheduler.cancelTask(saved)
        }
    }

    fun toggleEnabled(task: ScheduledTask) {
        viewModelScope.launch {
            val updated = task.copy(enabled = !task.enabled)
            repository.update(updated)
            if (updated.enabled) alarmScheduler.scheduleTask(updated)
            else alarmScheduler.cancelTask(updated)
        }
    }

    fun deleteTask(task: ScheduledTask) {
        viewModelScope.launch {
            alarmScheduler.cancelTask(task)
            repository.delete(task)
        }
    }

    fun taskFromType(type: TaskType) = ScheduledTask(
        taskType = type,
        name = type.displayName,
        emoji = type.emoji,
        hour = type.defaultHour,
        minute = type.defaultMinute,
        daysOfWeek = type.defaultDays,
        focusDurationMinutes = type.defaultFocusMinutes,
        breakDurationMinutes = type.defaultBreakMinutes,
        isCustom = type == TaskType.CUSTOM,
        childProfileId = activeProfileId.value,
    )
}
