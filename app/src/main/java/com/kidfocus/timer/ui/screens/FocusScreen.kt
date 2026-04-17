package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.ui.components.CircularTimer
import com.kidfocus.timer.ui.components.MascotWidget
import com.kidfocus.timer.ui.theme.FocusBlue
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import com.kidfocus.timer.ui.viewmodel.TimerViewModel

/**
 * Full-screen focus session screen.
 *
 * Observes [TimerViewModel.completedSessionMinutes] to fire [onSessionComplete] exactly once
 * when the countdown reaches zero.
 */
@Composable
fun FocusScreen(
    timerViewModel: TimerViewModel,
    settingsViewModel: SettingsViewModel,
    onSessionComplete: (Int) -> Unit,
    onStop: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val timerState by timerViewModel.timerState.collectAsState()
    val completedMinutes by timerViewModel.completedSessionMinutes.collectAsState()

    // Navigate away when session completes
    LaunchedEffect(completedMinutes) {
        if (completedMinutes > 0) {
            timerViewModel.consumeCompletedSession()
            onSessionComplete(completedMinutes)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Thời gian tập trung",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (timerState.isWarning) "Gần xong rồi! Cố lên!" else "Hãy giữ sự tập trung nhé!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }

            // Timer + Mascot
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularTimer(
                    progress = timerState.progress,
                    timeText = timerState.timeFormatted,
                    arcColor = colors.focusArc,
                    isWarning = timerState.isWarning,
                    size = 280.dp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                MascotWidget(
                    phase = TimerPhase.Focus,
                    isRunning = timerState.isRunning,
                    size = 100.dp,
                )
            }

            // Controls
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Stop button
                    Surface(
                        shape = CircleShape,
                        color = colors.onBackground.copy(alpha = 0.1f),
                        modifier = Modifier.size(56.dp),
                        onClick = onStop,
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Dừng",
                                tint = colors.onBackground,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }

                    // Pause / Resume button
                    Surface(
                        shape = CircleShape,
                        color = colors.primary,
                        modifier = Modifier.size(72.dp),
                        onClick = {
                            if (timerState.isRunning) timerViewModel.pause()
                            else timerViewModel.resume()
                        },
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (timerState.isRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (timerState.isRunning) "Tạm dừng" else "Tiếp tục",
                                tint = colors.onPrimary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                    }
                }

                if (timerState.isPaused) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Đang tạm dừng",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onBackground.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
