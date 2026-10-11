package com.kidfocus.timer.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EarlyFocusCompletionTest {
    private val running = TimerState(TimerPhase.Focus, totalSeconds = 300, remainingSeconds = 181, isRunning = true)

    @Test fun earlyCompletionUsesActualElapsedSecondsWhileRunningOrPaused() {
        assertEquals(119, EarlyFocusCompletion.elapsedSeconds(running))
        assertEquals(119, EarlyFocusCompletion.elapsedSeconds(running.copy(isRunning = false, isPaused = true)))
    }

    @Test fun idleBreakUnstartedAndAlreadyFinishedCannotCompleteEarly() {
        assertNull(EarlyFocusCompletion.elapsedSeconds(TimerState.IDLE))
        assertNull(EarlyFocusCompletion.elapsedSeconds(running.copy(phase = TimerPhase.Break)))
        assertNull(EarlyFocusCompletion.elapsedSeconds(running.copy(remainingSeconds = 300)))
        assertNull(EarlyFocusCompletion.elapsedSeconds(running.copy(remainingSeconds = 0)))
    }
}
