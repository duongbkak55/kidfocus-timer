package com.kidfocus.timer.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kidfocus.timer.domain.model.AppTheme
import com.kidfocus.timer.domain.model.TimerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "kidfocus_settings")

/**
 * DataStore wrapper for persisting [TimerSettings].
 *
 * PINs are stored as SHA-256 hex digests — plaintext is never written to disk.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val FOCUS_DURATION = intPreferencesKey("focus_duration_minutes")
        val BREAK_DURATION = intPreferencesKey("break_duration_minutes")
        val APP_THEME = stringPreferencesKey("app_theme")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val VIBRATION_ENABLED = booleanPreferencesKey("vibration_enabled")
        val COMPLETED_FOCUS_SESSIONS = intPreferencesKey("completed_focus_sessions")
        val DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
    }

    /** Emits [TimerSettings] whenever any preference value changes. */
    val settingsFlow: Flow<TimerSettings> = context.dataStore.data.map { prefs ->
        TimerSettings(
            focusDurationMinutes = prefs[Keys.FOCUS_DURATION] ?: TimerSettings.DEFAULT_FOCUS_MINUTES,
            breakDurationMinutes = prefs[Keys.BREAK_DURATION] ?: TimerSettings.DEFAULT_BREAK_MINUTES,
            appTheme = AppTheme.fromKey(prefs[Keys.APP_THEME] ?: AppTheme.OCEAN.name),
            pinHash = prefs[Keys.PIN_HASH],
            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: false,
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            vibrationEnabled = prefs[Keys.VIBRATION_ENABLED] ?: true,
            dailyGoalMinutes = prefs[Keys.DAILY_GOAL_MINUTES] ?: TimerSettings.DEFAULT_DAILY_GOAL_MINUTES,
            geminiApiKey = prefs[Keys.GEMINI_API_KEY],
        )
    }

    /** Persists the full [TimerSettings] object in a single transactional write. */
    suspend fun saveSettings(settings: TimerSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.FOCUS_DURATION] = settings.focusDurationMinutes
            prefs[Keys.BREAK_DURATION] = settings.breakDurationMinutes
            prefs[Keys.APP_THEME] = settings.appTheme.name
            prefs[Keys.ONBOARDING_COMPLETED] = settings.onboardingCompleted
            prefs[Keys.SOUND_ENABLED] = settings.soundEnabled
            prefs[Keys.VIBRATION_ENABLED] = settings.vibrationEnabled
            prefs[Keys.DAILY_GOAL_MINUTES] = settings.dailyGoalMinutes

            if (settings.pinHash != null) {
                prefs[Keys.PIN_HASH] = settings.pinHash
            } else {
                prefs.remove(Keys.PIN_HASH)
            }
            if (settings.geminiApiKey != null) {
                prefs[Keys.GEMINI_API_KEY] = settings.geminiApiKey
            } else {
                prefs.remove(Keys.GEMINI_API_KEY)
            }
        }
    }

    suspend fun saveGeminiApiKey(apiKey: String) {
        context.dataStore.edit { prefs ->
            if (apiKey.isBlank()) prefs.remove(Keys.GEMINI_API_KEY)
            else prefs[Keys.GEMINI_API_KEY] = apiKey.trim()
        }
    }

    /** Saves a hashed PIN. The plaintext [pin] is never stored. */
    suspend fun savePin(pin: String) {
        val hash = sha256Hex(pin)
        context.dataStore.edit { prefs ->
            prefs[Keys.PIN_HASH] = hash
        }
    }

    /** Removes the stored PIN, effectively disabling the parental lock. */
    suspend fun clearPin() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PIN_HASH)
        }
    }

    /** Marks the onboarding flow as completed. */
    suspend fun completeOnboarding() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_COMPLETED] = true
        }
    }

    /** Returns the persisted completed focus session count (0 if never written). */
    suspend fun getCompletedFocusSessions(): Int =
        context.dataStore.data.map { prefs ->
            prefs[Keys.COMPLETED_FOCUS_SESSIONS] ?: 0
        }.first()

    /** Persists the completed focus session count. */
    suspend fun saveCompletedFocusSessions(count: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.COMPLETED_FOCUS_SESSIONS] = count
        }
    }

    /**
     * Verifies that [pin] matches the stored SHA-256 hash.
     * Returns false if no PIN has been set.
     */
    suspend fun verifyPin(pin: String, storedHash: String): Boolean =
        sha256Hex(pin) == storedHash

    /**
     * Computes the SHA-256 hex digest of [input].
     * Used for PIN hashing — never call this with sensitive data you need to recover.
     */
    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
