package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.alarm.AlarmScheduler
import com.kidfocus.timer.alarm.ExactAlarmPermission
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.domain.model.ScheduledTask
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduleAlarmPermissionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val permission = mockk<ExactAlarmPermission>()
    private val scheduler = mockk<AlarmScheduler>(relaxed = true)
    private fun task(id: Long, profile: String = "first", enabled: Boolean = true) = ScheduledTask(id, TaskType.CUSTOM, "Reading", "", 18, 0, setOf(2), 30, 5, enabled = enabled, childProfileId = profile)
    private val tasks = MutableStateFlow(listOf(task(1), task(2, "second")))
    private val dismissed = MutableStateFlow(false)
    private val repository = mockk<ScheduledTaskRepository>()
    private val settings = mockk<SettingsDataStore>()
    private lateinit var vm: ScheduleAlarmPermissionViewModel
    private val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry.createUnsafe(this) }

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { permission.isAllowed() } returns false
        every { repository.allTasks } returns tasks
        coEvery { repository.getEnabledTasks() } answers { tasks.value.filter { it.enabled } }
        every { settings.scheduleAlarmReminderDismissed } returns dismissed
        coEvery { settings.dismissScheduleAlarmReminder() } coAnswers { dismissed.value = true }
        vm = ScheduleAlarmPermissionViewModel(permission, repository, scheduler, settings)
    }

    @After fun teardown() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }

    private fun TestScope.observe() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.reminderVisible.collect() }
        runCurrent()
    }

    @Test fun `prompt needs enabled task and missing permission`() = runTest(dispatcher) {
        observe(); assertTrue(vm.reminderVisible.value)
        tasks.value = tasks.value.map { it.copy(enabled = false) }; runCurrent()
        assertFalse(vm.reminderVisible.value)
        tasks.value = emptyList(); runCurrent(); assertFalse(vm.reminderVisible.value)
    }

    @Test fun `actual lifecycle resume after permission grant reschedules enabled tasks of all profiles`() = runTest(dispatcher) {
        observe()
        tasks.value = tasks.value + task(3, enabled = false)
        owner.lifecycle.addObserver(vm)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME); runCurrent()
        verify(exactly = 0) { scheduler.scheduleAll(any()) }
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        every { permission.isAllowed() } returns true
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME); runCurrent()
        assertFalse(vm.reminderVisible.value)
        verify(exactly = 1) { scheduler.scheduleAll(tasks.value.filter { it.enabled }) }
    }

    @Test fun `dismissal is persisted and hides prompt after a new viewmodel`() = runTest(dispatcher) {
        observe(); vm.dismissReminder(); runCurrent()
        coVerify(exactly = 1) { settings.dismissScheduleAlarmReminder() }
        assertFalse(vm.reminderVisible.value)
        vm.viewModelScope.cancel()
        vm = ScheduleAlarmPermissionViewModel(permission, repository, scheduler, settings)
        observe(); assertFalse(vm.reminderVisible.value)
        every { permission.isAllowed() } returns true
        vm.onResume(owner); runCurrent()
        verify(exactly = 1) { scheduler.scheduleAll(tasks.value) } // Dismissing never disables rescheduling.
    }

    @Test fun `opening settings safely reports unavailable destination`() = runTest(dispatcher) {
        every { permission.openSettings() } returns false
        vm.requestPermission(); assertTrue(vm.settingsUnavailable.value)
        every { permission.openSettings() } returns true
        vm.requestPermission(); assertFalse(vm.settingsUnavailable.value)
    }

    @Test fun `ordinary resume with unchanged grant keeps existing alarm times`() = runTest(dispatcher) {
        every { permission.isAllowed() } returns true
        vm.onResume(owner); runCurrent()
        vm.onResume(owner); runCurrent()
        verify(exactly = 1) { scheduler.scheduleAll(tasks.value) }
        every { permission.isAllowed() } returns false
        vm.onResume(owner); runCurrent()
        every { permission.isAllowed() } returns true
        vm.onResume(owner); runCurrent()
        verify(exactly = 2) { scheduler.scheduleAll(tasks.value) }
    }
}
