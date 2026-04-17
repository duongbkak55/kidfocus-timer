package com.kidfocus.timer.domain.model

/**
 * Represents the current phase of the Pomodoro-style timer cycle.
 */
sealed class TimerPhase {

    /** Active focus/study period. */
    data object Focus : TimerPhase()

    /** Rest/break period between focus sessions. */
    data object Break : TimerPhase()

    /** Initial state before any session starts. */
    data object Idle : TimerPhase()

    /** Returns a human-readable label for the phase. */
    val label: String
        get() = when (this) {
            is Focus -> "Tập trung"
            is Break -> "Nghỉ ngơi"
            is Idle -> "Sẵn sàng"
        }

    /** Returns true if this phase is an active focus session. */
    val isFocus: Boolean get() = this is Focus

    /** Returns true if this phase is a break session. */
    val isBreak: Boolean get() = this is Break

    /** Returns true if the timer is in idle state. */
    val isIdle: Boolean get() = this is Idle
}
