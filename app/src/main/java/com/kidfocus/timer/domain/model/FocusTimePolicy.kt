package com.kidfocus.timer.domain.model

/** One five-minute step is the only child-facing change to a running focus session. */
object FocusTimePolicy {
    const val STEP_MINUTES = 5
    val extraChoices = listOf(0, 15, 30, 60)

    enum class Blocked { INACTIVE, DISABLED, SESSION_LIMIT, TOTAL_LIMIT }

    fun selection(minutes: Int): Int =
        ((minutes.coerceIn(TimerSettings.MIN_FOCUS_MINUTES, TimerSettings.MAX_FOCUS_MINUTES) + 2) / STEP_MINUTES * STEP_MINUTES)
            .coerceIn(TimerSettings.MIN_FOCUS_MINUTES, TimerSettings.MAX_FOCUS_MINUTES)

    fun selectionStep(minutes: Int, direction: Int): Int =
        (selection(minutes) + direction.coerceIn(-1, 1) * STEP_MINUTES)
            .coerceIn(TimerSettings.MIN_FOCUS_MINUTES, TimerSettings.MAX_FOCUS_MINUTES)

    fun blocked(state: TimerState, settings: TimerSettings): Blocked? = when {
        !state.phase.isFocus || (!state.isRunning && !state.isPaused) -> Blocked.INACTIVE
        !settings.allowChildExtendFocus || settings.maxExtraFocusMinutes == 0 -> Blocked.DISABLED
        state.extendedMinutes + STEP_MINUTES > settings.maxExtraFocusMinutes -> Blocked.SESSION_LIMIT
        state.totalSeconds + STEP_MINUTES * 60 > TimerSettings.MAX_FOCUS_MINUTES * 60 -> Blocked.TOTAL_LIMIT
        else -> null
    }

    fun extend(state: TimerState, settings: TimerSettings): TimerState? =
        if (blocked(state, settings) != null) null else state.copy(
            totalSeconds = state.totalSeconds + STEP_MINUTES * 60,
            remainingSeconds = state.remainingSeconds + STEP_MINUTES * 60,
            extendedMinutes = state.extendedMinutes + STEP_MINUTES,
        )
}
