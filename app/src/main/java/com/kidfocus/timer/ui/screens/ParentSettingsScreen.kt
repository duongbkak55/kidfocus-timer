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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.domain.model.TimerSettings
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.CloudSyncStatus
import androidx.compose.material3.CircularProgressIndicator
import com.kidfocus.timer.R
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
    onOpenRoutineSettings: () -> Unit = {},
    onOpenCloudSync: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onOpenChildProfiles: () -> Unit = {},
    cloudAccount: CloudAccount = CloudAccount(configured = false),
    cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.LocalOnly,
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
                        contentDescription = stringResource(R.string.back),
                        tint = colors.onBackground,
                    )
                }
                Text(
                    text = stringResource(R.string.parent_settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(text = stringResource(R.string.parent_section_children))
            Spacer(modifier = Modifier.height(12.dp))
            SettingRow(
                icon = "🧒",
                title = stringResource(R.string.parent_manage_children),
                subtitle = stringResource(R.string.parent_manage_children_subtitle),
                onClick = onOpenChildProfiles,
                trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Schedule
            SectionHeader(text = stringResource(R.string.parent_section_study_schedule))
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
                    text = stringResource(R.string.parent_manage_study_schedule),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Timer durations
            SectionHeader(text = stringResource(R.string.parent_section_timer))
            Spacer(modifier = Modifier.height(12.dp))

            SettingSliderRow(
                title = stringResource(R.string.parent_focus_duration),
                value = current.focusDurationMinutes.toFloat(),
                valueRange = TimerSettings.MIN_FOCUS_MINUTES.toFloat()..TimerSettings.MAX_FOCUS_MINUTES.toFloat(),
                onValueChange = { settingsViewModel.setFocusDuration(it.toInt()) },
                valueLabel = stringResource(R.string.parent_minutes_format, current.focusDurationMinutes),
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingSliderRow(
                title = stringResource(R.string.parent_break_duration),
                value = current.breakDurationMinutes.toFloat(),
                valueRange = TimerSettings.MIN_BREAK_MINUTES.toFloat()..TimerSettings.MAX_BREAK_MINUTES.toFloat(),
                onValueChange = { settingsViewModel.setBreakDuration(it.toInt()) },
                valueLabel = stringResource(R.string.parent_minutes_format, current.breakDurationMinutes),
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingSliderRow(
                title = stringResource(R.string.parent_daily_goal),
                value = current.dailyGoalMinutes.toFloat(),
                valueRange = TimerSettings.MIN_DAILY_GOAL_MINUTES.toFloat()..TimerSettings.MAX_DAILY_GOAL_MINUTES.toFloat(),
                onValueChange = { settingsViewModel.updateDailyGoal(it.toInt()) },
                valueLabel = stringResource(R.string.parent_minutes_format, current.dailyGoalMinutes),
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(text = stringResource(R.string.parent_section_routines))
            Spacer(modifier = Modifier.height(12.dp))
            SettingRow(
                icon = "📅",
                title = stringResource(R.string.parent_manage_routines),
                subtitle = stringResource(R.string.parent_manage_routines_subtitle),
                onClick = onOpenRoutineSettings,
                trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            SectionHeader(text = stringResource(R.string.parent_section_account))
            Spacer(modifier = Modifier.height(12.dp))
            SettingRow(
                icon = if (cloudAccount.isSignedIn) "✅" else "☁️",
                title = if (cloudAccount.isSignedIn) {
                    cloudAccount.displayName ?: stringResource(R.string.parent_signed_in)
                } else {
                    stringResource(R.string.parent_multi_device_sync)
                },
                subtitle = if (cloudAccount.isSignedIn) {
                    val syncLabel = when (cloudSyncStatus) {
                        CloudSyncStatus.Syncing -> stringResource(R.string.sync_state_syncing)
                        is CloudSyncStatus.Synced -> stringResource(R.string.sync_state_synced)
                        is CloudSyncStatus.Error -> stringResource(R.string.sync_state_retry)
                        else -> stringResource(R.string.sync_state_signed_in)
                    }
                    listOfNotNull(cloudAccount.email, syncLabel).joinToString(" • ")
                } else {
                    stringResource(R.string.parent_cloud_signed_out_subtitle)
                },
                onClick = onOpenCloudSync,
                trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) },
            )

            Spacer(modifier = Modifier.height(12.dp))
            SettingRow(
                icon = "🌟",
                title = stringResource(R.string.parent_premium_title),
                subtitle = stringResource(R.string.parent_premium_subtitle),
                onClick = onOpenSubscription,
                trailingContent = { Text("›", style = MaterialTheme.typography.headlineSmall) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: Notifications
            SectionHeader(text = stringResource(R.string.parent_section_notifications))
            Spacer(modifier = Modifier.height(12.dp))

            SettingToggleRow(
                title = stringResource(R.string.parent_sound_title),
                subtitle = stringResource(R.string.parent_sound_subtitle),
                checked = current.soundEnabled,
                onCheckedChange = { settingsViewModel.setSoundEnabled(it) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingToggleRow(
                title = stringResource(R.string.parent_vibration_title),
                subtitle = stringResource(R.string.parent_vibration_subtitle),
                checked = current.vibrationEnabled,
                onCheckedChange = { settingsViewModel.setVibrationEnabled(it) },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Section: PIN / Parental lock
            SectionHeader(text = stringResource(R.string.parent_section_pin))
            Spacer(modifier = Modifier.height(12.dp))

            SettingRow(
                icon = if (current.hasPinSet) "\uD83D\uDD12" else "\uD83D\uDD13",
                title = stringResource(if (current.hasPinSet) R.string.parent_pin_set else R.string.parent_pin_not_set),
                subtitle = stringResource(
                    if (current.hasPinSet) R.string.parent_pin_set_subtitle else R.string.parent_pin_not_set_subtitle
                ),
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
                    text = stringResource(if (current.hasPinSet) R.string.parent_change_pin else R.string.parent_create_pin),
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
                        text = stringResource(R.string.parent_remove_pin),
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
