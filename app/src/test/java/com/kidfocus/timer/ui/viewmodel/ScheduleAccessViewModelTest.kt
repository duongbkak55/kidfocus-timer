package com.kidfocus.timer.ui.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.remote.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ScheduleAccessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val account = mockk<FirebaseAccountRepository>()
    private val ai = mockk<GeminiApi>()
    private val scheduleAi = mockk<ScheduleAiApi>()
    private val accounts = MutableStateFlow(CloudAccount(configured = true))
    private lateinit var vm: ScheduleAccessViewModel
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        every { account.account } returns accounts
        coEvery { ai.getConfig() } returns Result.success(AiConfig(scheduleEnabled = true, earlyAccessOpen = true))
        coEvery { scheduleAi.claimEarlyAccess() } just Runs
        vm = ScheduleAccessViewModel(account, ai, scheduleAi)
    }
    @After fun teardown() { vm.viewModelScope.cancel(); Dispatchers.resetMain() }
    @Test fun `claim only after sign-in and claim failure leaves config usable`() = runTest(dispatcher) {
        runCurrent()
        coVerify(exactly = 0) { scheduleAi.claimEarlyAccess() }
        coEvery { scheduleAi.claimEarlyAccess() } throws IllegalStateException("offline")
        accounts.value = CloudAccount(configured = true, userId = "parent")
        runCurrent()
        coVerify(exactly = 1) { scheduleAi.claimEarlyAccess() }
        assertTrue(vm.config.value.scheduleEnabled)
        assertTrue(vm.account.value.isSignedIn)
    }
    @Test fun `claim timeout does not end observer for next signed-in account`() = runTest(dispatcher) {
        runCurrent()
        coEvery { scheduleAi.claimEarlyAccess() } coAnswers { withTimeout(10) { delay(20) } }
        accounts.value = CloudAccount(configured = true, userId = "one")
        runCurrent(); advanceTimeBy(20); runCurrent()
        coEvery { scheduleAi.claimEarlyAccess() } just Runs
        accounts.value = CloudAccount(configured = true, userId = "two")
        runCurrent()
        coVerify(exactly = 2) { scheduleAi.claimEarlyAccess() }
        assertTrue(vm.config.value.scheduleEnabled)
    }
    @Test fun `closed enrollment and config errors leave schedule hidden by default`() = runTest(dispatcher) {
        coEvery { ai.getConfig() } returns Result.success(AiConfig(scheduleEnabled = false, earlyAccessOpen = false))
        runCurrent()
        accounts.value = CloudAccount(configured = true, userId = "one")
        runCurrent()
        coVerify(exactly = 0) { scheduleAi.claimEarlyAccess() }
        assertFalse(vm.config.value.scheduleEnabled)
        coEvery { ai.getConfig() } returns Result.failure(IllegalStateException("offline"))
        accounts.value = CloudAccount(configured = true, userId = "two")
        runCurrent()
        assertFalse(vm.config.value.scheduleEnabled)
    }
}
