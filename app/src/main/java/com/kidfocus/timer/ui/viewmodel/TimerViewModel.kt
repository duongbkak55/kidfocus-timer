package com.kidfocus.timer.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.domain.model.TimerState
import com.kidfocus.timer.domain.usecase.RecordSessionUseCase
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
import javax.inject.Inject

/**
 * ViewModel that bridges the UI with [TimerService] via a [ServiceConnection].
 *
 * Session recording is guarded by [lastRecordedTotalSeconds] to prevent double-recording
 * when the service emits a finished state and the composition re-enters.
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordSessionUseCase: RecordSessionUseCase,
) : ViewModel() {

    // ---- Public state ---------------------------------------------------------------------------

    private val _timerState = MutableStateFlow(TimerState.IDLE)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    /**
     * Emits the total minutes of the session that just completed.
     * Consumed by the navigation layer to route to the Celebration screen.
     * Reset to 0 after consumption with [consumeCompletedSession].
     */
    private val _completedSessionMinutes = MutableStateFlow(0)
    val completedSessionMinutes: StateFlow<Int> = _completedSessionMinutes.asStateFlow()

    // ---- Service binding -----------------------------------------------------------------------

    private var timerBinder: TimerService.TimerBinder? = null
    private var lastRecordedTotalSeconds: Int = -1

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

    // ---- Internal helpers ----------------------------------------------------------------------

    private fun onTimerStateUpdated(state: TimerState) {
        _timerState.update { state }

        // Guard: only record when the timer has naturally finished (not manually stopped)
        if (state.isFinished && state.totalSeconds != lastRecordedTotalSeconds) {
            lastRecordedTotalSeconds = state.totalSeconds
            viewModelScope.launch {
                recordSessionUseCase(
                    durationSeconds = state.totalSeconds,
                    isFocus = state.phase.isFocus,
                )
                if (state.phase.isFocus) {
                    _completedSessionMinutes.update { state.totalSeconds / 60 }
                }
            }
        }
    }

    // ---- Service binding management ------------------------------------------------------------

    /** Binds to [TimerService]. Call from the composable's [LaunchedEffect]. */
    fun bindService() {
        val intent = Intent(context, TimerService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    /** Unbinds from [TimerService]. Call from [ViewModel.onCleared] or composable disposal. */
    fun unbindService() {
        runCatching { context.unbindService(serviceConnection) }
        timerBinder = null
    }

    // ---- Timer control -------------------------------------------------------------------------

    /** Starts a focus countdown of [totalSeconds] seconds. */
    fun startFocus(totalSeconds: Int) {
        lastRecordedTotalSeconds = -1
        ensureServiceStarted()
        timerBinder?.startFocus(totalSeconds) ?: startServiceWithAction(
            action = TimerService.ACTION_START_FOCUS,
            totalSeconds = totalSeconds,
        )
    }

    /** Starts a break countdown of [totalSeconds] seconds. */
    fun startBreak(totalSeconds: Int) {
        lastRecordedTotalSeconds = -1
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

    /** Stops the timer and resets to idle. */
    fun stop() {
        timerBinder?.stop() ?: sendServiceAction(TimerService.ACTION_STOP)
        _timerState.update { TimerState.IDLE }
    }

    /** Acknowledges the completed session signal so navigation fires only once. */
    fun consumeCompletedSession() {
        _completedSessionMinutes.update { 0 }
    }

    // ---- Private helpers -----------------------------------------------------------------------

    private fun ensureServiceStarted() {
        val intent = Intent(context, TimerService::class.java)
        context.startForegroundService(intent)
    }

    private fun startServiceWithAction(action: String, totalSeconds: Int) {
        val intent = Intent(context, TimerService::class.java).apply {
            this.action = action
            putExtra(TimerService.EXTRA_TOTAL_SECONDS, totalSeconds)
        }
        context.startForegroundService(intent)
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(context, TimerService::class.java).apply { this.action = action }
        context.startService(intent)
    }

    override fun onCleared() {
        super.onCleared()
        unbindService()
    }
}
