package com.kidfocus.timer.domain

import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.data.repository.SessionRepository
import com.kidfocus.timer.domain.usecase.GetTodaySessionsUseCase
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

/**
 * Unit tests for [GetTodaySessionsUseCase].
 *
 * Verifies that the use case queries from today's midnight, and that callers
 * can derive correct aggregates (total minutes, session count, focus/break split,
 * and goal-progress ratios) from the emitted list.
 */
class GetTodaySessionsUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var getTodaySessionsUseCase: GetTodaySessionsUseCase

    @Before
    fun setUp() {
        sessionRepository = mockk()
        getTodaySessionsUseCase = GetTodaySessionsUseCase(sessionRepository)
    }

    // ---- Midnight boundary ------------------------------------------------------------------

    @Test
    fun `invokes repository with a timestamp at or after today's midnight`() {
        val startOfDayBefore = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val sinceSlot = slot<Long>()
        every { sessionRepository.getSessionsSince(capture(sinceSlot)) } returns flowOf(emptyList())

        getTodaySessionsUseCase()

        val startOfDayAfter = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertTrue(
            "Expected sinceMillis in [$startOfDayBefore, $startOfDayAfter] but was ${sinceSlot.captured}",
            sinceSlot.captured in startOfDayBefore..startOfDayAfter,
        )
    }

    @Test
    fun `midnight boundary is not later than the current time`() {
        val capturedSince = slot<Long>()
        every { sessionRepository.getSessionsSince(capture(capturedSince)) } returns flowOf(emptyList())

        val beforeInvoke = System.currentTimeMillis()
        getTodaySessionsUseCase()

        assertTrue(capturedSince.captured <= beforeInvoke)
    }

    // ---- Empty result -----------------------------------------------------------------------

    @Test
    fun `emits empty list when no sessions exist today`() = runTest {
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(emptyList())

        val sessions = getTodaySessionsUseCase().first()

        assertTrue(sessions.isEmpty())
    }

    // ---- Session count aggregation ----------------------------------------------------------

    @Test
    fun `session count equals size of emitted list`() = runTest {
        val todaySessions = listOf(
            focusSession(durationSeconds = 1500),
            breakSession(durationSeconds = 300),
            focusSession(durationSeconds = 1500),
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()

        assertEquals(3, sessions.size)
    }

    @Test
    fun `single session list has count of one`() = runTest {
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(
            listOf(focusSession(durationSeconds = 1500))
        )

        val sessions = getTodaySessionsUseCase().first()

        assertEquals(1, sessions.size)
    }

    // ---- Total minutes aggregation ----------------------------------------------------------

    @Test
    fun `total focus minutes sums only isFocus sessions`() = runTest {
        val todaySessions = listOf(
            focusSession(durationSeconds = 1500), // 25 min
            breakSession(durationSeconds = 300),  //  5 min (must not be counted)
            focusSession(durationSeconds = 900),  // 15 min
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val totalFocusMinutes = sessions.filter { it.isFocus }.sumOf { it.durationMinutes }

        assertEquals(40, totalFocusMinutes)
    }

    @Test
    fun `total focus minutes is zero when all sessions are breaks`() = runTest {
        val todaySessions = listOf(
            breakSession(durationSeconds = 300),
            breakSession(durationSeconds = 600),
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val totalFocusMinutes = sessions.filter { it.isFocus }.sumOf { it.durationMinutes }

        assertEquals(0, totalFocusMinutes)
    }

    @Test
    fun `total minutes across all session types sums correctly`() = runTest {
        val todaySessions = listOf(
            focusSession(durationSeconds = 1500), // 25 min
            breakSession(durationSeconds = 300),  //  5 min
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val totalMinutes = sessions.sumOf { it.durationMinutes }

        assertEquals(30, totalMinutes)
    }

    // ---- Sub-minute durations ---------------------------------------------------------------

    @Test
    fun `sub-minute sessions contribute zero durationMinutes to total`() = runTest {
        val todaySessions = listOf(
            focusSession(durationSeconds = 59),  // 0 whole minutes
            focusSession(durationSeconds = 1500), // 25 min
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val totalFocusMinutes = sessions.filter { it.isFocus }.sumOf { it.durationMinutes }

        assertEquals(25, totalFocusMinutes)
    }

    // ---- Focus / break split ----------------------------------------------------------------

    @Test
    fun `focus session count is correctly derived from the list`() = runTest {
        val todaySessions = listOf(
            focusSession(durationSeconds = 1500),
            focusSession(durationSeconds = 1500),
            breakSession(durationSeconds = 300),
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val focusCount = sessions.count { it.isFocus }

        assertEquals(2, focusCount)
    }

    @Test
    fun `break session count is correctly derived from the list`() = runTest {
        val todaySessions = listOf(
            focusSession(durationSeconds = 1500),
            breakSession(durationSeconds = 300),
            breakSession(durationSeconds = 300),
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val breakCount = sessions.count { !it.isFocus }

        assertEquals(2, breakCount)
    }

    // ---- Goal progress ----------------------------------------------------------------------

    @Test
    fun `goal progress is 0 percent when no focus minutes have been logged`() = runTest {
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(emptyList())

        val sessions = getTodaySessionsUseCase().first()
        val goalMinutes = 120
        val progress = sessions.filter { it.isFocus }.sumOf { it.durationMinutes }.toFloat() / goalMinutes

        assertEquals(0f, progress, 0.001f)
    }

    @Test
    fun `goal progress is 1_0 (100 percent) when focus minutes equal the daily goal`() = runTest {
        // Goal: 120 min = 2 x 25-min sessions (3000s) + 1 x 70-min session (4200s)
        val todaySessions = listOf(
            focusSession(durationSeconds = 3000),  // 50 min
            focusSession(durationSeconds = 4200),  // 70 min
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val goalMinutes = 120
        val totalFocusMinutes = sessions.filter { it.isFocus }.sumOf { it.durationMinutes }
        val progress = totalFocusMinutes.toFloat() / goalMinutes

        assertEquals(1f, progress, 0.001f)
    }

    @Test
    fun `goal progress is 0_5 (50 percent) when half the daily goal is complete`() = runTest {
        val todaySessions = listOf(
            focusSession(durationSeconds = 3600), // 60 min
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val goalMinutes = 120
        val totalFocusMinutes = sessions.filter { it.isFocus }.sumOf { it.durationMinutes }
        val progress = totalFocusMinutes.toFloat() / goalMinutes

        assertEquals(0.5f, progress, 0.001f)
    }

    @Test
    fun `goal progress can exceed 1_0 when daily goal is surpassed`() = runTest {
        val todaySessions = listOf(
            focusSession(durationSeconds = 9000), // 150 min
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(todaySessions)

        val sessions = getTodaySessionsUseCase().first()
        val goalMinutes = 120
        val totalFocusMinutes = sessions.filter { it.isFocus }.sumOf { it.durationMinutes }
        val progress = totalFocusMinutes.toFloat() / goalMinutes

        assertTrue("Expected progress > 1.0 but was $progress", progress > 1f)
    }

    // ---- Flow re-emission -------------------------------------------------------------------

    @Test
    fun `flow emits updated list when repository emits a second value`() = runTest {
        val firstBatch = listOf(focusSession(durationSeconds = 1500))
        val secondBatch = listOf(
            focusSession(durationSeconds = 1500),
            focusSession(durationSeconds = 1500),
        )
        every { sessionRepository.getSessionsSince(any()) } returns flowOf(firstBatch, secondBatch)

        val allEmissions = mutableListOf<List<SessionEntity>>()
        getTodaySessionsUseCase().collect { allEmissions.add(it) }

        assertEquals(2, allEmissions.size)
        assertEquals(1, allEmissions[0].size)
        assertEquals(2, allEmissions[1].size)
    }

    // ---- Factory helpers --------------------------------------------------------------------

    private fun focusSession(
        durationSeconds: Int,
        timestampMillis: Long = System.currentTimeMillis(),
    ) = SessionEntity(
        durationSeconds = durationSeconds,
        isFocus = true,
        timestampMillis = timestampMillis,
    )

    private fun breakSession(
        durationSeconds: Int,
        timestampMillis: Long = System.currentTimeMillis(),
    ) = SessionEntity(
        durationSeconds = durationSeconds,
        isFocus = false,
        timestampMillis = timestampMillis,
    )
}
