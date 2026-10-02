package com.kidfocus.timer.domain.model

import org.junit.Assert.*
import org.junit.Test

class FocusTimePolicyTest {
    private fun focus(minutes: Int, remaining: Int = minutes * 60, paused: Boolean = false) =
        TimerState(TimerPhase.Focus, minutes * 60, remaining, isRunning = !paused, isPaused = paused)

    @Test fun selectionUsesFiveMinuteStepsAndCaps() {
        assertEquals(5, FocusTimePolicy.selection(1))
        assertEquals(50, FocusTimePolicy.selectionStep(45, 1))
        assertEquals(5, FocusTimePolicy.selectionStep(5, -1))
        assertEquals(120, FocusTimePolicy.selectionStep(120, 1))
    }

    @Test fun runningAndPausedExtensionsMoveFinishTimeWithoutReducingElapsedTime() {
        val running = focus(50, 49 * 60)
        val first = FocusTimePolicy.extend(running, TimerSettings())!!
        val second = FocusTimePolicy.extend(first, TimerSettings())!!
        assertEquals(60 * 60, second.totalSeconds)
        assertEquals(59 * 60, second.remainingSeconds)
        assertEquals(60, second.elapsedSeconds)
        assertEquals(10, second.extendedMinutes)
        val paused = second.copy(isRunning = false, isPaused = true)
        assertEquals(65 * 60, FocusTimePolicy.extend(paused, TimerSettings())!!.totalSeconds)
    }

    @Test fun disabledPerSessionAndTotalCapsRejectExtraTime() {
        val state = focus(90).copy(extendedMinutes = 30)
        assertEquals(FocusTimePolicy.Blocked.SESSION_LIMIT, FocusTimePolicy.blocked(state, TimerSettings()))
        assertNull(FocusTimePolicy.extend(focus(30), TimerSettings(allowChildExtendFocus = false)))
        assertNull(FocusTimePolicy.extend(focus(30), TimerSettings(maxExtraFocusMinutes = 0)))
        assertEquals(FocusTimePolicy.Blocked.TOTAL_LIMIT,
            FocusTimePolicy.blocked(focus(120), TimerSettings(maxExtraFocusMinutes = 60)))
        assertNull(FocusTimePolicy.extend(TimerState(TimerPhase.Break, 300, 300, isRunning = true), TimerSettings()))
    }
}
