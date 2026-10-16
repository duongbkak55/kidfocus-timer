package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.billing.SubscriptionManager
import com.kidfocus.timer.data.billing.SubscriptionState
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.data.repository.LearningRepository
import com.kidfocus.timer.domain.model.TimerSettings
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearningAgeBandViewModelTest {
    @Test fun `setAgeBand accepts five to six but rejects unknown`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val learning = mockk<LearningRepository>()
        val settings = mockk<SettingsDataStore>()
        val subscription = mockk<SubscriptionManager>()
        val account = mockk<FirebaseAccountRepository>()
        val profiles = mockk<ChildProfileRepository>()
        every { learning.progress } returns MutableStateFlow(emptyList())
        every { settings.settingsFlow } returns MutableStateFlow(TimerSettings())
        every { subscription.state } returns MutableStateFlow(SubscriptionState())
        every { account.account } returns MutableStateFlow(CloudAccount(false))
        every { profiles.activeProfile } returns MutableStateFlow(ChildProfileEntity.default())
        coEvery { profiles.updateActiveAgeBand(any()) } returns Unit
        val vm = LearningViewModel(learning, settings, subscription, account, profiles)
        try {
            vm.setAgeBand("5-6")
            vm.setAgeBand("unknown")
            runCurrent()
            coVerify(exactly = 1) { profiles.updateActiveAgeBand("5-6") }
            assertEquals("4-5", vm.config.value.ageBand)
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
