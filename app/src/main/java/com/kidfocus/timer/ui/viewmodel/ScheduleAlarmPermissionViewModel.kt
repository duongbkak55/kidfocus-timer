package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.alarm.AlarmScheduler
import com.kidfocus.timer.alarm.ExactAlarmPermission
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduleAlarmPermissionViewModel @Inject constructor(
    private val permission: ExactAlarmPermission,
    private val repository: ScheduledTaskRepository,
    private val scheduler: AlarmScheduler,
    private val settings: SettingsDataStore,
) : ViewModel(), DefaultLifecycleObserver {
    private val allowed = MutableStateFlow(permission.isAllowed())
    private var lastResumedPermission: Boolean? = null
    private val _settingsUnavailable = MutableStateFlow(false)
    val settingsUnavailable = _settingsUnavailable.asStateFlow()

    val reminderVisible = combine(repository.allTasks, settings.scheduleAlarmReminderDismissed, allowed) { tasks, dismissed, granted ->
        tasks.any { it.enabled } && !dismissed && !granted
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    override fun onResume(owner: LifecycleOwner) {
        allowed.value = permission.isAllowed()
        val reschedule = allowed.value && lastResumedPermission != true
        lastResumedPermission = allowed.value
        if (reschedule) {
            _settingsUnavailable.value = false
            // Reschedule all profiles, including after Settings killed/recreated the app.
            viewModelScope.launch { scheduler.scheduleAll(repository.getEnabledTasks()) }
        }
    }

    fun requestPermission() { _settingsUnavailable.value = !permission.openSettings() }

    fun dismissReminder() {
        viewModelScope.launch { settings.dismissScheduleAlarmReminder() }
    }
}
