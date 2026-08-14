package com.kidfocus.timer.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.kidfocus.timer.data.billing.SubscriptionManager
import com.kidfocus.timer.data.billing.SubscriptionState
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    private val subscriptionManager: SubscriptionManager,
    accountRepository: FirebaseAccountRepository,
) : ViewModel() {
    val state: StateFlow<SubscriptionState> = subscriptionManager.state
    val account: StateFlow<CloudAccount> = accountRepository.account

    fun refresh() = subscriptionManager.refresh()
    fun restore() = subscriptionManager.restore()
    fun purchase(activity: Activity, planId: String, onFinished: (Boolean) -> Unit = {}) =
        subscriptionManager.purchase(activity, planId, onFinished)
}
