package com.kidfocus.timer.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.CloudSyncManager
import com.kidfocus.timer.data.cloud.CloudSyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val cloudSyncManager: CloudSyncManager,
) : ViewModel() {
    val account: StateFlow<CloudAccount> = cloudSyncManager.account
    val syncStatus: StateFlow<CloudSyncStatus> = cloudSyncManager.status

    private val _formMessage = MutableStateFlow<String?>(null)
    val formMessage: StateFlow<String?> = _formMessage.asStateFlow()
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    fun signIn(email: String, password: String) = runAccountAction {
        requireValid(email, password)
        cloudSyncManager.signIn(email, password)
        _formMessage.value = "Đã đăng nhập. Dữ liệu đang được đồng bộ."
    }

    fun createAccount(email: String, password: String) = runAccountAction {
        requireValid(email, password)
        cloudSyncManager.createAccount(email, password)
        _formMessage.value = "Đã tạo tài khoản và bắt đầu sao lưu."
    }

    fun signInWithGoogle(activityContext: Context) = runAccountAction {
        cloudSyncManager.signInWithGoogle(activityContext)
        _formMessage.value = "Đã đăng nhập Google. Dữ liệu đang được đồng bộ."
    }

    fun resetPassword(email: String) = runAccountAction {
        require(email.contains('@')) { "Nhập email hợp lệ trước" }
        cloudSyncManager.sendPasswordReset(email)
        _formMessage.value = "Đã gửi email đặt lại mật khẩu."
    }

    fun signOut() {
        runAccountAction {
            cloudSyncManager.signOut()
            _formMessage.value = "Đã đăng xuất. Dữ liệu trên máy vẫn được giữ nguyên."
        }
    }

    fun deleteAccount() {
        runAccountAction {
            cloudSyncManager.deleteAccount()
            _formMessage.value = "Tài khoản và dữ liệu cloud đã được xóa. Dữ liệu offline vẫn còn trên thiết bị này."
        }
    }

    fun syncNow() {
        cloudSyncManager.syncNow()
        _formMessage.value = null
    }

    private fun runAccountAction(block: suspend () -> Unit) {
        if (_working.value) return
        viewModelScope.launch {
            _working.value = true
            _formMessage.value = null
            runCatching { block() }.onFailure {
                _formMessage.value = it.toFriendlyMessage()
            }
            _working.value = false
        }
    }

    private fun requireValid(email: String, password: String) {
        require(email.contains('@')) { "Email chưa hợp lệ" }
        require(password.length >= 6) { "Mật khẩu cần ít nhất 6 ký tự" }
    }

    private fun Throwable.toFriendlyMessage(): String {
        val raw = message.orEmpty()
        return when {
            raw.contains("email address is already", ignoreCase = true) -> "Email này đã có tài khoản"
            raw.contains("password is invalid", ignoreCase = true) ||
                raw.contains("credential is incorrect", ignoreCase = true) -> "Email hoặc mật khẩu chưa đúng"
            raw.contains("network", ignoreCase = true) -> "Không kết nối được mạng"
            raw.contains("no credentials", ignoreCase = true) -> "Thiết bị chưa có tài khoản Google khả dụng"
            raw.contains("account reauth failed", ignoreCase = true) ->
                "Google chưa xác thực được tài khoản. Hãy thử lại hoặc chọn tài khoản khác."
            raw.contains("canceled", ignoreCase = true) ||
                raw.contains("cancelled", ignoreCase = true) -> "Đã hủy đăng nhập Google"
            raw.isNotBlank() -> raw
            else -> "Không thể thực hiện lúc này"
        }
    }
}
