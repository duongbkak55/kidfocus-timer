package com.kidfocus.timer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.data.remote.AiConfig
import com.kidfocus.timer.data.remote.AiUsage
import com.kidfocus.timer.data.remote.ScheduleLogReply
import com.kidfocus.timer.data.remote.ScheduleLogger
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.data.repository.DayLogRepository
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository
import com.kidfocus.timer.domain.daylog.DayLogCandidate
import com.kidfocus.timer.domain.daylog.DayLogCategory
import com.kidfocus.timer.domain.daylog.DayLogPreview
import com.kidfocus.timer.domain.daylog.DayLogSource
import com.kidfocus.timer.domain.model.TimerSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ChildQuickLogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val profiles = mockk<ChildProfileRepository>()
    private val tasks = mockk<ScheduledTaskRepository>()
    private val anchors = mockk<ScheduleAnchorsRepository>()
    private val settings = mockk<SettingsDataStore>()
    private val logger = mockk<ScheduleLogger>()
    private val logs = mockk<DayLogRepository>(relaxed = true)
    private val account = mockk<FirebaseAccountRepository>()
    private val children = MutableStateFlow(listOf(ChildProfileEntity.default()))
    private val preferences = MutableStateFlow(TimerSettings())
    private val signedIn = MutableStateFlow(CloudAccount(true, "test-owner"))
    private lateinit var vm: ChildQuickLogViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { profiles.profiles } returns children
        every { tasks.allTasks } returns MutableStateFlow(emptyList())
        every { anchors.all } returns MutableStateFlow(emptyMap())
        every { settings.settingsFlow } returns preferences
        every { account.account } returns signedIn
        coEvery { logs.activeProfileId() } returns "default"
        vm = ChildQuickLogViewModel(profiles, tasks, anchors, settings, logger, logs, account)
    }

    @After fun tearDown() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }

    @Test fun savesOnlyGroundedHighConfidencePastEntriesAndUndoIsTheSameBatch() = runTest(dispatcher) {
        runCurrent()
        val today = LocalDate.now()
        coEvery { logger.log(any()) } returns ScheduleLogReply(DayLogPreview(listOf(
            DayLogCandidate(today, null, "Went to bed", DayLogCategory.SLEEP, 0, null, .9),
            DayLogCandidate(today, null, "Unclear", DayLogCategory.OTHER, 0, null, .59),
            DayLogCandidate(today.plusDays(1), null, "Tomorrow", DayLogCategory.OTHER, 0, null, .95),
            DayLogCandidate(today, "missing-ref", "Ungrounded", DayLogCategory.STUDY, 0, null, .99),
        ), listOf("What time?")), AiUsage(9, 9, false))
        vm.editText("Con đi ngủ lúc 0 giờ")
        vm.send(AiConfig(scheduleEnabled = true))
        runCurrent()
        assertEquals(1, vm.state.value.saved.size)
        assertEquals(listOf("Unclear", "Tomorrow", "Ungrounded"), vm.state.value.unclear)
        assertEquals("What time?", vm.state.value.question)
        coVerify(exactly = 1) { logs.addChildBatch(match { it.size == 1 && it.single().source == DayLogSource.CHILD }) }
        val batch = vm.state.value.undoBatch
        vm.undo()
        runCurrent()
        coVerify(exactly = 1) { logs.undoChildBatch(batch) }
        assertTrue(vm.state.value.saved.isEmpty())
    }

    @Test fun disabledLockEntryAndMissingActiveProfileDoNotCallAi() = runTest(dispatcher) {
        preferences.value = TimerSettings(allowLockScreenChildLog = false)
        runCurrent()
        vm.editText("Con đi ngủ lúc 0 giờ")
        vm.send(AiConfig(scheduleEnabled = true), fromLockScreen = true)
        assertEquals(ChildLogError.UNAVAILABLE, vm.state.value.error)
        preferences.value = TimerSettings(activeChildProfileId = "missing")
        runCurrent()
        vm.send(AiConfig(scheduleEnabled = true))
        assertEquals(ChildLogError.UNAVAILABLE, vm.state.value.error)
        coVerify(exactly = 0) { logger.log(any()) }
    }

    @Test fun inputIsBoundedAndAccountChangeDropsPendingResult() = runTest(dispatcher) {
        runCurrent()
        vm.editText("x".repeat(301))
        assertEquals(300, vm.state.value.text.length)
        val pending = kotlinx.coroutines.CompletableDeferred<ScheduleLogReply>()
        coEvery { logger.log(any()) } coAnswers { pending.await() }
        vm.send(AiConfig(scheduleEnabled = true))
        runCurrent()
        assertTrue(vm.state.value.busy)
        signedIn.value = CloudAccount(true, "different-owner")
        pending.complete(ScheduleLogReply(DayLogPreview(emptyList(), emptyList()), AiUsage(9, 9, false)))
        runCurrent()
        assertFalse(vm.state.value.busy)
        assertEquals(ChildLogError.PROFILE_CHANGED, vm.state.value.error)
        coVerify(exactly = 0) { logs.addChildBatch(any()) }
    }
}
