package com.kidfocus.timer.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kidfocus.timer.KidFocusApp
import com.kidfocus.timer.MainActivity
import com.kidfocus.timer.R
import com.kidfocus.timer.data.datastore.SettingsDataStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    // ---- Audio / haptic -------------------------------------------------------------------------

    private var toneGenerator: ToneGenerator? = null
    private lateinit var vibrator: Vibrator

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

    override fun onCreate() {
        super.onCreate()
        @Suppress("DEPRECATION")
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        toneGenerator = try {
            ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        } catch (e: RuntimeException) {
            null
        }
    }

    override fun onBind(intent: Intent?): IBinder = TimerBinder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> {
                val seconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, DEFAULT_FOCUS_SECONDS)
                serviceScope.launch {
                    restoreCompletedSessionsIfNeeded()
                    startCountdown(TimerPhase.Focus, seconds)
                }
            }
            ACTION_START_BREAK -> {
                val seconds = intent.getIntExtra(EXTRA_TOTAL_SECONDS, DEFAULT_BREAK_SECONDS)
                serviceScope.launch {
                    restoreCompletedSessionsIfNeeded()
                    startCountdown(TimerPhase.Break, seconds)
                }
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
        }
        return START_STICKY
    }

    /**
     * Loads the persisted [completedFocusSessions] count from DataStore into the in-memory
     * [_timerState] when the service is (re)started and the count is still at its default of 0.
     * This ensures the count survives process death.
     */
    private suspend fun restoreCompletedSessionsIfNeeded() {
        if (_timerState.value.completedFocusSessions == 0) {
            val persisted = settingsDataStore.getCompletedFocusSessions()
            if (persisted > 0) {
                _timerState.update { it.copy(completedFocusSessions = persisted) }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        toneGenerator?.release()
        toneGenerator = null
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

        serviceScope.launch { playPhaseStartFeedback() }

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

        serviceScope.launch {
            playCompletionFeedback()
            showAlertNotification(phase)
        }

        if (phase.isFocus) {
            serviceScope.launch {
                settingsDataStore.saveCompletedFocusSessions(newCompletedCount)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- Notification ---------------------------------------------------------------------------

    private fun startForegroundWithNotification() {
        startForeground(
            KidFocusApp.TIMER_NOTIFICATION_ID,
            buildNotification(
                remainingSeconds = _timerState.value.remainingSeconds,
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

        val pauseOrResumeLabel = if (_timerState.value.isPaused) {
            getString(R.string.notification_resume_action)
        } else {
            getString(R.string.notification_pause_action)
        }

        return NotificationCompat.Builder(this, KidFocusApp.TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(phaseLabel)
            .setContentText(timeText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(
                R.drawable.ic_timer_notification,
                pauseOrResumeLabel,
                pauseOrResumeIntent,
            )
            .addAction(R.drawable.ic_timer_notification, getString(R.string.notification_stop_action), stopIntent)
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

    // ---- Audio / vibration helpers --------------------------------------------------------------

    private suspend fun playPhaseStartFeedback() {
        val settings = settingsDataStore.settingsFlow.first()
        if (settings.soundEnabled) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        }
        if (settings.vibrationEnabled) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100), -1)
            )
        }
    }

    private suspend fun playCompletionFeedback() {
        val settings = settingsDataStore.settingsFlow.first()
        if (settings.soundEnabled) {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        }
        if (settings.vibrationEnabled) {
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1)
            )
        }
    }

    private suspend fun showAlertNotification(phase: TimerPhase) {
        val (title, text) = if (phase.isFocus) {
            getString(R.string.notification_focus_complete) to
                getString(R.string.notification_focus_complete_text)
        } else {
            getString(R.string.notification_break_complete) to
                getString(R.string.notification_break_complete_text)
        }

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, KidFocusApp.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(this).notify(ALERT_NOTIFICATION_ID, notification)
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
        const val ALERT_NOTIFICATION_ID = 2
    }
}
