package com.kidfocus.timer.domain.usecase

import com.kidfocus.timer.data.repository.SettingsRepository
import com.kidfocus.timer.domain.model.TimerSettings
import javax.inject.Inject

/**
 * Use case for persisting [TimerSettings] to DataStore.
 *
 * Validates all numeric bounds before delegating to the repository so
 * invalid values from the UI can never reach storage.
 */
class SaveTimerSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Validates and persists [settings].
     *
     * @throws IllegalArgumentException if focus or break duration is out of range.
     */
    suspend operator fun invoke(settings: TimerSettings) {
        require(settings.focusDurationMinutes in TimerSettings.MIN_FOCUS_MINUTES..TimerSettings.MAX_FOCUS_MINUTES) {
            "Focus duration must be between ${TimerSettings.MIN_FOCUS_MINUTES} and ${TimerSettings.MAX_FOCUS_MINUTES} minutes"
        }
        require(settings.breakDurationMinutes in TimerSettings.MIN_BREAK_MINUTES..TimerSettings.MAX_BREAK_MINUTES) {
            "Break duration must be between ${TimerSettings.MIN_BREAK_MINUTES} and ${TimerSettings.MAX_BREAK_MINUTES} minutes"
        }
        settingsRepository.saveSettings(settings)
    }
}
