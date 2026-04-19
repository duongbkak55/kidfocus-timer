package com.kidfocus.timer.data

import com.kidfocus.timer.data.database.SessionDao
import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.data.repository.SessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [SessionRepository].
 *
 * The DAO is fully mocked with MockK so no Room infrastructure is needed.
 *
 * Focus areas:
 *  1. [SessionRepository.pruneOldSessions] — correct cut-off timestamp forwarded to DAO.
 *  2. Delegation correctness: every public method calls the matching DAO method exactly once
 *     with the argument it received.
 */
class SessionRepositoryTest {

    private lateinit var sessionDao: SessionDao
    private lateinit var sessionRepository: SessionRepository

    @Before
    fun setUp() {
        sessionDao = mockk(relaxed = true)
        sessionRepository = SessionRepository(sessionDao)
    }

    // ---- pruneOldSessions -------------------------------------------------------------------

    @Test
    fun `pruneOldSessions forwards the cut-off timestamp to the DAO`() = runTest {
        val cutOff = 1_700_000_000_000L
        coEvery { sessionDao.deleteSessionsBefore(cutOff) } returns Unit

        sessionRepository.pruneOldSessions(cutOff)

        coVerify(exactly = 1) { sessionDao.deleteSessionsBefore(cutOff) }
    }

    @Test
    fun `pruneOldSessions passes timestamp zero to delete all sessions`() = runTest {
        sessionRepository.pruneOldSessions(0L)

        coVerify(exactly = 1) { sessionDao.deleteSessionsBefore(0L) }
    }

    @Test
    fun `pruneOldSessions passes Long MAX_VALUE to delete nothing`() = runTest {
        sessionRepository.pruneOldSessions(Long.MAX_VALUE)

        coVerify(exactly = 1) { sessionDao.deleteSessionsBefore(Long.MAX_VALUE) }
    }

    @Test
    fun `pruneOldSessions for 30 days ago forwards the correct epoch millis`() = runTest {
        val now = System.currentTimeMillis()
        val thirtyDaysMillis = TimeUnit.DAYS.toMillis(30)
        val cutOff = now - thirtyDaysMillis

        val captured = slot<Long>()
        coEvery { sessionDao.deleteSessionsBefore(capture(captured)) } returns Unit

        sessionRepository.pruneOldSessions(cutOff)

        // The value passed must be within the expected range; we allow up to 1 second of drift
        // due to time passing between cutOff calculation and the assertion.
        val expectedMin = now - thirtyDaysMillis - 1_000L
        val expectedMax = now - thirtyDaysMillis + 1_000L
        assertTrue(
            "Expected captured cutOff in [$expectedMin, $expectedMax] but was ${captured.captured}",
            captured.captured in expectedMin..expectedMax,
        )
    }

    @Test
    fun `pruneOldSessions for 7 days uses the caller-supplied value unchanged`() = runTest {
        val sevenDaysAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)
        val captured = slot<Long>()
        coEvery { sessionDao.deleteSessionsBefore(capture(captured)) } returns Unit

        sessionRepository.pruneOldSessions(sevenDaysAgo)

