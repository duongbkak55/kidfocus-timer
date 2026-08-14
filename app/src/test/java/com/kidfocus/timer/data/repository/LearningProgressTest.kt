package com.kidfocus.timer.data.repository

import com.kidfocus.timer.data.database.LearningAttemptEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LearningProgressTest {
    @Test
    fun `summaries combine attempts and keep most recent game first`() {
        val rows = listOf(
            attempt(id = "1", game = "add", score = 3, total = 5, at = 100L),
            attempt(id = "2", game = "add", score = 5, total = 5, at = 300L),
            attempt(id = "3", game = "story", score = 2, total = 3, at = 500L),
        )

        val result = summarizeLearningAttempts(rows)

        assertEquals(listOf("story", "add"), result.map { it.gameId })
        val addition = result.last()
        assertEquals(2, addition.attempts)
        assertEquals(8, addition.correctAnswers)
        assertEquals(10, addition.totalQuestions)
        assertEquals(100, addition.bestPercent)
        assertEquals(40_000L, addition.totalDurationMillis)
    }

    private fun attempt(
        id: String,
        game: String,
        score: Int,
        total: Int,
        at: Long,
    ) = LearningAttemptEntity(
        id = id,
        ownerUid = null,
        gameId = game,
        ageBand = "l1",
        score = score,
        totalQuestions = total,
        durationMillis = 20_000L,
        completed = true,
        createdAtMillis = at,
        synced = false,
    )
}
