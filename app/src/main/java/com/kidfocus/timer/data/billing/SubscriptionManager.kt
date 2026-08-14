package com.kidfocus.timer.data.billing

import android.app.Activity
import android.content.Context
import com.kidfocus.timer.BuildConfig
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.StoreTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class StorePlan(
    val identifier: String,
    val title: String,
    val price: String,
    val isAnnual: Boolean,
)

data class SubscriptionState(
    val configured: Boolean = false,
    val loading: Boolean = false,
    val premium: Boolean = false,
    val plans: List<StorePlan> = emptyList(),
    val message: String? = null,
)

@Singleton
class SubscriptionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: FirebaseAccountRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val packages = mutableMapOf<String, Package>()
    private val _state = MutableStateFlow(SubscriptionState())
    val state: StateFlow<SubscriptionState> = _state.asStateFlow()
    private var started = false
    private var identifiedUserId: String? = null

    fun start() {
        if (started) return
        started = true
        val apiKey = BuildConfig.REVENUECAT_ANDROID_API_KEY
        if (apiKey.isBlank()) {
            _state.value = SubscriptionState(
                configured = false,
                message = "RevenueCat chưa được cấu hình cho bản cài này",
            )
            return
        }
        if (!Purchases.isConfigured) {
            if (BuildConfig.DEBUG) Purchases.logLevel = LogLevel.DEBUG
            Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())
        }
        _state.value = _state.value.copy(configured = true, loading = true)
        observeAccount()
        refresh()
    }

    fun refresh() {
        if (!Purchases.isConfigured) return
        _state.value = _state.value.copy(loading = true, message = null)
        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                updateOfferings(offerings)
                refreshCustomerInfo()
            }

            override fun onError(error: PurchasesError) {
                _state.value = _state.value.copy(loading = false, message = error.message)
            }
        })
    }

    fun purchase(activity: Activity, planId: String, onFinished: (Boolean) -> Unit) {
        val uid = accountRepository.account.value.userId
        if (uid == null) {
            _state.value = _state.value.copy(message = "Đăng nhập trước khi mua để đồng bộ gói Premium")
            onFinished(false)
            return
        }
        val selected = packages[planId]
        if (selected == null) {
            _state.value = _state.value.copy(message = "Không tìm thấy gói trên Google Play")
            onFinished(false)
            return
        }
        if (identifiedUserId != uid) {
            _state.value = _state.value.copy(loading = true, message = null)
            identify(uid) { success ->
                if (success) purchasePackage(activity, selected, onFinished)
                else onFinished(false)
            }
            return
        }
        purchasePackage(activity, selected, onFinished)
    }

    private fun purchasePackage(activity: Activity, selected: Package, onFinished: (Boolean) -> Unit) {
        _state.value = _state.value.copy(loading = true, message = null)
        Purchases.sharedInstance.purchase(
            PurchaseParams.Builder(activity, selected).build(),
            object : PurchaseCallback {
                override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                    updateCustomerInfo(customerInfo, "Đã kích hoạt Premium")
                    onFinished(isPremium(customerInfo))
                }

                override fun onError(error: PurchasesError, userCancelled: Boolean) {
                    _state.value = _state.value.copy(
                        loading = false,
                        message = if (userCancelled) null else error.message,
                    )
                    onFinished(false)
                }
            },
        )
    }

    fun restore() {
        if (!Purchases.isConfigured) return
        if (accountRepository.account.value.userId == null) {
            _state.value = _state.value.copy(message = "Hãy đăng nhập trước khi khôi phục giao dịch")
            return
        }
        _state.value = _state.value.copy(loading = true, message = null)
        Purchases.sharedInstance.restorePurchases(customerInfoCallback("Đã kiểm tra giao dịch đã mua"))
    }

    private fun observeAccount() {
        scope.launch {
            accountRepository.account.collectLatest { account ->
                val uid = account.userId
                if (uid != null && uid != identifiedUserId) {
                    identify(uid)
                } else if (uid == null && identifiedUserId != null) {
                    identifiedUserId = null
                    Purchases.sharedInstance.logOut(customerInfoCallback())
                }
            }
        }
    }

    private fun identify(uid: String, onFinished: (Boolean) -> Unit = {}) {
        Purchases.sharedInstance.logIn(uid, object : LogInCallback {
            override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                identifiedUserId = uid
                updateCustomerInfo(customerInfo)
                onFinished(true)
            }

            override fun onError(error: PurchasesError) {
                _state.value = _state.value.copy(loading = false, message = error.message)
                onFinished(false)
            }
        })
    }

    private fun refreshCustomerInfo() {
        Purchases.sharedInstance.getCustomerInfo(customerInfoCallback())
    }

    private fun customerInfoCallback(successMessage: String? = null) =
        object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                updateCustomerInfo(customerInfo, successMessage)
            }

            override fun onError(error: PurchasesError) {
                _state.value = _state.value.copy(loading = false, message = error.message)
            }
        }

    private fun updateOfferings(offerings: Offerings) {
        val offering = offerings.current
        val available = offering?.availablePackages.orEmpty()
        packages.clear()
        available.forEach { packages[it.identifier] = it }
        val annualId = offering?.annual?.identifier
        _state.value = _state.value.copy(
            plans = available.map {
                StorePlan(
                    identifier = it.identifier,
                    title = it.product.title,
                    price = it.product.price.formatted,
                    isAnnual = it.identifier == annualId || it.identifier.contains("annual", true),
                )
            }.sortedByDescending { it.isAnnual },
        )
    }

    private fun updateCustomerInfo(customerInfo: CustomerInfo, message: String? = null) {
        _state.value = _state.value.copy(
            loading = false,
            premium = isPremium(customerInfo),
            message = message,
        )
    }

    private fun isPremium(customerInfo: CustomerInfo): Boolean =
        customerInfo.entitlements.active["premium"]?.isActive == true
}
