package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.remote.GeminiApi
import com.kidfocus.timer.data.remote.AiConfig
import com.kidfocus.timer.domain.model.ChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiChatViewModel @Inject constructor(
    private val geminiApi: GeminiApi,
) : ViewModel() {

    private val _messages = MutableStateFlow(
        listOf(ChatMessage("Xin chào! 👋 Tôi là Cú học. Bạn đang học bài gì, cần hỏi gì cứ hỏi nhé!", isUser = false))
    )
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _config = MutableStateFlow(AiConfig())
    val config: StateFlow<AiConfig> = _config.asStateFlow()

    private val _selectedModelId = MutableStateFlow("openrouter/free")
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _configError = MutableStateFlow<String?>(null)
    val configError: StateFlow<String?> = _configError.asStateFlow()

    init {
        refreshConfig()
    }

    fun selectModel(modelId: String) {
        val model = _config.value.models.firstOrNull { it.id == modelId } ?: return
        if (model.premiumOnly && !_config.value.usage.premium) return
        _selectedModelId.value = modelId
    }

    fun refreshConfig() {
        viewModelScope.launch {
            geminiApi.getConfig().onSuccess { remote ->
                _config.value = remote
                _configError.value = null
                val selected = remote.models.firstOrNull { it.id == _selectedModelId.value }
                if (selected == null || (selected.premiumOnly && !remote.usage.premium)) {
                    _selectedModelId.value = remote.models.firstOrNull { !it.premiumOnly }?.id
                        ?: remote.models.firstOrNull()?.id.orEmpty()
                }
            }.onFailure {
                _configError.value = it.message
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return
        val userMsg = ChatMessage(content = text.trim(), isUser = true)
        val updatedHistory = _messages.value + userMsg
        _messages.value = updatedHistory
        _isLoading.value = true

        viewModelScope.launch {
            geminiApi.chat(updatedHistory, _selectedModelId.value).onSuccess { reply ->
                _messages.value = updatedHistory + ChatMessage(content = reply.text, isUser = false)
                _config.value = _config.value.copy(usage = reply.usage)
            }.onFailure { error ->
                android.util.Log.e("AiChat", "Error: ${error.message}")
                val errMsg = when {
                    error.containsCode("DAILY_LIMIT_REACHED") ->
                        "Bạn đã dùng hết lượt hỏi hôm nay. Hẹn gặp lại ngày mai nhé! 😊"
                    error.containsCode("PREMIUM_REQUIRED") ->
                        "Model này dành cho gói Premium. Hãy chọn model Miễn phí nhé!"
                    error.containsCode("PROVIDER_BUSY") ->
                        "Đang bận, thử lại sau vài giây nhé! 😊"
                    error.containsCode("FIREBASE_NOT_CONFIGURED") ->
                        "AI chưa được cấu hình trên bản cài này."
                    else -> "Có lỗi xảy ra, thử lại nhé! 😅"
                }
                _messages.value = updatedHistory + ChatMessage(content = errMsg, isUser = false)
            }
            _isLoading.value = false
        }
    }
}

private fun Throwable.containsCode(code: String): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current.message?.contains(code, ignoreCase = true) == true) return true
        current = current.cause
    }
    return false
}
