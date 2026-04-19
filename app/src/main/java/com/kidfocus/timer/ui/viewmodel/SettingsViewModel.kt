package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.repository.SettingsRepository
import com.kidfocus.timer.domain.model.AppTheme
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.domain.usecase.GetTimerSettingsUseCase
import com.kidfocus.timer.domain.usecase.SaveTimerSettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel responsible for reading and writing [TimerSettings].
 *
 * Also exposes PIN verification and onboarding state transitions.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val getTimerSettingsUseCase: GetTimerSettingsUseCase,
    private val saveTimerSettingsUseCase: SaveTimerSettingsUseCase,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    /** Latest [TimerSettings], null until the DataStore emits the first value. */
    val settings: StateFlow<TimerSettings?> = getTimerSettingsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    // ---- PIN state ------------------------------------------------------------------------------

    private val _pinError = MutableStateFlow(false)
    val pinError: StateFlow<Boolean> = _pinError.asStateFlow()

    private val _pinVerified = MutableStateFlow(false)
    val pinVerified: StateFlow<Boolean> = _pinVerified.asStateFlow()

    // ---- Settings mutations --------------------------------------------------------------------

    /** Updates the focus duration and persists immediately. */
    fun setFocusDuration(minutes: Int) {
        val current = settings.value ?: return
        save(current.copy(focusDurationMinutes = minutes))
    }

    /** Updates the break duration and persists immediately. */
    fun setBreakDuration(minutes: Int) {
        val current = settings.value ?: return
        save(current.copy(breakDurationMinutes = minutes))
    }

    /** Switches the active [AppTheme] and persists. */
    fun setTheme(theme: AppTheme) {
        val current = settings.value ?: return
        save(current.copy(appTheme = theme))
    }

    /** Enables or disables audio cues. */
    fun setSoundEnabled(enabled: Boolean) {
        val current = settings.value ?: return
        save(current.copy(soundEnabled = enabled))
    }

    /** Enables or disables vibration feedback. */
    fun setVibrationEnabled(enabled: Boolean) {
        val current = settings.value ?: return
        save(current.copy(vibrationEnabled = enabled))
    }

    /** Updates the daily focus goal and persists immediately. */
    fun updateDailyGoal(minutes: Int) {
        val current = settings.value ?: return
        save(current.copy(dailyGoalMinutes = minutes))
    }

    // ---- PIN management ------------------------------------------------------------------------

    /**
     * Hashes and stores [pin], enabling the parental lock.
     * Silently ignores empty strings.
     */
    fun savePin(pin: String) {
        if (pin.isBlank()) return
        viewModelScope.launch { settingsRepository.savePin(pin) }
    }

    /** Removes the stored PIN hash, disabling the parental lock. */
    fun clearPin() {
        viewModelScope.launch { settingsRepository.clearPin() }
    }

    /**
     * Verifies [pin] against the stored hash.
     * Sets [pinError] on mismatch, [pinVerified] on success.
     */
    fun verifyPin(pin: String) {
        val storedHash = settings.value?.pinHash ?: run {
            _pinVerified.update { true }
            return
        }
        val valid = settingsRepository.verifyPin(pin, storedHash)
        _pinError.update { !valid }
        _pinVerified.update { valid }
    }

    /** Clears the pin verified state (e.g. when navigating away from parent settings). */
    fun resetPinVerification() {
        _pinVerified.update { false }
        _pinError.update { false }
    }

    // ---- Onboarding ----------------------------------------------------------------------------

    /** Marks onboarding complete so the app starts on Home next launch. */
    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.completeOnboarding() }
    }

    // ---- Private helpers -----------------------------------------------------------------------

    private fun save(settings: TimerSettings) {
        viewModelScope.launch {
            runCatching { saveTimerSettingsUseCase(settings) }
        }
    }
}
