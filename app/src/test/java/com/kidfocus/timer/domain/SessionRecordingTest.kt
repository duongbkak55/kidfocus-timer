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
 * Uses MockK to verify that the repository receives correctly constructed [SessionEntity]
 * instances, and that edge cases (zero duration, negative duration) are handled safely.
 */
class SessionRecordingTest {

    private lateinit var sessionRepository: SessionRepository
    private lateinit var recordSessionUseCase: RecordSessionUseCase

    @Before
    fun setUp() {
        sessionRepository = mockk(relaxed = true)
        recordSessionUseCase = RecordSessionUseCase(sessionRepository)
    }

    // ---- Happy paths ------------------------------------------------------------------------

    @Test
    fun `records a focus session with correct duration`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1500, isFocus = true)

        coVerify(exactly = 1) { sessionRepository.insertSession(any()) }
        assertEquals(1500, slot.captured.durationSeconds)
        assertTrue(slot.captured.isFocus)
    }

    @Test
    fun `records a break session with correct duration`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 300, isFocus = false)

        coVerify(exactly = 1) { sessionRepository.insertSession(any()) }
        assertEquals(300, slot.captured.durationSeconds)
        assertFalse(slot.captured.isFocus)
    }

    @Test
    fun `sets timestampMillis to current time when not provided`() = runTest {
        val before = System.currentTimeMillis()
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 600, isFocus = true)

        val after = System.currentTimeMillis()
        assertTrue(slot.captured.timestampMillis in before..after)
    }

    @Test
    fun `uses provided timestampMillis when given`() = runTest {
        val fixedTime = 1_700_000_000_000L
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(
            durationSeconds = 900,
            isFocus = true,
            timestampMillis = fixedTime,
        )

        assertEquals(fixedTime, slot.captured.timestampMillis)
    }

    // ---- Edge cases -------------------------------------------------------------------------

    @Test
    fun `does not record session when duration is zero`() = runTest {
        recordSessionUseCase(durationSeconds = 0, isFocus = true)

        coVerify(exactly = 0) { sessionRepository.insertSession(any()) }
    }

    @Test
    fun `does not record session when duration is negative`() = runTest {
        recordSessionUseCase(durationSeconds = -1, isFocus = false)

        coVerify(exactly = 0) { sessionRepository.insertSession(any()) }
    }

    @Test
    fun `records minimal 1-second session`() = runTest {
        val slot = slot<SessionEntity>()
        coEvery { sessionRepository.insertSession(capture(slot)) } returns Unit

        recordSessionUseCase(durationSeconds = 1, isFocus = true)

        coVerify(exactly = 1) { sessionRepository.insertSession(any()) }
        assertEquals(1, slot.captured.durationSeconds)
    }

    // ---- SessionEntity computed properties --------------------------------------------------

    @Test
    fun `durationMinutes rounds down to whole minutes`() {
        val entity = SessionEntity(
            durationSeconds = 1510, // 25 minutes 10 seconds
            isFocus = true,
            timestampMillis = 0L,
        )
        assertEquals(25, entity.durationMinutes)
    }

    @Test
    fun `durationMinutes is zero for sub-minute sessions`() {
        val entity = SessionEntity(
            durationSeconds = 59,
            isFocus = false,
            timestampMillis = 0L,
        )
        assertEquals(0, entity.durationMinutes)
    }

    @Test
    fun `durationMinutes is zero for zero-second session`() {
        val entity = SessionEntity(
            durationSeconds = 0,
            isFocus = true,
            timestampMillis = 0L,
        )
        assertEquals(0, entity.durationMinutes)
    }

    @Test
    fun `multiple sequential recordings all persist`() = runTest {
        coEvery { sessionRepository.insertSession(any()) } returns Unit

        repeat(5) { i ->
            recordSessionUseCase(durationSeconds = (i + 1) * 300, isFocus = i % 2 == 0)
        }

        coVerify(exactly = 5) { sessionRepository.insertSession(any()) }
    }
}
