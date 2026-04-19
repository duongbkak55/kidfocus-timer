package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.remote.GeminiApi
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

    fun sendMessage(text: String) {
        if (text.isBlank() || _isLoading.value) return
        val userMsg = ChatMessage(content = text.trim(), isUser = true)
        val updatedHistory = _messages.value + userMsg
        _messages.value = updatedHistory
        _isLoading.value = true

        viewModelScope.launch {
            geminiApi.chat(updatedHistory).onSuccess { reply ->
                _messages.value = updatedHistory + ChatMessage(content = reply, isUser = false)
            }.onFailure { error ->
                android.util.Log.e("AiChat", "Error: ${error.message}")
                val errMsg = when {
                    error.message?.contains("API_ERROR_429") == true ->
                        "Đang bận, thử lại sau vài giây nhé! 😊"
                    error.message?.contains("API_ERROR_4") == true ->
                        "Có lỗi kết nối (${error.message}), thử lại nhé!"
                    else -> "Có lỗi xảy ra, thử lại nhé! 😅"
                }
                _messages.value = updatedHistory + ChatMessage(content = errMsg, isUser = false)
            }
            _isLoading.value = false
        }
    }
}
