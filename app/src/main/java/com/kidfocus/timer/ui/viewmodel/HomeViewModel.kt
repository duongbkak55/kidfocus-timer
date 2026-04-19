package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.domain.usecase.GetTimerSettingsUseCase
import com.kidfocus.timer.domain.usecase.GetTodaySessionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the Home screen.
 *
 * Exposes aggregated session statistics for today's usage summary.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    getTodaySessionsUseCase: GetTodaySessionsUseCase,
    getTimerSettingsUseCase: GetTimerSettingsUseCase,
) : ViewModel() {

    private val todaySessions: StateFlow<List<SessionEntity>> = getTodaySessionsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /**
     * Total focused minutes across all completed focus sessions today.
     */
    val todayFocusMinutes: StateFlow<Int> = todaySessions.map { sessions ->
        sessions
            .filter { it.isFocus }
            .sumOf { it.durationMinutes }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    /**
     * Number of completed focus sessions today.
     */
    val todayFocusCount: StateFlow<Int> = todaySessions.map { sessions ->
        sessions.count { it.isFocus }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    /**
     * Number of completed break sessions today.
     */
    val todayBreakCount: StateFlow<Int> = todaySessions.map { sessions ->
        sessions.count { !it.isFocus }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    private val timerSettings: StateFlow<TimerSettings> = getTimerSettingsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TimerSettings(),
        )

    /**
     * Daily goal progress as a fraction 0.0–1.0, using the configurable daily goal from settings.
     */
    val dailyGoalProgress: StateFlow<Float> = combine(todayFocusMinutes, timerSettings) { minutes, settings ->
        (minutes / settings.dailyGoalMinutes.toFloat()).coerceIn(0f, 1f)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0f,
    )
}
