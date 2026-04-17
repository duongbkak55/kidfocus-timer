package com.kidfocus.timer.domain

import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.domain.model.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [TimerState] computed properties.
 */
class TimerStateTest {

    // ---- progress ---------------------------------------------------------------------------

    @Test
    fun `progress is zero when no time has elapsed`() {
        val state = TimerState(
            phase = TimerPhase.Focus,
            totalSeconds = 1500,
            remainingSeconds = 1500,
        )
        assertEquals(0f, state.progress, 0.001f)
    }

    @Test
    fun `progress is one when all time has elapsed`() {
        val state = TimerState(
            phase = TimerPhase.Focus,
            totalSeconds = 1500,
            remainingSeconds = 0,
        )
        assertEquals(1f, state.progress, 0.001f)
    }

    @Test
    fun `progress is half when halfway through`() {
        val state = TimerState(
            phase = TimerPhase.Focus,
            totalSeconds = 1000,
            remainingSeconds = 500,
        )
        assertEquals(0.5f, state.progress, 0.001f)
    }

    @Test
    fun `progress is zero when totalSeconds is zero`() {
        val state = TimerState(totalSeconds = 0, remainingSeconds = 0)
        assertEquals(0f, state.progress, 0.001f)
    }

    // ---- isFinished -------------------------------------------------------------------------

    @Test
    fun `isFinished true when remaining is zero and total is positive`() {
        val state = TimerState(totalSeconds = 60, remainingSeconds = 0)
        assertTrue(state.isFinished)
    }

    @Test
    fun `isFinished false when remaining is positive`() {
        val state = TimerState(totalSeconds = 60, remainingSeconds = 1)
        assertFalse(state.isFinished)
    }

    @Test
    fun `isFinished false on IDLE default state`() {
        assertFalse(TimerState.IDLE.isFinished)
    }

    // ---- isWarning --------------------------------------------------------------------------

    @Test
    fun `isWarning true when 30 seconds remain`() {
        val state = TimerState(totalSeconds = 1500, remainingSeconds = 30)
        assertTrue(state.isWarning)
    }

    @Test
    fun `isWarning true when 1 second remains`() {
        val state = TimerState(totalSeconds = 1500, remainingSeconds = 1)
        assertTrue(state.isWarning)
    }

    @Test
    fun `isWarning false when 31 seconds remain`() {
        val state = TimerState(totalSeconds = 1500, remainingSeconds = 31)
        assertFalse(state.isWarning)
    }

    @Test
    fun `isWarning false when 0 seconds remain`() {
        val state = TimerState(totalSeconds = 1500, remainingSeconds = 0)
        assertFalse(state.isWarning)
    }

    // ---- timeFormatted -----------------------------------------------------------------------

    @Test
    fun `timeFormatted displays MM colon SS correctly for 1500 seconds`() {
        val state = TimerState(remainingSeconds = 1500)
        assertEquals("25:00", state.timeFormatted)
    }

    @Test
    fun `timeFormatted pads single-digit seconds with leading zero`() {
        val state = TimerState(remainingSeconds = 61)
        assertEquals("01:01", state.timeFormatted)
    }

    @Test
    fun `timeFormatted shows 00 colon 00 when no time remains`() {
        val state = TimerState(remainingSeconds = 0)
        assertEquals("00:00", state.timeFormatted)
    }

    // ---- elapsedSeconds ---------------------------------------------------------------------

    @Test
    fun `elapsedSeconds equals totalSeconds minus remainingSeconds`() {
        val state = TimerState(totalSeconds = 300, remainingSeconds = 100)
        assertEquals(200, state.elapsedSeconds)
    }

    // ---- Phase helpers -----------------------------------------------------------------------

    @Test
    fun `focus phase isFocus returns true`() {
        assertTrue(TimerPhase.Focus.isFocus)
    }

    @Test
    fun `break phase isBreak returns true`() {
        assertTrue(TimerPhase.Break.isBreak)
    }

    @Test
    fun `idle phase isIdle returns true`() {
        assertTrue(TimerPhase.Idle.isIdle)
    }

    @Test
    fun `idle phase isFocus returns false`() {
        assertFalse(TimerPhase.Idle.isFocus)
    }
}
