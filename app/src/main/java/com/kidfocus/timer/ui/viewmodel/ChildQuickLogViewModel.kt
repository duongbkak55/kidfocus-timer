package com.kidfocus.timer.ui.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.kidfocus.timer.data.cloud.FirebaseAccountRepository
import com.kidfocus.timer.data.datastore.SettingsDataStore
import com.kidfocus.timer.data.remote.AiConfig
import com.kidfocus.timer.data.remote.AiAppVerificationException
import com.kidfocus.timer.data.remote.ScheduleLogger
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.data.repository.DayLogRepository
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository
import com.kidfocus.timer.domain.daylog.*
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.schedule.ScheduleAnchors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ChildLogContext(
    val profileId: String? = null,
    val avatar: String = "🐣",
    val ageBand: String = "4-5",
    val tasks: List<ScheduledTask> = emptyList(),
    val anchors: ScheduleAnchors = ScheduleAnchors(),
    val lockScreenAllowed: Boolean = true,
)

enum class ChildLogError { UNAVAILABLE, QUOTA, NETWORK, STORAGE, PROFILE_CHANGED }

data class ChildLogState(
    val text: String = "",
    val busy: Boolean = false,
    val saved: List<DayLogEntry> = emptyList(),
    val unclear: List<String> = emptyList(),
    val question: String? = null,
    val error: ChildLogError? = null,
    val undoBatch: List<DayLogEntry> = emptyList(),
    val undoUntilMillis: Long = 0,
)

@HiltViewModel
class ChildQuickLogViewModel @Inject constructor(
    profiles: ChildProfileRepository,
    tasks: ScheduledTaskRepository,
    anchors: ScheduleAnchorsRepository,
    settings: SettingsDataStore,
    private val logger: ScheduleLogger,
    private val logs: DayLogRepository,
    private val account: FirebaseAccountRepository,
) : ViewModel() {
    // An invalid/missing active ID never silently selects another child on the lock screen.
    val context = combine(profiles.profiles, settings.settingsFlow, tasks.allTasks, anchors.all) { children, prefs, rows, hours ->
        val child = children.firstOrNull { it.id == prefs.activeChildProfileId }
        ChildLogContext(child?.id, child?.avatarEmoji ?: "🐣", child?.ageBand ?: "4-5",
            rows.filter { it.childProfileId == child?.id }, child?.id?.let(hours::get) ?: ScheduleAnchors(),
            prefs.allowLockScreenChildLog)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ChildLogContext())

    private val _state = MutableStateFlow(ChildLogState())
    val state = _state.asStateFlow()
    private var lastSendElapsed = -1L

    fun editText(text: String) {
        if (!_state.value.busy) _state.value = _state.value.copy(text = text.take(300), error = null)
    }

    fun clearDraft() { _state.value = _state.value.copy(text = "") }

    fun send(config: AiConfig, fromLockScreen: Boolean = false) {
        val snapshot = context.value
        val ownerId = account.account.value.userId
        val input = _state.value.text.trim()
        val nowElapsed = SystemClock.elapsedRealtime()
        if (_state.value.busy || input.isEmpty() ||
            (lastSendElapsed >= 0 && nowElapsed - lastSendElapsed < 1_000)) return
        if (snapshot.profileId == null || ownerId == null || !config.enabled || !config.scheduleEnabled ||
            (fromLockScreen && !snapshot.lockScreenAllowed)) {
            _state.value = _state.value.copy(error = ChildLogError.UNAVAILABLE)
            return
        }
        lastSendElapsed = nowElapsed
        _state.value = _state.value.copy(busy = true, error = null, saved = emptyList(), unclear = emptyList(), question = null)
        viewModelScope.launch {
            try {
                val request = DayLogDraft.request(input, LocalDate.now(), snapshot.tasks, snapshot.anchors,
                    snapshot.ageBand, UUID.randomUUID().toString())
                val reply = logger.log(request)
                if (context.value.profileId != snapshot.profileId || account.account.value.userId != ownerId ||
                    (fromLockScreen && !context.value.lockScreenAllowed) || logs.activeProfileId() != snapshot.profileId) {
                    _state.value = _state.value.copy(error = ChildLogError.PROFILE_CHANGED)
                    return@launch
                }
                val now = LocalDateTime.now()
                val (selected, rejected) = reply.preview.entries.partition { candidate ->
                    candidate.selectedByDefault && !candidate.isFutureAt(now) &&
                        (candidate.planRef == null || request.plans[candidate.planRef]?.let { plan ->
                            plan.date == candidate.date ||
                                (plan.date.plusDays(1) == candidate.date && candidate.startMinute < 360)
                        } == true)
                }
                val savedAt = System.currentTimeMillis()
                val batch = selected.map { candidate ->
                    DayLogEntry(profileId = snapshot.profileId, date = candidate.date,
                        taskId = candidate.planRef?.let { request.plans.getValue(it).taskId }, name = candidate.name,
                        category = candidate.category, startMinute = candidate.startMinute, endMinute = candidate.endMinute,
                        source = DayLogSource.CHILD, createdAt = savedAt)
                }
                if (batch.isNotEmpty()) logs.addChildBatch(batch)
                if (context.value.profileId != snapshot.profileId || account.account.value.userId != ownerId) {
                    _state.value = _state.value.copy(text = "", error = ChildLogError.PROFILE_CHANGED)
                    return@launch
                }
                _state.value = _state.value.copy(text = "", saved = batch, unclear = rejected.map { it.name },
                    question = reply.preview.questions.firstOrNull(), undoBatch = batch,
                    undoUntilMillis = if (batch.isEmpty()) 0 else savedAt + 10_000)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.value = _state.value.copy(error = when {
                    error is FirebaseFunctionsException && error.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> ChildLogError.QUOTA
                    error is FirebaseFunctionsException || error is AiAppVerificationException -> ChildLogError.UNAVAILABLE
                    else -> ChildLogError.NETWORK
                })
            } finally { _state.value = _state.value.copy(busy = false) }
        }
    }

    fun undo(onComplete: () -> Unit = {}) {
        val batch = _state.value.undoBatch
        if (_state.value.busy || batch.isEmpty() || System.currentTimeMillis() > _state.value.undoUntilMillis) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            try {
                if (context.value.profileId != batch.first().profileId) throw IllegalStateException("PROFILE_CHANGED")
                logs.undoChildBatch(batch)
                _state.value = _state.value.copy(saved = emptyList(), undoBatch = emptyList(), undoUntilMillis = 0)
                onComplete()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.value = _state.value.copy(error = ChildLogError.STORAGE) }
            finally { _state.value = _state.value.copy(busy = false) }
        }
    }
}
