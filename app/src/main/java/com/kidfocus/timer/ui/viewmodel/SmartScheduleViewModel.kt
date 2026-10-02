package com.kidfocus.timer.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.data.repository.ChildProfileRepository
import com.kidfocus.timer.data.repository.RoutineRepository
import com.kidfocus.timer.data.repository.ScheduledTaskRepository
import com.kidfocus.timer.data.schedule.RoomScheduleStore
import com.kidfocus.timer.data.schedule.ScheduleAnchorsRepository
import com.kidfocus.timer.data.schedule.SchedulePlansRepository
import com.kidfocus.timer.data.remote.ScheduleAdviser
import com.kidfocus.timer.data.remote.AiUsage
import com.google.firebase.functions.FirebaseFunctionsException
import java.util.Locale
import java.util.UUID
import com.kidfocus.timer.domain.model.RoutineTime
import com.kidfocus.timer.domain.schedule.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class SmartScheduleUiState(val profile: ChildProfileEntity, val schedule: ScheduleState, val findings: List<Finding>, val canUndo: Boolean,
    val routineStats: List<RoutineStat> = emptyList(), val plan: SchedulePlan? = null,
    val actualStats: com.kidfocus.timer.domain.daylog.ActualScheduleStats = com.kidfocus.timer.domain.daylog.ActualScheduleStats())
enum class AdviceError { NETWORK, QUOTA, SIGN_IN, DISABLED, STALE, APPLY }
data class AdviceUiState(val note: String = "", val tags: Set<NoteTag> = emptySet(), val advice: ScheduleAdvice? = null,
    val rows: List<AdviceRow> = emptyList(), val selected: Set<Int> = emptySet(), val expected: ScheduleState? = null,
    val error: AdviceError? = null, val usage: AiUsage? = null)
enum class ScheduleEvent { SAVED, RESTORED, ERROR }

