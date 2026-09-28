package com.kidfocus.timer.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.remote.AiConfig
import com.kidfocus.timer.data.remote.AiUsage
import com.kidfocus.timer.data.remote.GeminiApi
import com.kidfocus.timer.data.remote.ScheduleAiApi
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class ScheduleAccessViewModel @Inject constructor(
    private val accountRepository: FirebaseAccountRepository,
    private val ai: GeminiApi,
    private val scheduleAi: ScheduleAiApi,
) : ViewModel() {
    val account = accountRepository.account
    private val _config = MutableStateFlow(AiConfig())
    val config = _config.asStateFlow()
    val signingIn = MutableStateFlow(false)
    val signInFailed = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            account.map { it.userId }.distinctUntilChanged().collectLatest { uid ->
                _config.value = AiConfig()
                refreshFor(uid, claim = true)
            }
        }
    }
    private suspend fun refreshFor(uid: String?, claim: Boolean) {
        val next = ai.getConfig().getOrNull() ?: return
        if (account.value.userId != uid) return
        _config.value = next
        if (claim && uid != null && next.earlyAccessOpen) {
            try {
                scheduleAi.claimEarlyAccess()
                ai.getConfig().getOrNull()?.let { if (account.value.userId == uid) _config.value = it }
            } catch (_: TimeoutCancellationException) {
                // A timed-out trial claim must not end the account observer.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Best effort, idempotent on server. A failed claim never blocks signing in.
            }
        }
    }
    fun refresh() { viewModelScope.launch { refreshFor(account.value.userId, claim = true) } }
    fun updateUsage(usage: AiUsage) { _config.value = _config.value.copy(usage = usage) }
    fun signIn(context: Context) {
        if (signingIn.value) return
        signingIn.value = true
        signInFailed.value = false
        viewModelScope.launch {
            try { accountRepository.signInWithGoogle(context) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { signInFailed.value = true }
            finally { signingIn.value = false }
        }
    }
}
