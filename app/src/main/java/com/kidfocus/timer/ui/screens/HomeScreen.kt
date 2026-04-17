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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.ui.components.MascotWidget
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.HomeViewModel
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import com.kidfocus.timer.ui.viewmodel.TimerViewModel

/**
 * Home screen displaying today's stats, the mascot, and the start focus button.
 */
@Composable
fun HomeScreen(
    timerViewModel: TimerViewModel,
    settingsViewModel: SettingsViewModel,
    onStartFocus: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenParentSettings: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val colors = KidFocusTheme.colors
    val settings by settingsViewModel.settings.collectAsState()
    val focusMinutes by homeViewModel.todayFocusMinutes.collectAsState()
    val focusCount by homeViewModel.todayFocusCount.collectAsState()
    val goalProgress by homeViewModel.dailyGoalProgress.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "KidFocus",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.primary,
                    fontWeight = FontWeight.Bold,
                )
                Row {
                    IconButton(onClick = onOpenTheme) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Chủ đề",
                            tint = colors.primary,
                        )
                    }
                    IconButton(onClick = onOpenParentSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Cài đặt phụ huynh",
                            tint = colors.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mascot
            MascotWidget(
                phase = TimerPhase.Idle,
                isRunning = false,
                size = 140.dp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Xin chào! Sẵn sàng học chưa?",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Stats cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    label = "Phút tập trung",
                    value = "$focusMinutes",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = "Phiên hôm nay",
                    value = "$focusCount",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Daily goal progress bar
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Mục tiêu hôm nay",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "${(goalProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.primary.copy(alpha = 0.15f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(goalProgress)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.primary),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Timer settings summary
            val focus = settings?.focusDurationMinutes ?: 25
            val breakMin = settings?.breakDurationMinutes ?: 5

            Text(
                text = "Tập trung $focus phút • Nghỉ $breakMin phút",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Start button
            Button(
                onClick = {
                    val seconds = (settings?.focusDurationMinutes ?: 25) * 60
                    timerViewModel.startFocus(seconds)
                    onStartFocus()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(
                    text = "Bắt đầu tập trung!",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = KidFocusTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.primary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}
