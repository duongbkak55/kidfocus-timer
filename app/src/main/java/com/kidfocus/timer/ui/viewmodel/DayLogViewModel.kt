package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.repository.*
import com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository
import com.kidfocus.timer.data.daylog.DayLogSyncManager
import com.kidfocus.timer.domain.daylog.*
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class DayLogData(val profileId: String? = null, val tasks: List<ScheduledTask> = emptyList(),
    val anchors: ScheduleAnchors = ScheduleAnchors(), val entries: List<DayLogEntry> = emptyList())
enum class DayLogError { INVALID, NO_PROFILE, PROFILE_CHANGED, STORAGE }

@HiltViewModel
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DayLogViewModel @Inject constructor(private val logs: DayLogRepository, profiles: ChildProfileRepository,
    tasks: ScheduledTaskRepository, anchors: ScheduleAnchorsRepository, private val sync: DayLogSyncManager) : ViewModel() {
    val data: StateFlow<DayLogData> = combine(profiles.profiles, profiles.activeProfileId) { rows, id ->
        rows.firstOrNull { it.id == id }?.id
    }.flatMapLatest { id ->
        if (id == null) flowOf(DayLogData()) else combine(tasks.allTasks, anchors.all, logs.observe(id)) { allTasks, allAnchors, entries ->
            DayLogData(id, allTasks.filter { it.childProfileId == id }, allAnchors[id] ?: ScheduleAnchors(), entries)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DayLogData())
    private val _date = MutableStateFlow(LocalDate.now())
    val date = _date.asStateFlow()
    private val _week = MutableStateFlow(monday(LocalDate.now()))
    val week = _week.asStateFlow()
    private val _draft = MutableStateFlow<DayLogEntry?>(null)
    val draft = _draft.asStateFlow()
    private var editingExisting = false
    private val _error = MutableStateFlow<DayLogError?>(null)
    val error = _error.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    val syncError = sync.error

    init { viewModelScope.launch { data.map { it.profileId }.distinctUntilChanged().drop(1).collect { cancelEdit() } } }
    fun selectDate(date: LocalDate) { _date.value = date; sync.loadHistory(date, date) }
    fun moveWeek(offset: Long) {
        _week.value = _week.value.plusWeeks(offset)
        sync.loadHistory(_week.value, _week.value.plusDays(6))
    }
    fun thisWeek() { _week.value = monday(LocalDate.now()) }
    fun openPlan(plan: DayPlanItem) {
        val profile = data.value.profileId ?: run { _error.value = DayLogError.NO_PROFILE; return }
        val now = System.currentTimeMillis()
        editingExisting = false; _error.value = null
        _draft.value = DayLogEntry(profileId = profile, date = plan.date, taskId = plan.taskId, name = plan.name,
            category = plan.category, startMinute = plan.startMinute,
            endMinute = plan.durationMinutes?.let { (plan.startMinute + it) % 1440 }, source = DayLogSource.MANUAL, createdAt = now)
    }
    fun openActual(entry: DayLogEntry) { editingExisting = true; _error.value = null; _draft.value = entry }
    fun openNew(category: DayLogCategory, name: String) {
        val profile = data.value.profileId ?: run { _error.value = DayLogError.NO_PROFILE; return }
        val now = LocalTime.now(); val minute = now.hour * 60 + now.minute
        editingExisting = false; _error.value = null
        _draft.value = DayLogEntry(profileId = profile, date = date.value, name = name, category = category,
            startMinute = minute, endMinute = minute.takeIf { category == DayLogCategory.WAKE },
            source = DayLogSource.MANUAL, createdAt = System.currentTimeMillis())
    }
    fun cancelEdit() { _draft.value = null; _error.value = null }
    fun clearError() { _error.value = null }
    fun save(name: String, start: String, end: String, category: DayLogCategory) {
        if (_saving.value) return
        val old = draft.value ?: return
        val entry = runCatching {
            fun minute(text: String): Int { require(text.matches(Regex("[0-9]{2}:[0-9]{2}"))); return LocalTime.parse(text).let { it.hour * 60 + it.minute } }
            old.copy(name = name.trim(), startMinute = minute(start), endMinute = end.trim().takeIf { it.isNotEmpty() }?.let(::minute),
                category = category, source = DayLogSource.MANUAL, updatedAt = maxOf(System.currentTimeMillis(), old.updatedAt + 1)).also { it.validate() }
        }.getOrElse { _error.value = DayLogError.INVALID; return }
        _saving.value = true
        viewModelScope.launch {
            try {
                if (logs.activeProfileId() != entry.profileId) { _error.value = DayLogError.PROFILE_CHANGED; return@launch }
                if (editingExisting) logs.edit(entry) else logs.add(entry)
                cancelEdit()
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; _error.value = DayLogError.STORAGE }
            finally { _saving.value = false }
        }
    }
    fun deleteDraft() {
        if (_saving.value || !editingExisting) return
        val entry = draft.value ?: return
        _saving.value = true
        viewModelScope.launch {
            try {
                if (logs.activeProfileId() != entry.profileId) { _error.value = DayLogError.PROFILE_CHANGED; return@launch }
                logs.delete(entry.id, entry.profileId); cancelEdit()
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; _error.value = DayLogError.STORAGE }
            finally { _saving.value = false }
        }
    }
    fun isExisting() = editingExisting
    companion object { fun monday(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) }
}
