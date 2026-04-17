package com.kidfocus.timer.data.repository

import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.model.TimerSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository that abstracts DataStore access for [TimerSettings].
 *
 * All callers should depend on this class rather than [SettingsDataStore] directly,
 * making it straightforward to swap the backing store in tests.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) {
    /** Continuously emits the latest [TimerSettings] from DataStore. */
    val settingsFlow: Flow<TimerSettings> = settingsDataStore.settingsFlow

    /** Persists [settings] to DataStore. */
    suspend fun saveSettings(settings: TimerSettings) =
        settingsDataStore.saveSettings(settings)

    /** Stores a SHA-256 hash of [pin]. The raw value is never persisted. */
    suspend fun savePin(pin: String) = settingsDataStore.savePin(pin)

    /** Removes the stored PIN hash, disabling the parental lock. */
    suspend fun clearPin() = settingsDataStore.clearPin()

    /** Marks onboarding as complete so the app opens directly to Home next launch. */
    suspend fun completeOnboarding() = settingsDataStore.completeOnboarding()

    /**
     * Returns true if [pin] hashes to the same value as [storedHash].
     * Call this to authenticate a parent unlock attempt.
     */
    fun verifyPin(pin: String, storedHash: String): Boolean =
        settingsDataStore.sha256Hex(pin) == storedHash
}
