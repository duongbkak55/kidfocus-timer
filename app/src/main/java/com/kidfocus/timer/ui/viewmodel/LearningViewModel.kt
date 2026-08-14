package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.billing.SubscriptionManager
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.data.repository.LearningProgress
import com.kidfocus.timer.data.repository.LearningRepository
import com.kidfocus.timer.data.repository.LearningResult
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.data.repository.ChildProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LearningNativeConfig(
    val ageBand: String = TimerSettings.DEFAULT_LEARNING_AGE_BAND,
    val soundEnabled: Boolean = true,
    val premium: Boolean = false,
    val signedIn: Boolean = false,
)

@HiltViewModel
class LearningViewModel @Inject constructor(
    private val repository: LearningRepository,
    private val settingsDataStore: SettingsDataStore,
    subscriptionManager: SubscriptionManager,
    accountRepository: FirebaseAccountRepository,
    private val childProfileRepository: ChildProfileRepository,
) : ViewModel() {
    val progress: StateFlow<List<LearningProgress>> = repository.progress.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    val config: StateFlow<LearningNativeConfig> = kotlinx.coroutines.flow.combine(
        settingsDataStore.settingsFlow,
        subscriptionManager.state,
        accountRepository.account,
        childProfileRepository.activeProfile,
    ) { settings, subscription, account, profile ->
        LearningNativeConfig(
            ageBand = profile.ageBand,
            soundEnabled = settings.soundEnabled,
            premium = subscription.premium,
            signedIn = account.isSignedIn,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        LearningNativeConfig(),
    )

    val totalCompleted: StateFlow<Int> = progress.map { rows ->
        rows.sumOf { it.completedAttempts }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun recordResult(result: LearningResult) {
        viewModelScope.launch { repository.record(result) }
    }

    fun setAgeBand(ageBand: String) {
        if (ageBand !in VALID_AGE_BANDS || ageBand == config.value.ageBand) return
        viewModelScope.launch { childProfileRepository.updateActiveAgeBand(ageBand) }
    }

    fun syncNow() = repository.syncNow()

    private companion object {
        val VALID_AGE_BANDS = setOf("2-3", "4-5", "l1", "l2", "l3")
    }
}
