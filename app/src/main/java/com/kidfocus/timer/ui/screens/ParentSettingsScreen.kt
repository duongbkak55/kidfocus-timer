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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.domain.model.TimerSettings
import androidx.compose.material3.CircularProgressIndicator
import com.kidfocus.timer.ui.components.SettingRow
import com.kidfocus.timer.ui.components.SettingSliderRow
import com.kidfocus.timer.ui.components.SettingToggleRow
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel

/**
 * Parent-only settings screen, accessible only after PIN verification.
 */
@Composable
fun ParentSettingsScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onSetPin: () -> Unit,
    onOpenSchedule: () -> Unit = {},
) {
    val colors = KidFocusTheme.colors
    val settings by settingsViewModel.settings.collectAsState()
    val current = settings ?: run {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KidFocusTheme.colors.primary)
        }
        return
    }

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
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = colors.onBackground,
                    )
                }
                Text(
                    text = "Cài đặt phụ huynh",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Schedule
            SectionHeader(text = "Lịch học")
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onOpenSchedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(
                    text = "📅  Quản lý lịch học",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Timer durations
            SectionHeader(text = "Thời gian hẹn giờ")
            Spacer(modifier = Modifier.height(12.dp))

            SettingSliderRow(
                title = "Thời gian tập trung",
                value = current.focusDurationMinutes.toFloat(),
                valueRange = TimerSettings.MIN_FOCUS_MINUTES.toFloat()..TimerSettings.MAX_FOCUS_MINUTES.toFloat(),
                onValueChange = { settingsViewModel.setFocusDuration(it.toInt()) },
                valueLabel = "${current.focusDurationMinutes} phút",
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingSliderRow(
                title = "Thời gian nghỉ ngơi",
                value = current.breakDurationMinutes.toFloat(),
                valueRange = TimerSettings.MIN_BREAK_MINUTES.toFloat()..TimerSettings.MAX_BREAK_MINUTES.toFloat(),
                onValueChange = { settingsViewModel.setBreakDuration(it.toInt()) },
                valueLabel = "${current.breakDurationMinutes} phút",
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingSliderRow(
                title = "Mục tiêu hằng ngày",
                value = current.dailyGoalMinutes.toFloat(),
                valueRange = TimerSettings.MIN_DAILY_GOAL_MINUTES.toFloat()..TimerSettings.MAX_DAILY_GOAL_MINUTES.toFloat(),
                onValueChange = { settingsViewModel.updateDailyGoal(it.toInt()) },
                valueLabel = "${current.dailyGoalMinutes} phút",
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Notifications
            SectionHeader(text = "Thông báo & Âm thanh")
            Spacer(modifier = Modifier.height(12.dp))

            SettingToggleRow(
                title = "Âm thanh",
                subtitle = "Phát âm thanh khi kết thúc phiên",
                checked = current.soundEnabled,
                onCheckedChange = { settingsViewModel.setSoundEnabled(it) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingToggleRow(
                title = "Rung",
                subtitle = "Rung khi kết thúc phiên",
                checked = current.vibrationEnabled,
                onCheckedChange = { settingsViewModel.setVibrationEnabled(it) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: PIN / Parental lock
            SectionHeader(text = "Khóa phụ huynh")
            Spacer(modifier = Modifier.height(12.dp))

            SettingRow(
                icon = if (current.hasPinSet) "\uD83D\uDD12" else "\uD83D\uDD13",
                title = if (current.hasPinSet) "PIN đã được thiết lập" else "Chưa có PIN",
                subtitle = if (current.hasPinSet) "Cài đặt được bảo vệ" else "Trẻ có thể thay đổi cài đặt",
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onSetPin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(
                    text = if (current.hasPinSet) "Đổi PIN" else "Tạo PIN",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (current.hasPinSet) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { settingsViewModel.clearPin() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        text = "Xóa PIN",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    val colors = KidFocusTheme.colors
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = colors.primary,
        fontWeight = FontWeight.Bold,
    )
}
