package com.kidfocus.timer.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.database.ChildProfileDao
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.database.LearningAttemptDao
import com.kidfocus.timer.data.database.LearningAttemptEntity
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.model.TimerSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AgeBandRepositoryTest {
    @Test fun `profile save and active update retain five to six and reject unknown bands`() = runTest {
        val rows = MutableStateFlow(listOf(ChildProfileEntity.default()))
        val dao = mockk<ChildProfileDao>()
        val settings = mockk<SettingsDataStore>()
        every { dao.observeActive() } returns rows
        every { settings.settingsFlow } returns MutableStateFlow(TimerSettings())
        coEvery { dao.getById(any()) } coAnswers { rows.value.firstOrNull { it.id == firstArg() } }
        coEvery { dao.upsert(any()) } coAnswers {
            val saved = firstArg<ChildProfileEntity>()
            rows.value = rows.value.filterNot { it.id == saved.id } + saved
        }
        val repository = ChildProfileRepository(dao, settings)
        val saved = repository.save("default", "Bé Na", "🐣", "5-6")
        assertEquals("5-6", saved.ageBand)
        assertEquals("5-6", rows.value.single().ageBand)
        repository.updateActiveAgeBand("4-5")
        repository.updateActiveAgeBand("5-6")
        assertEquals("5-6", rows.value.single().ageBand)
        repository.updateActiveAgeBand("unknown")
        assertEquals("5-6", rows.value.single().ageBand)
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { repository.save("default", "Bé Na", "🐣", "unknown") }
        }
    }

    @Test fun `learning attempt stores five to six instead of coercing it`() = runTest {
        val dao = mockk<LearningAttemptDao>()
        val account = mockk<FirebaseAccountRepository>()
        val profiles = mockk<ChildProfileRepository>()
        val attempt = slot<LearningAttemptEntity>()
        every { account.account } returns MutableStateFlow(CloudAccount(false))
        every { profiles.activeProfileId } returns MutableStateFlow("default")
        coEvery { profiles.currentProfileId() } returns "default"
        coEvery { dao.upsert(capture(attempt)) } returns Unit
        val repository = LearningRepository(dao, account, profiles)
        repository.record(LearningResult("tuduy", "5-6", 6, 8, 40_000, true))
        assertEquals("5-6", attempt.captured.ageBand)
        assertEquals("default", attempt.captured.childProfileId)
        repository.record(LearningResult("tuduy", "unknown", 6, 8, 40_000, true))
        assertEquals("4-5", attempt.captured.ageBand)
        coVerify(exactly = 2) { dao.upsert(any()) }
    }

    @Test fun `learning attempt cloud download keeps five to six and unknown fallback`() {
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.id } returns "attempt-1"
        every { snapshot.getString("gameId") } returns "tuduy"
        every { snapshot.getString("ageBand") } returnsMany listOf("5-6", "unknown")
        every { snapshot.getString("childProfileId") } returns "default"
        every { snapshot.get("totalQuestions") } returns 8
        every { snapshot.get("score") } returns 6
        every { snapshot.get("durationMillis") } returns 40_000L
        every { snapshot.get("createdAtMillis") } returns 123L
        every { snapshot.getBoolean("completed") } returns true
        assertEquals("5-6", snapshot.toLearningAttempt("owner")?.ageBand)
        assertEquals("4-5", snapshot.toLearningAttempt("owner")?.ageBand)
    }
}
