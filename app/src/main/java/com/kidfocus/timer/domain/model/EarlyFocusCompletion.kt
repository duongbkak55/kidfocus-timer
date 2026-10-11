package com.kidfocus.timer.domain.model

/** Seconds actually focused when a child marks an active task done before its timer ends. */
object EarlyFocusCompletion {
    fun elapsedSeconds(state: TimerState): Int? = state
        .takeIf { it.phase.isFocus && !it.isFinished && (it.isRunning || it.isPaused) }
        ?.elapsedSeconds
        ?.takeIf { it > 0 }
}
