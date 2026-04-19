package com.kidfocus.timer.domain.model

/**
 * User-configurable settings for the timer app.
 *
 * @property focusDurationMinutes Duration of a focus session in minutes (5–120).
 * @property breakDurationMinutes Duration of a break session in minutes (1–30).
 * @property appTheme Currently selected color theme.
 * @property pinHash SHA-256 hex hash of the parent PIN. Null if no PIN is set.
 * @property onboardingCompleted Whether the user has finished the onboarding flow.
 * @property soundEnabled Whether audio cues are played on phase transitions.
 * @property vibrationEnabled Whether the device vibrates on phase transitions.
 * @property dailyGoalMinutes Target focus minutes per day (30–240).
 */
data class TimerSettings(
    val focusDurationMinutes: Int = DEFAULT_FOCUS_MINUTES,
    val breakDurationMinutes: Int = DEFAULT_BREAK_MINUTES,
    val appTheme: AppTheme = AppTheme.OCEAN,
    val pinHash: String? = null,
    val onboardingCompleted: Boolean = false,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val dailyGoalMinutes: Int = DEFAULT_DAILY_GOAL_MINUTES,
) {
    /** True if the parent has set a PIN to lock settings. */
    val hasPinSet: Boolean get() = pinHash != null

    /** Focus duration in seconds for direct use by the timer engine. */
    val focusDurationSeconds: Int get() = focusDurationMinutes * 60

    /** Break duration in seconds for direct use by the timer engine. */
    val breakDurationSeconds: Int get() = breakDurationMinutes * 60

    companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_BREAK_MINUTES = 5
        const val MIN_FOCUS_MINUTES = 5
        const val MAX_FOCUS_MINUTES = 120
        const val MIN_BREAK_MINUTES = 1
        const val MAX_BREAK_MINUTES = 30
        const val DEFAULT_DAILY_GOAL_MINUTES = 120
        const val MIN_DAILY_GOAL_MINUTES = 30
        const val MAX_DAILY_GOAL_MINUTES = 240
    }
}