@HiltViewModel
class SmartScheduleViewModel @Inject constructor(
    profiles: ChildProfileRepository,
    tasks: ScheduledTaskRepository,
    routines: RoutineRepository,
    anchors: ScheduleAnchorsRepository,
    private val applySchedule: ApplyScheduleUseCase,
    private val store: RoomScheduleStore,
    private val adviser: ScheduleAdviser,
    private val plans: SchedulePlansRepository,
    dayLogs: com.kidfocus.timer.data.repository.DayLogRepository,
    private val settingsDataStore: com.kidfocus.timer.data.datastore.SettingsDataStore,
) : ViewModel() {
    private val _advice = MutableStateFlow(AdviceUiState())
    val advice = _advice.asStateFlow()
    private var references: AdviceRefs = emptyMap()
    private val profile = profiles.activeProfile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var profileRevision = 0L
    init {
        viewModelScope.launch {
            profile.filterNotNull().map { it.id }.distinctUntilChanged().collect {
                profileRevision++
                _advice.value = AdviceUiState()
                references = emptyMap()
            }
        }
    }
    val busy = MutableStateFlow(false)
    private val _events = MutableSharedFlow<ScheduleEvent>()
    val events = _events.asSharedFlow()
    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }
    val state = profiles.activeProfile.flatMapLatest { profile ->
        val scheduleFlow = combine(combine(tasks.allTasks, dayLogs.observe(profile.id)) { rows, actual -> rows to actual }, anchors.observe(profile.id), routines.observeAll(),
            routines.observeCompletionsSince(profile.id, LocalDate.now().minusDays(ScheduleThresholds.HISTORY_DAYS - 1)), clock) { taskAndActual, hours, routineRows, completions, now ->
            val (rows, actual) = taskAndActual
            val extensions = settingsDataStore.focusExtensions.first()
            val scoped = rows.filter { it.childProfileId == profile.id }.sortedBy { it.id }
            val patterns = routineRows.filter { it.childProfileId == profile.id && it.enabled }.mapNotNull {
                runCatching {
                    RoutinePattern(it.id, DayCodec.fromMask(it.repeatDaysMask), it.deadlineMinutes, RoutineTime.dateAt(it.createdAtMillis))
                }.getOrNull()
            }
            val observations = completions.mapNotNull {
                runCatching { RoutineObservation(it.routineId, LocalDate.parse(it.occurrenceDate), it.status) }.getOrNull()
            }
            val schedule = ScheduleState(scoped, hours)
            val snapshot = store.snapshot(profile.id)
            val findings = runCatching {
                ScheduleAdvisor().advise(scoped, hours, profile.ageBand, patterns, observations, now.toLocalDate(), now.toLocalTime().minutes(), actual, extensions)
            }.getOrElse { error ->
                Log.w("SmartScheduleViewModel", "Unable to evaluate weekly schedule", error)
                emptyList()
            }
            val stats = patterns.map { pattern ->
                val tracked = observations.any { it.routineId == pattern.id && it.date in now.toLocalDate().minusDays(13)..now.toLocalDate() }
                val count = if (!tracked) 0 else (0L..6L).count { offset ->
                    val day = now.toLocalDate().minusDays(offset)
                    val status = observations.lastOrNull { it.routineId == pattern.id && it.date == day }?.status
                    day >= pattern.createdDate && day.dayOfWeek in pattern.days && (status in setOf("LATE", "MISSED") ||
                        (status == null && (day < now.toLocalDate() || now.toLocalTime().minutes() > pattern.deadlineMinutes)))
                }
                RoutineStat(routineRows.single { it.id == pattern.id }.title, count)
            }
            SmartScheduleUiState(profile, schedule, findings,
                snapshot != null && snapshot.applied == schedule && System.currentTimeMillis() - snapshot.savedAtMillis in 0..604_800_000L, stats, actualStats = com.kidfocus.timer.domain.daylog.ActualScheduleAdvisor.evaluate(scoped, hours, actual, now.toLocalDate(), now.toLocalTime().minutes(), extensions).stats)
        }
        combine(scheduleFlow, plans.observe(profile.id)) { current, plan -> current.copy(plan = plan?.takeIf { it.matches(current.schedule.anchors) }) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun editNote(note: String) { if (!busy.value) _advice.value = _advice.value.copy(note = note.take(500)) }
    fun toggleTag(tag: NoteTag) { if (!busy.value) _advice.value = _advice.value.let { it.copy(tags = if (tag in it.tags) it.tags - tag else it.tags + tag) } }
    fun select(index: Int, selected: Boolean) {
        if (busy.value) return
        val row = _advice.value.rows.find { it.index == index } ?: return
        if (row.rejection != null || row.requiresPlan) return
        _advice.value = _advice.value.let { it.copy(selected = if (selected) it.selected + index else it.selected - index) }
    }
    fun requestAdvice() {
        val current = state.value ?: return
        if (busy.value || current.findings.isEmpty()) return
        val revision = profileRevision
        val input = _advice.value
        val refs = ScheduleAdviceReview.refs(current.schedule)
        busy.value = true
        _advice.value = input.copy(error = null)
        viewModelScope.launch {
            try {
                val reply = adviser.advise(ScheduleAdvicePayload.build(current.schedule, refs, current.findings, current.routineStats,
                    current.profile.ageBand, LocalDate.now().toString(), Locale.getDefault().let { it.language + (it.country.takeIf { country -> country.length == 2 }?.let { country -> "-$country" } ?: "") },
                    input.note, input.tags, UUID.randomUUID().toString(), current.actualStats))
                if (revision != profileRevision || profile.value?.id != current.profile.id) return@launch
                if (store.read(current.profile.id) != current.schedule) {
                    _advice.value = _advice.value.copy(error = AdviceError.STALE, usage = reply.usage)
                    return@launch
                }
                references = refs
                val rows = ScheduleAdviceReview.review(reply.advice, refs, current.profile.id, current.schedule, current.profile.ageBand, applySchedule)
                _advice.value = input.copy(advice = reply.advice, rows = rows, selected = emptySet(), expected = current.schedule, usage = reply.usage)
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                if (revision == profileRevision) _advice.value = _advice.value.copy(error = AdviceError.NETWORK)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (revision == profileRevision) _advice.value = _advice.value.copy(error = when ((error as? FirebaseFunctionsException)?.code) {
                    FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> AdviceError.QUOTA
                    FirebaseFunctionsException.Code.UNAUTHENTICATED -> AdviceError.SIGN_IN
                    FirebaseFunctionsException.Code.FAILED_PRECONDITION -> AdviceError.DISABLED
                    else -> AdviceError.NETWORK
                })
            } finally { busy.value = false }
        }
    }
    fun applyAdvice(indices: Set<Int>, removalsConfirmed: Boolean = false) {
        val current = state.value ?: return
        val preview = _advice.value
        val rows = preview.rows.filter { it.index in indices }
        if (rows.isEmpty() || rows.size != indices.size || rows.any { it.rejection != null || it.requiresPlan } || (rows.any { it.removes } && !removalsConfirmed)) return
        val changes = rows.flatMap { it.changes }
        val revision = profileRevision
        runAction(ScheduleEvent.SAVED) {
            check(profile.value?.id == current.profile.id && current.schedule == preview.expected) { "STALE" }
            applySchedule.applyAdvice(current.profile.id, checkNotNull(preview.expected), changes, current.profile.ageBand)
            val updated = store.read(current.profile.id)
            if (revision == profileRevision) {
                references = ScheduleAdviceReview.rebase(references, current.schedule, updated, changes)
                val remaining = checkNotNull(preview.advice).copy(proposals = preview.advice.proposals.filterIndexed { index, _ -> index !in indices })
                _advice.value = preview.copy(advice = remaining, rows = ScheduleAdviceReview.review(remaining, references, current.profile.id, updated, current.profile.ageBand, applySchedule), selected = emptySet(), expected = updated, error = null)
            }
        }
    }
    fun startPlan(index: Int) {
        val current = state.value ?: return
        val preview = _advice.value
        val row = preview.rows.find { it.index == index && it.requiresPlan && it.rejection == null } ?: return
        val change = row.changes.single()
        val times = when (change) { is ScheduleChange.SetBed -> change.times; is ScheduleChange.SetWake -> change.times; else -> return }
        val revision = profileRevision
        val kind = if (change is ScheduleChange.SetBed) PlanKind.BED else PlanKind.WAKE
        runAction(ScheduleEvent.SAVED) {
            check(profile.value?.id == current.profile.id && current.schedule == preview.expected && plans.get(current.profile.id) == null) { "STALE" }
            val today = LocalDate.now()
            val plan = SchedulePlan.create(kind, times.keys, times.values.first(), current.schedule.anchors, today)
            // Check every intermediate step, including new school/high conflicts, before starting.
            var trial = current.schedule
            for (step in 1..plan.totalSteps) trial = applySchedule.previewAdvice(current.profile.id, trial, listOf(plan.copy(nextStep = step).change()), current.profile.ageBand)
            applySchedule.applyAdvice(current.profile.id, current.schedule, listOf(plan.change()), current.profile.ageBand)
            plans.save(current.profile.id, plan.advanced(today))
            if (revision == profileRevision) _advice.value = AdviceUiState(note = preview.note, tags = preview.tags, usage = preview.usage)
        }
    }
    fun applyPlanStep() {
        val current = state.value ?: return
        val plan = current.plan ?: return
        val revision = profileRevision
        runAction(ScheduleEvent.SAVED) {
            val today = LocalDate.now()
            check(profile.value?.id == current.profile.id && plans.get(current.profile.id) == plan && plan.matches(current.schedule.anchors) && plan.due(today))
            applySchedule.applyAdvice(current.profile.id, current.schedule, listOf(plan.change()), current.profile.ageBand)
            plans.save(current.profile.id, plan.advanced(today))
            if (revision == profileRevision) _advice.value = AdviceUiState(note = _advice.value.note, tags = _advice.value.tags)
        }
    }
    fun cancelPlan() {
        val current = state.value ?: return
        if (busy.value) return
        runAction(null) { plans.save(current.profile.id, null) }
    }

    fun apply(finding: Finding) {
        val current = state.value ?: return
        val change = finding.suggestion ?: return
        runAction(ScheduleEvent.SAVED) { applySchedule.apply(current.profile.id, current.schedule, listOf(change), current.profile.ageBand) }
    }
    fun saveAnchors(anchors: ScheduleAnchors) {
        val current = state.value ?: return
        runAction(ScheduleEvent.SAVED) { applySchedule.saveAnchors(current.profile.id, current.schedule, anchors) }
    }
    fun undo() {
        val profileId = state.value?.profile?.id ?: return
        runAction(ScheduleEvent.RESTORED) { applySchedule.undo(profileId) }
    }
    private fun runAction(event: ScheduleEvent?, action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        val revision = profileRevision
        viewModelScope.launch {
            try {
                action()
                if (revision == profileRevision && event != null) _events.emit(event)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (revision == profileRevision) {
                    _advice.value = _advice.value.copy(error = if (error.message == "STALE" || error.message?.contains("Schedule changed") == true) AdviceError.STALE else AdviceError.APPLY)
                    _events.emit(ScheduleEvent.ERROR)
                }
            } finally {
                busy.value = false
            }
        }
    }
}
