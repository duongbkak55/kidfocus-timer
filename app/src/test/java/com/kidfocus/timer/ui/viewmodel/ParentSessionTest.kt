package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.repository.SettingsRepository
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.domain.usecase.GetTimerSettingsUseCase
import com.kidfocus.timer.domain.usecase.SaveTimerSettingsUseCase
import io.mockk.every
import io.mockk.mockk
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
class ParentSessionTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var vm: SettingsViewModel
    private val values = MutableStateFlow(TimerSettings(pinHash = "hash"))

    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        val repository = mockk<SettingsRepository>()
        every { repository.settingsFlow } returns values
        every { repository.verifyPin(any(), "hash") } answers { firstArg<String>() == "2468" }
        vm = SettingsViewModel(GetTimerSettingsUseCase(repository), SaveTimerSettingsUseCase(repository), repository)
    }

    @After fun teardown() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }

    private fun TestScope.observeSettings() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.settings.collect() }
        runCurrent()
    }

    @Test fun `consuming PIN result does not clear parent session`() = runTest(dispatcher) {
        observeSettings(); vm.verifyPin("2468"); vm.resetPinVerification()
        assertFalse(vm.pinVerified.value)
        assertTrue(vm.parentUnlocked.value)
    }

    @Test fun `incorrect PIN keeps parent session locked`() = runTest(dispatcher) {
        observeSettings(); vm.verifyPin("0000")
        assertTrue(vm.pinError.value)
        assertFalse(vm.pinVerified.value)
        assertFalse(vm.parentUnlocked.value)
    }

    @Test fun `missing PIN never opens schedule parent session`() = runTest(dispatcher) {
        values.value = TimerSettings(); observeSettings(); vm.verifyPin("2468")
        assertTrue(vm.pinVerified.value) // Existing verification behavior is preserved.
        assertFalse(vm.parentUnlocked.value)
    }

    @Test fun `leaving parent area locks session and clears transient verification`() = runTest(dispatcher) {
        observeSettings(); vm.verifyPin("2468"); vm.lockParentSession()
        assertFalse(vm.parentUnlocked.value)
        assertFalse(vm.pinVerified.value)
    }

    @Test fun `process stop locks session through lifecycle observer`() = runTest(dispatcher) {
        observeSettings(); vm.verifyPin("2468")
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry.createUnsafe(this)
        }
        owner.lifecycle.addObserver(vm)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        assertFalse(vm.parentUnlocked.value)
    }

    @Test fun `five idle minutes locks but interaction extends the idle deadline`() = runTest(dispatcher) {
        observeSettings(); vm.verifyPin("2468"); runCurrent()
        val timeout = SettingsViewModel.PARENT_INACTIVITY_TIMEOUT_MILLIS
        advanceTimeBy(timeout - 1); runCurrent()
        assertTrue(vm.parentUnlocked.value)
        vm.recordParentInteraction(); runCurrent()
        advanceTimeBy(1); runCurrent()
        assertTrue(vm.parentUnlocked.value)
        advanceTimeBy(timeout - 1); runCurrent()
        assertFalse(vm.parentUnlocked.value)
    }
}
