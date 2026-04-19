package com.kidfocus.timer.domain

import com.kidfocus.timer.data.database.SessionEntity
import com.kidfocus.timer.data.repository.SessionRepository
import com.kidfocus.timer.domain.usecase.RecordSessionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [RecordSessionUseCase].
 *
 * Verifies that focus and break sessions are passed to [SessionRepository] with
 * the correct field values, and that guard-clause filtering (zero / negative duration)
 * prevents spurious inserts.
 */
class RecordSessionUseCaseTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var recordSessionUseCase: RecordSessionUseCase

    @Before
    fun setUp() {
        sessionRepository = mockk(relaxed = true)
        recordSessionUseCase = RecordSessionUseCase(sessionRepository)
    }

    // ---- Focus sessions ---------------------------------------------------------------------

    @Test
    fun `focus session is recorded with isFocus true`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1500, isFocus = true)

        coVerify(exactly = 1) { sessionRepository.insertSession(any()) }
        assertTrue(slot.captured.isFocus)
    }

    @Test
    fun `focus session carries the exact duration supplied`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1500, isFocus = true)

        assertEquals(1500, slot.captured.durationSeconds)
    }

    @Test
    fun `focus session durationMinutes converts seconds to whole minutes`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1500, isFocus = true) // 25 min exactly

        assertEquals(25, slot.captured.durationMinutes)
    }

    @Test
    fun `focus session with non-round duration truncates to whole minutes`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1537, isFocus = true) // 25 min 37 sec

        assertEquals(25, slot.captured.durationMinutes)
    }

    // ---- Break sessions ---------------------------------------------------------------------

    @Test
    fun `break session is recorded with isFocus false`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 300, isFocus = false)

        coVerify(exactly = 1) { sessionRepository.insertSession(any()) }
        assertFalse(slot.captured.isFocus)
    }

    @Test
    fun `break session carries the exact duration supplied`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 300, isFocus = false)

        assertEquals(300, slot.captured.durationSeconds)
    }

    @Test
    fun `short break session durationMinutes is 5 for 300 seconds`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 300, isFocus = false)

        assertEquals(5, slot.captured.durationMinutes)
    }

    @Test
    fun `long break session of 15 minutes is recorded correctly`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 900, isFocus = false)

        assertFalse(slot.captured.isFocus)
        assertEquals(900, slot.captured.durationSeconds)
        assertEquals(15, slot.captured.durationMinutes)
    }

    // ---- Timestamp handling -----------------------------------------------------------------

    @Test
    fun `timestampMillis defaults to current wall-clock time`() = runTest {
        val before = System.currentTimeMillis()
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1500, isFocus = true)

        val after = System.currentTimeMillis()
        assertTrue(
            "Expected timestamp in [$before, $after] but was ${slot.captured.timestampMillis}",
            slot.captured.timestampMillis in before..after,
        )
    }

    @Test
    fun `explicit timestampMillis overrides the default`() = runTest {
        val fixedTime = 1_700_000_000_000L
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(
            durationSeconds = 1500,
            isFocus = true,
            timestampMillis = fixedTime,
        )

        assertEquals(fixedTime, slot.captured.timestampMillis)
    }

    @Test
    fun `break session also respects explicit timestampMillis`() = runTest {
        val fixedTime = 1_600_000_000_000L
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(
            durationSeconds = 300,
            isFocus = false,
            timestampMillis = fixedTime,
        )

        assertEquals(fixedTime, slot.captured.timestampMillis)
    }

    // ---- Guard clause: invalid durations ----------------------------------------------------

    @Test
    fun `zero duration does not call insertSession`() = runTest {
        recordSessionUseCase(durationSeconds = 0, isFocus = true)

        coVerify(exactly = 0) { sessionRepository.insertSession(any()) }
    }

    @Test
    fun `negative duration does not call insertSession`() = runTest {
        recordSessionUseCase(durationSeconds = -1, isFocus = false)

        coVerify(exactly = 0) { sessionRepository.insertSession(any()) }
    }

    @Test
    fun `large negative duration does not call insertSession`() = runTest {
        recordSessionUseCase(durationSeconds = Int.MIN_VALUE, isFocus = true)

        coVerify(exactly = 0) { sessionRepository.insertSession(any()) }
    }

    // ---- Boundary: minimal valid duration ---------------------------------------------------

    @Test
    fun `one-second session is the minimum accepted and is recorded`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1, isFocus = true)

        coVerify(exactly = 1) { sessionRepository.insertSession(any()) }
        assertEquals(1, slot.captured.durationSeconds)
    }

    @Test
    fun `one-second session has durationMinutes of zero`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1, isFocus = false)

        assertEquals(0, slot.captured.durationMinutes)
    }

    // ---- Multiple sequential recordings -----------------------------------------------------

    @Test
    fun `multiple sessions are each forwarded to the repository`() = runTest {
        coEvery { sessionRepository.insertSession(any()) } returns Unit

        recordSessionUseCase(durationSeconds = 1500, isFocus = true)
        recordSessionUseCase(durationSeconds = 300, isFocus = false)
        recordSessionUseCase(durationSeconds = 1500, isFocus = true)

        coVerify(exactly = 3) { sessionRepository.insertSession(any()) }
    }

    @Test
    fun `alternating focus and break sessions are each recorded independently`() = runTest {
        val captured = mutableListOf<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(captured)) } returns Unit

        val durations = listOf(1500, 300, 1500, 300, 1500)
        durations.forEachIndexed { index, duration ->
            recordSessionUseCase(durationSeconds = duration, isFocus = index % 2 == 0)
        }

        assertEquals(5, captured.size)
        // Odd-indexed captures (indices 1, 3) are breaks
        assertFalse(captured[1].isFocus)
        assertFalse(captured[3].isFocus)
        // Even-indexed captures (indices 0, 2, 4) are focus
        assertTrue(captured[0].isFocus)
        assertTrue(captured[2].isFocus)
        assertTrue(captured[4].isFocus)
    }
}
