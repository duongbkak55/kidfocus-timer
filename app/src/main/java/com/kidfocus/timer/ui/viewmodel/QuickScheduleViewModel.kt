package com.kidfocus.timer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.remote.AiUsage
import com.kidfocus.timer.data.remote.ScheduleParser
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.data.schedule.RoomScheduleStore
import com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository
import com.kidfocus.timer.domain.schedule.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class QuickScheduleError { NETWORK, QUOTA, PARSE, SIGN_IN, DISABLED, STALE, APPLY }
data class QuickScheduleState(
    val text: String = "", val draft: ScheduleDraft? = null, val busy: Boolean = false,
    val error: QuickScheduleError? = null, val usage: AiUsage? = null,
    val expected: ScheduleState? = null, val profileId: String? = null, val ageBand: String = "4-5",
    val saved: Boolean = false,
)

@HiltViewModel
class QuickScheduleViewModel @Inject constructor(
    profiles: ChildProfileRepository,
    tasks: ScheduledTaskRepository,
    anchors: ScheduleAnchorsRepository,
    private val parser: ScheduleParser,
    private val applySchedule: ApplyScheduleUseCase,
    private val store: RoomScheduleStore,
) : ViewModel() {
    private val _state = MutableStateFlow(QuickScheduleState())
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<ScheduleEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    val profile = profiles.activeProfile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val current = profiles.activeProfile.flatMapLatest { child ->
        combine(tasks.allTasks, anchors.observe(child.id)) { rows, hours ->
            ScheduleState(rows.filter { it.childProfileId == child.id }.sortedBy { it.id }, hours)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var observedProfileId: String? = null
    private var profileRevision = 0L
    private fun isCurrent(child: ChildProfileEntity, revision: Long) = profileRevision == revision && profile.value?.id == child.id
    init {
        viewModelScope.launch {
            profile.filterNotNull().map { it.id }.distinctUntilChanged().collect { id ->
                profileRevision++
                if (observedProfileId != null && observedProfileId != id) _state.value = QuickScheduleState()
                observedProfileId = id
            }
        }
    }
    fun editText(text: String) {
        if (!_state.value.busy) _state.value = _state.value.copy(text = text.take(2000), error = null)
    }
    fun editItem(item: DraftItem) {
        val state = _state.value
        if (state.busy) return
        _state.value = state.copy(draft = state.draft?.copy(items = state.draft.items.map { if (it.key == item.key) item else it }), error = null)
    }
    fun parse() {
        val child = profile.value ?: return
        val revision = profileRevision
        val initial = _state.value
        if (initial.busy || initial.text.isBlank()) return
        _state.value = initial.copy(busy = true, error = null, profileId = child.id)
        viewModelScope.launch {
            try {
                val expected = store.read(child.id)
                val reply = parser.parse(initial.text, child.ageBand, expected)
                if (!isCurrent(child, revision)) return@launch
                _state.value = _state.value.copy(draft = reply.draft, usage = reply.usage, expected = expected,
                    profileId = child.id, ageBand = child.ageBand, saved = false)
            } catch (_: TimeoutCancellationException) {
                setError(child, revision, QuickScheduleError.NETWORK)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val kind = when {
                    error is FirebaseFunctionsException && error.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> QuickScheduleError.QUOTA
                    error is FirebaseFunctionsException && error.code == FirebaseFunctionsException.Code.UNAUTHENTICATED -> QuickScheduleError.SIGN_IN
                    error.message?.contains("AI_PARSE_FAILED") == true -> QuickScheduleError.PARSE
                    error.message?.contains("AI_SCHEDULE_DISABLED") == true -> QuickScheduleError.DISABLED
                    else -> QuickScheduleError.NETWORK
                }
                setError(child, revision, kind)
            } finally {
                if (isCurrent(child, revision)) _state.value = _state.value.copy(busy = false)
            }
        }
    }
    private fun setError(child: ChildProfileEntity, revision: Long, error: QuickScheduleError) {
        // Preserve the parent's text and existing preview on quota/network/parse errors.
        if (isCurrent(child, revision)) _state.value = _state.value.copy(error = error)
    }
    fun apply() {
        val state = _state.value
        val child = profile.value ?: return
        val revision = profileRevision
        val draft = state.draft ?: return
        val expected = state.expected ?: return
        if (state.busy || state.profileId != child.id) return
        val changes = runCatching { draft.changes(child.id, expected) }.getOrElse {
            _state.value = state.copy(error = QuickScheduleError.APPLY); return
        }
        if (changes.isEmpty()) return
        _state.value = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                applySchedule.apply(child.id, expected, changes, state.ageBand)
                if (isCurrent(child, revision)) _state.value = _state.value.copy(draft = null, expected = null, saved = true)
                if (isCurrent(child, revision)) _events.emit(ScheduleEvent.SAVED)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val stale = runCatching { store.read(child.id) }.getOrNull()?.let { it != expected } ?: false
                setError(child, revision, if (stale) QuickScheduleError.STALE else QuickScheduleError.APPLY)
            }
            finally { if (isCurrent(child, revision)) _state.value = _state.value.copy(busy = false) }
        }
    }
    fun undo() {
        val state = _state.value
        val child = profile.value ?: return
        val revision = profileRevision
        if (!state.saved || state.busy || state.profileId != child.id) return
        _state.value = state.copy(busy = true, error = null)
        viewModelScope.launch {
            try {
                applySchedule.undo(child.id)
                if (isCurrent(child, revision)) {
                    _state.value = _state.value.copy(saved = false)
                    _events.emit(ScheduleEvent.RESTORED)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { setError(child, revision, QuickScheduleError.APPLY) }
            finally { if (isCurrent(child, revision)) _state.value = _state.value.copy(busy = false) }
        }
    }
}
