package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.ui.components.CircularTimer
import com.kidfocus.timer.ui.components.MascotWidget
import com.kidfocus.timer.ui.theme.BreakGreen
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import com.kidfocus.timer.ui.viewmodel.TimerViewModel

/**
 * Full-screen break timer screen.
 * Calls [onBreakComplete] when the countdown naturally finishes.
 */
@Composable
fun BreakScreen(
    timerViewModel: TimerViewModel,
    settingsViewModel: SettingsViewModel,
    onBreakComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val timerState by timerViewModel.timerState.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()

    // Launch break timer when screen first composes
    LaunchedEffect(Unit) {
        val seconds = (settings?.breakDurationMinutes ?: 5) * 60
        timerViewModel.startBreak(seconds)
        timerViewModel.bindService()
    }

    // Detect natural completion
    LaunchedEffect(timerState.isFinished) {
        if (timerState.isFinished && timerState.phase.isBreak) {
            onBreakComplete()
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "Thời gian nghỉ ngơi",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Thư giãn và lấy lại năng lượng nhé!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground.copy(alpha = 0.6f),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularTimer(
                    progress = timerState.progress,
                    timeText = timerState.timeFormatted,
                    arcColor = BreakGreen,
                    isWarning = false,
                    size = 260.dp,
                )
                Spacer(modifier = Modifier.height(24.dp))
                MascotWidget(
                    phase = TimerPhase.Break,
                    isRunning = true,
                    size = 110.dp,
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 48.dp),
            ) {
                Text(
                    text = "Bạn có thể làm những điều bạn thích trong thời gian này",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBackground.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onSkip,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Bỏ qua nghỉ ngơi")
                }
            }
        }
    }
}
