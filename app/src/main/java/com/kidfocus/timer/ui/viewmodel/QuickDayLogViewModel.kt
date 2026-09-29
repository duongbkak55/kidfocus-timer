package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.repository.DayLogRepository
import com.kidfocus.timer.data.remote.*
import com.kidfocus.timer.domain.daylog.*
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

enum class LogError { NETWORK, QUOTA, SIGN_IN, DISABLED, PROFILE_CHANGED, STALE, STORAGE }
data class QuickDayLogState(val text: String = "", val busy: Boolean = false, val preview: DayLogPreview? = null,
    val selected: Set<Int> = emptySet(), val request: DayLogRequest? = null, val profileId: String? = null,
    val error: LogError? = null, val usage: AiUsage? = null, val undoBatch: List<DayLogEntry> = emptyList(),
    val saved: Boolean = false, val restored: Boolean = false, val ownerId: String? = null)
@HiltViewModel
class QuickDayLogViewModel @Inject constructor(private val logger: ScheduleLogger, private val logs: DayLogRepository) : ViewModel() {
    private val _state = MutableStateFlow(QuickDayLogState())
    val state = _state.asStateFlow()
    private var revision = 0L
    private var contextKey: Triple<String?, LocalDate, String?>? = null
    fun context(profileId: String?, date: LocalDate, ownerId: String? = null) {
        val key = Triple(profileId, date, ownerId)
        if (key == contextKey) return
        contextKey = key; revision++
        _state.value = QuickDayLogState(ownerId = ownerId) // Discard previews/undo for another child, date or account.
    }
    fun editText(text: String) { if (!_state.value.busy) { revision++; _state.value = _state.value.copy(text = text.take(2000), preview = null, selected = emptySet(), request = null, error = null, saved = false, restored = false) } }
    fun select(index: Int, selected: Boolean) {
        val s = state.value; if (s.busy || s.preview?.entries?.getOrNull(index) == null) return
        _state.value = s.copy(selected = if (selected) s.selected + index else s.selected - index)
    }
    fun preview(data: DayLogData, date: LocalDate) {
        val s = state.value; val profile = data.profileId ?: return
        if (s.busy || s.text.isBlank()) return
        val request = DayLogDraft.request(s.text, date, data.tasks, data.anchors, data.ageBand, UUID.randomUUID().toString())
        val captured = revision
        _state.value = s.copy(busy = true, error = null, preview = null, selected = emptySet(), saved = false, restored = false)
        viewModelScope.launch {
            try {
                if (logs.activeProfileId() != profile) return@launch
                val reply = logger.log(request)
                if (captured != revision || logs.activeProfileId() != profile) return@launch
                _state.value = _state.value.copy(preview = reply.preview, selected = reply.preview.entries.mapIndexedNotNull { i, e -> i.takeIf { e.selectedByDefault } }.toSet(),
                    request = request, profileId = profile, usage = reply.usage)
            } catch (_: TimeoutCancellationException) { if (captured == revision) _state.value = _state.value.copy(error = LogError.NETWORK) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (captured == revision) _state.value = _state.value.copy(error = when ((e as? FirebaseFunctionsException)?.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> LogError.QUOTA
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> LogError.SIGN_IN
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> LogError.DISABLED
                else -> LogError.NETWORK
            }) }
            finally { if (captured == revision) _state.value = _state.value.copy(busy = false) }
        }
    }
    fun save(data: DayLogData) {
        val s = state.value; val preview = s.preview ?: return; val request = s.request ?: return
        if (s.busy || s.selected.isEmpty() || s.profileId != data.profileId) return
        val selected = s.selected.sorted().mapNotNull(preview.entries::getOrNull); if (selected.size != s.selected.size) return
        // A ref is valid only while that recurring occurrence still matches the reviewed plan.
        if (selected.any { e -> e.planRef?.let { ref -> request.plans[ref]?.let { plan ->
                planForDate(plan.date, data.tasks, data.anchors).none { it == plan }
            } ?: true } ?: false }) { _state.value = s.copy(error = LogError.STALE); return }
        val now = System.currentTimeMillis()
        val batch = selected.map { e -> DayLogEntry(profileId = s.profileId!!, date = e.date,
            taskId = e.planRef?.let { request.plans.getValue(it).taskId }, name = e.name, category = e.category,
            startMinute = e.startMinute, endMinute = e.endMinute, source = DayLogSource.AI, createdAt = now) }
        action(s.profileId!!, LogError.STORAGE) {
            logs.addAiBatch(batch)
            _state.value.copy(preview = null, selected = emptySet(), request = null, undoBatch = batch, saved = true, restored = false)
        }
    }
    fun undo() {
        val s = state.value; if (s.busy || s.undoBatch.isEmpty()) return
        action(s.undoBatch.first().profileId, LogError.STALE) {
            logs.undoAiBatch(s.undoBatch)
            _state.value.copy(undoBatch = emptyList(), saved = false, restored = true)
        }
    }
    private fun action(profile: String, failure: LogError, block: suspend () -> QuickDayLogState) {
        val captured = revision; _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                check(logs.activeProfileId() == profile) { "PROFILE_CHANGED" }; val next = block(); if (captured == revision) _state.value = next
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (captured == revision) _state.value = _state.value.copy(error = if (e.message == "PROFILE_CHANGED") LogError.PROFILE_CHANGED else failure) }
            finally { if (captured == revision) _state.value = _state.value.copy(busy = false) }
        }
    }
}
