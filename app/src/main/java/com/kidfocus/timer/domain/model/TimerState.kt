package com.kidfocus.timer.domain.model

/**
 * Immutable snapshot of the timer at any point in time.
 *
 * @property phase Current phase of the timer (Focus, Break, or Idle).
 * @property totalSeconds Total duration of the current phase in seconds.
 * @property remainingSeconds Seconds remaining until the phase ends.
 * @property isRunning Whether the timer is actively counting down.
 * @property isPaused Whether the timer has been paused mid-session.
 * @property completedFocusSessions Number of focus sessions fully completed.
 */
data class TimerState(
    val phase: TimerPhase = TimerPhase.Idle,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val completedFocusSessions: Int = 0,
) {
    /** Progress from 0.0 (start) to 1.0 (complete). */
    val progress: Float
        get() = if (totalSeconds == 0) 0f
        else 1f - (remainingSeconds.toFloat() / totalSeconds.toFloat())

    /** Elapsed seconds since the phase started. */
    val elapsedSeconds: Int get() = totalSeconds - remainingSeconds

    /** Returns true when the countdown has reached zero. */
    val isFinished: Boolean get() = remainingSeconds <= 0 && totalSeconds > 0

    /** Returns true if less than 30 seconds remain (warning zone). */
    val isWarning: Boolean get() = remainingSeconds in 1..30

    /** Formatted MM:SS string for display. */
    val timeFormatted: String
        get() {
            val minutes = remainingSeconds / 60
            val seconds = remainingSeconds % 60
            return "%02d:%02d".format(minutes, seconds)
        }

    companion object {
        /** Default idle state with no active session. */
        val IDLE = TimerState()
    }
}
