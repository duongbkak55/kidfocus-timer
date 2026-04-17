package com.kidfocus.timer.domain.usecase

import com.kidfocus.timer.data.repository.SettingsRepository
import com.kidfocus.timer.domain.model.TimerSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case that emits the current [TimerSettings] and any subsequent updates.
 *
 * Wraps [SettingsRepository] so the UI layer has no direct dependency on the data layer.
 */
class GetTimerSettingsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Returns a cold [Flow] that emits [TimerSettings] once for each stored update.
     * Always emits at least once with either the saved value or the default.
     */
    operator fun invoke(): Flow<TimerSettings> = settingsRepository.settingsFlow
}
