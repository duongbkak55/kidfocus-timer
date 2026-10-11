package com.kidfocus.timer.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.domain.model.FocusTimePolicy
import com.kidfocus.timer.domain.model.TimerState
import com.kidfocus.timer.domain.daylog.DayLogCategory
import com.kidfocus.timer.data.repository.RoutineRepository
import com.kidfocus.timer.service.RoutineAlarmScheduler
import com.kidfocus.timer.service.RoutineNotificationManager
import com.kidfocus.timer.service.TimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * ViewModel that bridges the UI with [TimerService] via a [ServiceConnection].
 *
 * Completion feedback and linked routine ticks are guarded by [lastRecordedTotalSeconds].
 * The service owns session and actual-log recording, including background completion.
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routineRepository: RoutineRepository,
    private val routineScheduler: RoutineAlarmScheduler,
    private val routineNotifications: RoutineNotificationManager,
) : ViewModel() {

    // ---- Public state ---------------------------------------------------------------------------

    private val _timerState = MutableStateFlow(TimerState.IDLE)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    /**
     * Emits the actual seconds of the session that just completed.
     * Consumed by the navigation layer to route to the Celebration screen.
     * Reset to 0 after consumption with [consumeCompletedSession].
     */
    private val _completedSessionSeconds = MutableStateFlow(0)
    val completedSessionSeconds: StateFlow<Int> = _completedSessionSeconds.asStateFlow()

    // ---- Service binding -----------------------------------------------------------------------

    private var timerBinder: TimerService.TimerBinder? = null
    private var lastRecordedTotalSeconds: Int = -1
    private var awaitingPhase: TimerPhase? = null
    private var isBound = false
    private var linkedRoutineId: Long? = null
    private var linkedOccurrenceDate: LocalDate? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TimerService.TimerBinder ?: return
            timerBinder = binder

            binder.timerState
                .onEach { state -> onTimerStateUpdated(state) }
                .launchIn(viewModelScope)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            timerBinder = null
        }
    }

    init {
        bindService()
    }

    // ---- Internal helpers ----------------------------------------------------------------------

    private fun onTimerStateUpdated(state: TimerState) {
        if (awaitingPhase != null && state.isFinished) return
        if (awaitingPhase == state.phase && state.remainingSeconds > 0) awaitingPhase = null
        _timerState.update { state }

        // Emit completion feedback only once after natural or early completion, never manual stop.
        if (state.isFinished && state.totalSeconds != lastRecordedTotalSeconds) {
            lastRecordedTotalSeconds = state.totalSeconds
            viewModelScope.launch {
                if (state.phase.isFocus) {
                    _completedSessionSeconds.value = state.totalSeconds
                    completeLinkedRoutineIfNeeded()
                }
            }
        }
    }

    // ---- Service binding management ------------------------------------------------------------

    /** Binds to [TimerService]. Call from the composable's [LaunchedEffect]. */
    fun bindService() {
        if (isBound) return
        val intent = Intent(context, TimerService::class.java)
        isBound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** Unbinds from [TimerService]. Call from [ViewModel.onCleared] or composable disposal. */
    fun unbindService() {
        runCatching { context.unbindService(serviceConnection) }
        timerBinder = null
        isBound = false
    }

    // ---- Timer control -------------------------------------------------------------------------

    /** Starts a focus countdown of [totalSeconds] seconds. */
    fun startFocusForActivity(name: String, totalSeconds: Int) {
        require(name.isNotBlank())
        linkedRoutineId = null
        linkedOccurrenceDate = null
        startFocusInternal(totalSeconds, activityName = name.trim().take(80), category = DayLogCategory.OTHER)
    }

    /** Starts a focus timer that completes the linked routine only after the timer finishes. */
    fun startFocusForRoutine(routineId: Long, occurrenceDate: LocalDate, totalSeconds: Int, activityName: String) {
        linkedRoutineId = routineId
        linkedOccurrenceDate = occurrenceDate
        startFocusInternal(totalSeconds, activityName = activityName, category = DayLogCategory.ROUTINE)
    }

    fun startFocusForTask(task: com.kidfocus.timer.domain.model.ScheduledTask, totalSeconds: Int = task.focusDurationMinutes * 60) {
        linkedRoutineId = null
        linkedOccurrenceDate = null
        startFocusInternal(totalSeconds, task.id, task.name, DayLogCategory.valueOf(task.taskType.category.name))
    }

    private fun startFocusInternal(totalSeconds: Int, taskId: Long? = null, activityName: String? = null,
        category: DayLogCategory = DayLogCategory.OTHER) {
        lastRecordedTotalSeconds = -1
        awaitingPhase = TimerPhase.Focus
        _completedSessionSeconds.value = 0
        ensureServiceStarted()
        timerBinder?.startFocus(totalSeconds, taskId, activityName, category) ?: startServiceWithAction(
            action = TimerService.ACTION_START_FOCUS,
            totalSeconds = totalSeconds,
            taskId = taskId,
            activityName = activityName,
            category = category,
        )
    }

    /** Starts a break countdown of [totalSeconds] seconds. */
    fun startBreak(totalSeconds: Int) {
        lastRecordedTotalSeconds = -1
        awaitingPhase = TimerPhase.Break
        _completedSessionSeconds.value = 0
        ensureServiceStarted()
        timerBinder?.startBreak(totalSeconds) ?: startServiceWithAction(
            action = TimerService.ACTION_START_BREAK,
            totalSeconds = totalSeconds,
        )
    }

    /** Pauses the running timer. */
    fun pause() {
        timerBinder?.pause() ?: sendServiceAction(TimerService.ACTION_PAUSE)
    }

    /** Resumes a paused timer. */
    fun resume() {
        timerBinder?.resume() ?: sendServiceAction(TimerService.ACTION_RESUME)
    }

    /** The service rechecks parent policy and both caps when this command is handled. */
    fun extendFocus() {
        timerBinder?.extendFocus(FocusTimePolicy.STEP_MINUTES) ?: run {
            val intent = Intent(context, TimerService::class.java).apply {
                action = TimerService.ACTION_EXTEND_FOCUS
                putExtra(TimerService.EXTRA_EXTEND_MINUTES, FocusTimePolicy.STEP_MINUTES)
            }
            context.startService(intent)
        }
    }

    /** Marks the linked activity complete, retaining only the time actually focused. */
    fun completeEarly() {
        timerBinder?.completeEarly() ?: sendServiceAction(TimerService.ACTION_COMPLETE_EARLY)
    }

    /** Stops the timer and resets to idle. */
    fun stop() {
        awaitingPhase = null
        _completedSessionSeconds.value = 0
        timerBinder?.stop() ?: sendServiceAction(TimerService.ACTION_STOP)
        _timerState.update { TimerState.IDLE }
    }

    /** Acknowledges the completed session signal so navigation fires only once. */
    fun consumeCompletedSession() {
        _completedSessionSeconds.value = 0
    }

    // ---- Private helpers -----------------------------------------------------------------------

    private fun ensureServiceStarted() {
        val intent = Intent(context, TimerService::class.java)
        context.startForegroundService(intent)
    }

    private fun startServiceWithAction(action: String, totalSeconds: Int, taskId: Long? = null,
        activityName: String? = null, category: DayLogCategory = DayLogCategory.OTHER) {
        val intent = Intent(context, TimerService::class.java).apply {
            this.action = action
            putExtra(TimerService.EXTRA_TOTAL_SECONDS, totalSeconds)
            taskId?.let { putExtra(TimerService.EXTRA_TASK_ID, it) }
            activityName?.let { putExtra(TimerService.EXTRA_ACTIVITY_NAME, it) }
            putExtra(TimerService.EXTRA_ACTIVITY_CATEGORY, category.name)
        }
        context.startForegroundService(intent)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(context, TimerService::class.java).apply { this.action = action }
        context.startService(intent)
    }

    private suspend fun completeLinkedRoutineIfNeeded() {
        val routineId = linkedRoutineId ?: return
        val occurrenceDate = linkedOccurrenceDate ?: return
        linkedRoutineId = null
        linkedOccurrenceDate = null
        val routine = routineRepository.getById(routineId) ?: return
        routineRepository.complete(routine, occurrenceDate)
        routineNotifications.cancel(routineId)
        val tomorrow = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        routineScheduler.schedule(routine, tomorrow)
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}
