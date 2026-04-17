package com.kidfocus.timer.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.kidfocus.timer.KidFocusApp
import com.kidfocus.timer.MainActivity
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.domain.model.TimerState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Foreground service that owns the timer countdown coroutine.
 *
 * The service survives app process background and exposes [timerState] as a
 * [StateFlow] so ViewModels can observe it without tight coupling.
 *
 * Lifecycle:
 * - Start via [startForeground] → [ACTION_START_FOCUS] or [ACTION_START_BREAK]
 * - Pause/resume via [ACTION_PAUSE] / [ACTION_RESUME]
 * - Stop via [ACTION_STOP] or when the countdown reaches zero
 */
@AndroidEntryPoint
class TimerService : Service() {

    // ---- Binder ---------------------------------------------------------------------------------

    inner class TimerBinder : Binder() {
        /** Returns the [StateFlow] of [TimerState] for UI observation. */
        val timerState: StateFlow<TimerState> get() = this@TimerService.timerState

        fun startFocus(totalSeconds: Int) = this@TimerService.startCountdown(
            phase = TimerPhase.Focus,
            totalSeconds = totalSeconds,
        )

        fun startBreak(totalSeconds: Int) = this@TimerService.startCountdown(
            phase = TimerPhase.Break,
            totalSeconds = totalSeconds,
        )

        fun pause() = this@TimerService.pauseTimer()
        fun resume() = this@TimerService.resumeTimer()
        fun stop() = this@TimerService.stopTimer()
    }

    // ---- State ----------------------------------------------------------------------------------

    private val _timerState = MutableStateFlow(TimerState.IDLE)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    // ---- Coroutine scope ------------------------------------------------------------------------

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var countdownJob: Job? = null

    // ---- Service lifecycle ----------------------------------------------------------------------

    override fun onBind(intent: Intent?): IBinder = TimerBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> {
                val seconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, DEFAULT_FOCUS_SECONDS)
                startCountdown(TimerPhase.Focus, seconds)
            }
            ACTION_START_BREAK -> {
                val seconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, DEFAULT_BREAK_SECONDS)
                startCountdown(TimerPhase.Break, seconds)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    // ---- Timer logic ----------------------------------------------------------------------------

    private fun startCountdown(phase: TimerPhase, totalSeconds: Int) {
        countdownJob?.cancel()

        val completedSessions = _timerState.value.completedFocusSessions

        _timerState.update {
            TimerState(
                phase = phase,
                totalSeconds = totalSeconds,
                remainingSeconds = totalSeconds,
                isRunning = true,
                isPaused = false,
                completedFocusSessions = completedSessions,
            )
        }

        startForegroundWithNotification()

        countdownJob = serviceScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1_000L)
                remaining--
                _timerState.update { it.copy(remainingSeconds = remaining) }
                updateNotification(remaining, phase)
            }
            onPhaseCompleted(phase, completedSessions)
        }
    }

    private fun pauseTimer() {
        countdownJob?.cancel()
        countdownJob = null
        _timerState.update { it.copy(isRunning = false, isPaused = true) }
        updateNotification(_timerState.value.remainingSeconds, _timerState.value.phase)
    }

    private fun resumeTimer() {
        val state = _timerState.value
        if (!state.isPaused) return
        startCountdown(state.phase, state.remainingSeconds)
        // Restore completed count explicitly since startCountdown reads it from current state
    }

    private fun stopTimer() {
        countdownJob?.cancel()
        countdownJob = null
        _timerState.update { it.copy(isRunning = false, isPaused = false) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun onPhaseCompleted(phase: TimerPhase, previousCompletedSessions: Int) {
        val newCompletedCount = if (phase.isFocus) previousCompletedSessions + 1
        else previousCompletedSessions

        _timerState.update {
            it.copy(
                remainingSeconds = 0,
                isRunning = false,
                isPaused = false,
                completedFocusSessions = newCompletedCount,
            )
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- Notification ---------------------------------------------------------------------------

    private fun startForegroundWithNotification() {
        startForeground(
            KidFocusApp.TIMER_NOTIFICATION_ID,
            buildNotification(
                remaining = _timerState.value.remainingSeconds,
                phase = _timerState.value.phase,
            )
        )
    }

    private fun updateNotification(remainingSeconds: Int, phase: TimerPhase) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(
            KidFocusApp.TIMER_NOTIFICATION_ID,
            buildNotification(remainingSeconds, phase),
        )
    }

    private fun buildNotification(remainingSeconds: Int, phase: TimerPhase): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val pauseOrResumeIntent = if (_timerState.value.isPaused) {
            buildServicePendingIntent(ACTION_RESUME, 2)
        } else {
            buildServicePendingIntent(ACTION_PAUSE, 3)
        }

        val stopIntent = buildServicePendingIntent(ACTION_STOP, 4)

        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        val timeText = "%02d:%02d".format(minutes, seconds)
        val phaseLabel = phase.label

        return NotificationCompat.Builder(this, KidFocusApp.TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(phaseLabel)
            .setContentText(timeText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_timer_notification,
                if (_timerState.value.isPaused) "Tiếp tục" else "Tạm dừng",
                pauseOrResumeIntent,
            )
            .addAction(R.drawable.ic_timer_notification, "Dừng", stopIntent)
            .build()
    }

    private fun buildServicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, TimerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ---- Constants ------------------------------------------------------------------------------

    companion object {
        const val ACTION_START_FOCUS = "com.kidfocus.timer.ACTION_START_FOCUS"
        const val ACTION_START_BREAK = "com.kidfocus.timer.ACTION_START_BREAK"
        const val ACTION_PAUSE = "com.kidfocus.timer.ACTION_PAUSE"
        const val ACTION_RESUME = "com.kidfocus.timer.ACTION_RESUME"
        const val ACTION_STOP = "com.kidfocus.timer.ACTION_STOP"
        const val EXTRA_TOTAL_SECONDS = "extra_total_seconds"

        private const val DEFAULT_FOCUS_SECONDS = 25 * 60
        private const val DEFAULT_BREAK_SECONDS = 5 * 60
    }
}
