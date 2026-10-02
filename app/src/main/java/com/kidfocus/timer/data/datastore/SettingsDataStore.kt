package com.kidfocus.timer.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kidfocus.timer.domain.model.AppTheme
import com.kidfocus.timer.domain.model.TimerSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
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
    @Volatile private var lastFocusPruneAtMillis: Long? = null

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
        val LEARNING_AGE_BAND = stringPreferencesKey("learning_age_band")
        val CALM_MODE_ENABLED = booleanPreferencesKey("calm_mode_enabled")
        val KEEP_SCREEN_ON_ENABLED = booleanPreferencesKey("keep_screen_on_enabled")
        val ALLOW_CHILD_EXTEND_FOCUS = booleanPreferencesKey("allow_child_extend_focus")
        val MAX_EXTRA_FOCUS_MINUTES = intPreferencesKey("max_extra_focus_minutes")
        val ACTIVE_CHILD_PROFILE_ID = stringPreferencesKey("active_child_profile_id")
        val SCHEDULE_ALARM_REMINDER_DISMISSED = booleanPreferencesKey("schedule_alarm_reminder_dismissed")
    }

    val scheduleAlarmReminderDismissed: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.SCHEDULE_ALARM_REMINDER_DISMISSED] ?: false
    }

    /** Local-only metadata keyed by the timer log UUID; Room and cloud dayLogs stay unchanged. */
    val focusExtensions: Flow<Map<String, Int>> = context.dataStore.data.onStart {
        pruneFocusExtensions()
    }.map { prefs ->
        prefs.asMap().entries.mapNotNull { (key, value) ->
            if (key.name.startsWith("focus_extension_") && value is Int)
                key.name.removePrefix("focus_extension_") to value else null
        }.toMap()
    }

    suspend fun addFocusExtension(logId: String, minutes: Int, nowMillis: Long = System.currentTimeMillis()) {
        require(minutes > 0 && minutes % 5 == 0)
        context.dataStore.edit { prefs ->
            val key = intPreferencesKey("focus_extension_$logId")
            prefs[key] = ((prefs[key] ?: 0) + minutes).coerceAtMost(60)
            prefs[longPreferencesKey("focus_extension_updated_$logId")] = nowMillis
        }
    }

    /** Old W6 rows had no timestamp. Start their 60-day retention at first inspection. */
    suspend fun pruneFocusExtensions(nowMillis: Long = System.currentTimeMillis()) {
        val last = lastFocusPruneAtMillis
        if (last != null && nowMillis - last in 0 until FOCUS_PRUNE_INTERVAL_MILLIS) return
        val cutoff = nowMillis - FOCUS_EXTENSION_RETENTION_MILLIS
        context.dataStore.edit { prefs ->
            prefs.asMap().entries.filter { (key, value) ->
                key.name.startsWith("focus_extension_") && value is Int
            }.forEach { (key, _) ->
                val logId = key.name.removePrefix("focus_extension_")
                val updatedKey = longPreferencesKey("focus_extension_updated_$logId")
                val updatedAt = prefs[updatedKey]
                when {
                    updatedAt == null -> prefs[updatedKey] = nowMillis
                    updatedAt < cutoff -> {
                        prefs.remove(intPreferencesKey(key.name))
                        prefs.remove(updatedKey)
                    }
                }
            }
            prefs.asMap().keys.filter { it.name.startsWith("focus_extension_updated_") }.forEach { key ->
                val logId = key.name.removePrefix("focus_extension_updated_")
                if (prefs[intPreferencesKey("focus_extension_$logId")] == null) prefs.remove(longPreferencesKey(key.name))
            }
        }
        lastFocusPruneAtMillis = nowMillis
    }

    private companion object {
        const val FOCUS_PRUNE_INTERVAL_MILLIS = 24L * 60 * 60 * 1000
        const val FOCUS_EXTENSION_RETENTION_MILLIS = 60L * FOCUS_PRUNE_INTERVAL_MILLIS
    }

    suspend fun dismissScheduleAlarmReminder() {
        context.dataStore.edit { it[Keys.SCHEDULE_ALARM_REMINDER_DISMISSED] = true }
    }

    val scheduleAnchorsJson: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { (key, value) ->
            if (key.name.startsWith("schedule_anchors_") && value is String)
                key.name.removePrefix("schedule_anchors_") to value else null
        }.toMap()
    }

    val schedulePlansJson: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { (key, value) ->
            if (key.name.startsWith("schedule_plan_") && value is String) key.name.removePrefix("schedule_plan_") to value else null
        }.toMap()
    }

    suspend fun saveSchedulePlansJson(rows: Map<String, String>) {
        context.dataStore.edit { prefs -> rows.forEach { (profile, json) ->
            val key = stringPreferencesKey("schedule_plan_$profile")
            // Do not resurrect a synced plan after a manual anchor edit.
            val anchors = prefs[stringPreferencesKey("schedule_anchors_$profile")]
            val plan = runCatching { com.kidfocus.timer.data.schedule.SchedulePlanJson.decode(json) }.getOrNull()
            prefs[key] = if (plan != null && anchors != null && !plan.matches(com.kidfocus.timer.data.schedule.ScheduleJson.decodeAnchors(anchors))) "null" else json
        } }
    }

    suspend fun saveScheduleAnchorsJson(rows: Map<String, String>) {
        context.dataStore.edit { prefs -> rows.forEach { (profile, json) ->
            val planKey = stringPreferencesKey("schedule_plan_$profile")
            val plan = prefs[planKey]?.let { runCatching { com.kidfocus.timer.data.schedule.SchedulePlanJson.decode(it) }.getOrNull() }
            if (plan != null && !plan.matches(com.kidfocus.timer.data.schedule.ScheduleJson.decodeAnchors(json))) prefs[planKey] = "null"
            prefs[stringPreferencesKey("schedule_anchors_$profile")] = json
        } }
    }

    suspend fun getScheduleSnapshotJson(profileId: String): String? =
        context.dataStore.data.first()[stringPreferencesKey("schedule_snapshot_$profileId")]

    suspend fun saveScheduleSnapshotJson(profileId: String, json: String?) {
        context.dataStore.edit { prefs ->
            val key = stringPreferencesKey("schedule_snapshot_$profileId")
            if (json == null) prefs.remove(key) else prefs[key] = json
        }
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
            learningAgeBand = prefs[Keys.LEARNING_AGE_BAND]
                ?: TimerSettings.DEFAULT_LEARNING_AGE_BAND,
            calmModeEnabled = prefs[Keys.CALM_MODE_ENABLED] ?: false,
            keepScreenOnEnabled = prefs[Keys.KEEP_SCREEN_ON_ENABLED] ?: true,
            allowChildExtendFocus = prefs[Keys.ALLOW_CHILD_EXTEND_FOCUS] ?: true,
            maxExtraFocusMinutes = prefs[Keys.MAX_EXTRA_FOCUS_MINUTES] ?: TimerSettings.DEFAULT_MAX_EXTRA_FOCUS_MINUTES,
            activeChildProfileId = prefs[Keys.ACTIVE_CHILD_PROFILE_ID]
                ?: TimerSettings.DEFAULT_CHILD_PROFILE_ID,
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
            prefs[Keys.LEARNING_AGE_BAND] = settings.learningAgeBand
            prefs[Keys.CALM_MODE_ENABLED] = settings.calmModeEnabled
            prefs[Keys.KEEP_SCREEN_ON_ENABLED] = settings.keepScreenOnEnabled
            prefs[Keys.ALLOW_CHILD_EXTEND_FOCUS] = settings.allowChildExtendFocus
            prefs[Keys.MAX_EXTRA_FOCUS_MINUTES] = settings.maxExtraFocusMinutes
            prefs[Keys.ACTIVE_CHILD_PROFILE_ID] = settings.activeChildProfileId

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

    suspend fun saveLearningAgeBand(ageBand: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LEARNING_AGE_BAND] = ageBand
        }
    }

    suspend fun saveActiveChildProfileId(profileId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.ACTIVE_CHILD_PROFILE_ID] = profileId
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
