package com.kidfocus.timer.data.schedule

import android.app.Application
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.domain.schedule.*
import io.mockk.*
import java.time.DayOfWeek.*
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class SchedulePlansRepositoryTest {
    private val today = LocalDate.of(2026, 9, 28)
    private val anchors = ScheduleAnchors(bed = mapOf(MONDAY to LocalTime.of(22, 30)), wake = mapOf(MONDAY to LocalTime.of(6, 0)))
    private val plan = SchedulePlan.create(PlanKind.BED, setOf(MONDAY), LocalTime.of(21, 30), anchors, today)
    @Test fun `real DataStore persists plan per profile cancels manual edits and rejects stale synced plans`() = runBlocking {
        val settings = SettingsDataStore(RuntimeEnvironment.getApplication())
        val repository = SchedulePlansRepository(settings)
        val hours = ScheduleAnchorsRepository(settings)
        hours.save("plan-a", anchors); hours.save("plan-b", anchors)
        repository.save("plan-a", plan); repository.save("plan-b", plan.copy(kind = PlanKind.WAKE, initial = anchors.wake, target = LocalTime.of(7, 0)))
        assertEquals(plan, repository.get("plan-a"))
        // A commute-only edit does not cancel the sleep plan.
        hours.save("plan-a", anchors.copy(commuteMinutes = 5)); assertEquals(plan, repository.get("plan-a"))
        hours.save("plan-a", anchors.copy(bed = mapOf(MONDAY to LocalTime.of(22, 0))))
        assertNull(repository.get("plan-a")); assertNotNull(repository.get("plan-b"))
        assertEquals("null", settings.schedulePlansJson.first()["plan-a"])
        repository.applyRemote(mapOf("plan-a" to SchedulePlanJson.toMap(plan)))
        assertNull(repository.get("plan-a"))
        repository.applyRemote(null); assertNotNull(repository.get("plan-b"))
        repository.applyRemote(mapOf("plan-b" to null)); assertNull(repository.get("plan-b"))
        repository.applyRemote(mapOf("broken" to mapOf("kind" to "BAD"))); assertNull(repository.get("broken"))
        // Simulate a successful step, followed by Undo: progression matches, Undo cancels.
        hours.save("plan-a", anchors)
        val next = plan.advanced(today)!!
        hours.save("plan-a", anchors.copy(bed = plan.timesAt(1)))
        repository.save("plan-a", next); assertEquals(next, repository.get("plan-a"))
        hours.save("plan-a", anchors); assertNull(repository.get("plan-a"))
        repository.save("plan-a", null); repository.save("plan-b", null)
    }
    @Test fun `absent remote key profile or malformed JSON never deletes a local plan`() = runBlocking {
        val rows = MutableStateFlow(mapOf("p" to SchedulePlanJson.encode(plan)))
        val settings = mockk<SettingsDataStore>()
        every { settings.schedulePlansJson } returns rows
        coEvery { settings.saveSchedulePlansJson(any()) } coAnswers { rows.value = rows.value + firstArg<Map<String, String>>() }
        val repository = SchedulePlansRepository(settings)
        repository.applyRemote(null); assertEquals(plan, repository.get("p"))
        repository.applyRemote(emptyMap<String, Any>()); assertEquals(plan, repository.get("p"))
        repository.applyRemote(mapOf("p" to mapOf("invalid" to "data"))); assertEquals(plan, repository.get("p"))
        repository.applyRemote(mapOf("other" to SchedulePlanJson.toMap(plan))); assertEquals(plan, repository.get("p")); assertEquals(plan, repository.get("other"))
        repository.applyRemote(mapOf("p" to null)); assertNull(repository.get("p")); assertNotNull(repository.get("other"))
    }
}