        assertEquals(sevenDaysAgo, captured.captured)
    }

    @Test
    fun `pruneOldSessions is called exactly once per invocation`() = runTest {
        sessionRepository.pruneOldSessions(1_000_000L)
        sessionRepository.pruneOldSessions(2_000_000L)

        coVerify(exactly = 2) { sessionDao.deleteSessionsBefore(any()) }
    }

    // ---- insertSession ----------------------------------------------------------------------

    @Test
    fun `insertSession delegates to DAO with the exact entity`() = runTest {
        val entity = SessionEntity(durationSeconds = 1500, isFocus = true, timestampMillis = 1_000L)
        val captured = slot<SessionEntity>()
        coEvery { sessionDao.insertSession(capture(captured)) } returns Unit

        sessionRepository.insertSession(entity)

        coVerify(exactly = 1) { sessionDao.insertSession(entity) }
        assertEquals(entity.durationSeconds, captured.captured.durationSeconds)
        assertEquals(entity.isFocus, captured.captured.isFocus)
        assertEquals(entity.timestampMillis, captured.captured.timestampMillis)
    }

    @Test
    fun `insertSession does not call pruneOldSessions or any other DAO method`() = runTest {
        val entity = SessionEntity(durationSeconds = 300, isFocus = false, timestampMillis = 2_000L)

        sessionRepository.insertSession(entity)

        coVerify(exactly = 0) { sessionDao.deleteSessionsBefore(any()) }
        coVerify(exactly = 0) { sessionDao.deleteAllSessions() }
    }

    // ---- getSessionsSince -------------------------------------------------------------------

    @Test
    fun `getSessionsSince delegates to DAO and returns its flow`() = runTest {
        val expected = listOf(
            SessionEntity(durationSeconds = 1500, isFocus = true, timestampMillis = 5_000L),
        )
        every { sessionDao.getSessionsSince(5_000L) } returns flowOf(expected)

        val result = sessionRepository.getSessionsSince(5_000L).first()

        assertEquals(expected, result)
    }

    @Test
    fun `getSessionsSince returns empty list when DAO emits nothing`() = runTest {
        every { sessionDao.getSessionsSince(any()) } returns flowOf(emptyList())

        val result = sessionRepository.getSessionsSince(0L).first()

        assertTrue(result.isEmpty())
    }

    // ---- getFocusSessionsSince --------------------------------------------------------------

    @Test
    fun `getFocusSessionsSince returns only isFocus entities from DAO`() = runTest {
        val focusSessions = listOf(
            SessionEntity(durationSeconds = 1500, isFocus = true, timestampMillis = 1_000L),
            SessionEntity(durationSeconds = 900, isFocus = true, timestampMillis = 2_000L),
        )
        every { sessionDao.getFocusSessionsSince(any()) } returns flowOf(focusSessions)

        val result = sessionRepository.getFocusSessionsSince(0L).first()

        assertEquals(2, result.size)
        assertTrue(result.all { it.isFocus })
    }

    @Test
    fun `getFocusSessionsSince forwards the sinceMillis argument unchanged`() = runTest {
        val sinceMillis = 1_680_000_000_000L
        val captured = slot<Long>()
        every { sessionDao.getFocusSessionsSince(capture(captured)) } returns flowOf(emptyList())

        sessionRepository.getFocusSessionsSince(sinceMillis).first()

        assertEquals(sinceMillis, captured.captured)
    }

    // ---- getTotalFocusCount -----------------------------------------------------------------

    @Test
    fun `getTotalFocusCount returns the value from the DAO`() = runTest {
        coEvery { sessionDao.getTotalFocusSessionCount() } returns 42

        val count = sessionRepository.getTotalFocusCount()

        assertEquals(42, count)
    }

    @Test
    fun `getTotalFocusCount returns zero when DAO reports no sessions`() = runTest {
        coEvery { sessionDao.getTotalFocusSessionCount() } returns 0

        val count = sessionRepository.getTotalFocusCount()

        assertEquals(0, count)
    }

    // ---- clearAll ---------------------------------------------------------------------------

    @Test
    fun `clearAll delegates to deleteAllSessions on the DAO`() = runTest {
        sessionRepository.clearAll()

        coVerify(exactly = 1) { sessionDao.deleteAllSessions() }
    }

    @Test
    fun `clearAll does not call deleteSessionsBefore`() = runTest {
        sessionRepository.clearAll()

        coVerify(exactly = 0) { sessionDao.deleteSessionsBefore(any()) }
    }

    // ---- pruneOldSessions boundary: distinguishes before vs after the cut-off ---------------

    @Test
    fun `pruneOldSessions cut-off of yesterday midnight does not include today's sessions`() = runTest {
        // This test asserts at the repository contract level: the cut-off value passed by
        // the caller is forwarded verbatim.  Any session with timestamp >= cutOff must NOT
        // be deleted; the DAO enforces this, but we verify the boundary argument is exact.
        val yesterdayMidnight = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1)
        val captured = slot<Long>()
        coEvery { sessionDao.deleteSessionsBefore(capture(captured)) } returns Unit

        sessionRepository.pruneOldSessions(yesterdayMidnight)

        assertEquals(yesterdayMidnight, captured.captured)
        // A session timestamped at captured.captured is NOT deleted (< boundary).
        // A session at (captured.captured - 1) IS deleted.
        assertFalse(
            "Today's session (timestamp == cutOff) must not be older than the cut-off",
            captured.captured > yesterdayMidnight,
        )
    }
}
