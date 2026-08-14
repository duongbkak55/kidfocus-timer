package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.kidfocus.timer.R
import com.kidfocus.timer.BuildConfig
import androidx.hilt.navigation.compose.hiltViewModel
import com.kidfocus.timer.data.cloud.CloudAccount
import com.kidfocus.timer.data.cloud.CloudSyncStatus
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.ui.components.AccountAvatar
import com.kidfocus.timer.ui.components.MascotWidget
import com.kidfocus.timer.ui.components.TodayRoutinesSection
import com.kidfocus.timer.ui.components.TaskVisual
import com.kidfocus.timer.ui.components.childProfileName
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.HomeViewModel
import com.kidfocus.timer.ui.viewmodel.RoutineViewModel
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import com.kidfocus.timer.ui.viewmodel.TimerViewModel
import java.util.Calendar
import com.kidfocus.timer.data.database.ChildProfileEntity

/**
 * Home screen displaying today's stats, the mascot, and the start focus button.
 */
@Composable
fun HomeScreen(
    timerViewModel: TimerViewModel,
    settingsViewModel: SettingsViewModel,
    scheduleViewModel: ScheduleViewModel,
    onStartFocus: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenParentSettings: () -> Unit,
    onOpenDailySchedule: () -> Unit = {},
    onOpenAiChat: () -> Unit = {},
    onOpenLearning: () -> Unit = {},
    onOpenCloudSync: () -> Unit = {},
    onStartRoutine: () -> Unit = {},
    onOpenRoutineProgress: () -> Unit = {},
    cloudAccount: CloudAccount = CloudAccount(configured = false),
    cloudSyncStatus: CloudSyncStatus = CloudSyncStatus.LocalOnly,
    homeViewModel: HomeViewModel = hiltViewModel(),
    routineViewModel: RoutineViewModel = hiltViewModel(),
    childProfile: ChildProfileEntity = ChildProfileEntity.default(),
    onOpenProfilePicker: () -> Unit = {},
) {
    val colors = KidFocusTheme.colors
    val activeChildName = childProfileName(childProfile)
    val settings by settingsViewModel.settings.collectAsState()
    val focusMinutes by homeViewModel.todayFocusMinutes.collectAsState()
    val focusCount by homeViewModel.todayFocusCount.collectAsState()
    val goalProgress by homeViewModel.dailyGoalProgress.collectAsState()
    val allTasks by scheduleViewModel.tasks.collectAsState()
    val todayDow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val todayTasks = allTasks
        .filter { it.enabled && todayDow in it.daysOfWeek }
        .sortedWith(compareBy({ it.hour }, { it.minute }))
    val todayRoutines by routineViewModel.todayRoutines.collectAsState()

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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (cloudAccount.isSignedIn) {
                        AccountAvatar(
                            account = cloudAccount,
                            size = 38.dp,
                            showOnlineDot = cloudSyncStatus is CloudSyncStatus.Synced,
                            modifier = Modifier.clickable(onClick = onOpenCloudSync),
                        )
                    }
                    IconButton(onClick = onOpenTheme) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = stringResource(R.string.home_theme),
                            tint = colors.primary,
                        )
                    }
                    IconButton(onClick = onOpenParentSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.home_parent_settings),
                            tint = colors.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable(onClick = onOpenProfilePicker),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(childProfile.avatarEmoji, fontSize = 26.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        activeChildName,
                        modifier = Modifier.weight(1f),
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.profile_switch),
                        color = colors.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mascot
            MascotWidget(
                phase = TimerPhase.Idle,
                isRunning = false,
                size = 140.dp,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = cloudAccount.displayName
                    ?.substringBefore(' ')
                    ?.takeIf { cloudAccount.isSignedIn }
                    ?.let { "Xin chào, $it! Sẵn sàng học chưa?" }
                    ?: "Xin chào! Sẵn sàng học chưa?",
                style = MaterialTheme.typography.titleMedium,
                color = colors.onBackground.copy(alpha = 0.7f),
            )

            if (cloudAccount.isSignedIn) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = when (cloudSyncStatus) {
                        CloudSyncStatus.Syncing -> "☁️ Đang đồng bộ dữ liệu…"
                        is CloudSyncStatus.Synced -> "☁️ Đã đăng nhập • Dữ liệu được đồng bộ"
                        is CloudSyncStatus.Error -> "⚠️ Đã đăng nhập • Chờ đồng bộ lại"
                        else -> "☁️ Đã đăng nhập"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onOpenCloudSync),
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Stats cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatCard(
                    label = stringResource(R.string.home_today_focus_minutes),
                    value = "$focusMinutes",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    label = stringResource(R.string.home_today_sessions),
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
                            text = stringResource(R.string.home_daily_goal),
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

            Spacer(modifier = Modifier.height(16.dp))

            // Today's schedule preview
            if (todayTasks.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDailySchedule() },
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.home_schedule_today),
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.home_activity_count, todayTasks.size),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onBackground.copy(alpha = 0.5f),
                                )
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = colors.onBackground.copy(alpha = 0.4f),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val nowMin = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
                        val upcoming = todayTasks.filter { it.hour * 60 + it.minute >= nowMin }.take(3)
                        val preview = upcoming.ifEmpty { todayTasks.take(3) }
                        preview.forEach { task ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                TaskVisual(
                                    photoUri = task.photoUri,
                                    emoji = task.emoji,
                                    modifier = Modifier.size(28.dp),
                                    emojiSize = 14.sp,
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    text = task.timeFormatted,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.onBackground.copy(alpha = 0.45f),
                                )
                                Spacer(Modifier.size(6.dp))
                                Text(
                                    text = task.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onBackground.copy(alpha = 0.8f),
                                )
                            }
                        }
                        if (todayTasks.size > 3) {
                            Text(
                                text = stringResource(R.string.home_more_activities, todayTasks.size - 3),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primary.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            TodayRoutinesSection(
                routines = todayRoutines,
                onStartRoutine = onStartRoutine,
                onOpenProgress = onOpenRoutineProgress,
                onComplete = routineViewModel::complete,
                onStartTimer = { todayRoutine ->
                    todayRoutine.routine.linkedTimerMinutes?.let { minutes ->
                        timerViewModel.startFocusForRoutine(
                            routineId = todayRoutine.routine.id,
                            occurrenceDate = todayRoutine.occurrenceDate,
                            totalSeconds = minutes * 60,
                        )
                        onStartFocus()
                    }
                },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Timer settings summary
            val focus = settings?.focusDurationMinutes ?: 25
            val breakMin = settings?.breakDurationMinutes ?: 5

            Text(
                text = stringResource(R.string.home_timer_summary, focus, breakMin),
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
                    text = stringResource(R.string.home_start_focus),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.primary.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLearning),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🎓", fontSize = 28.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.learning_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                        Text(
                            text = stringResource(R.string.learning_home_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onBackground.copy(alpha = 0.65f),
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = colors.primary,
                    )
                }
            }

            if (BuildConfig.ENABLE_AI_CHAT) {
                Spacer(modifier = Modifier.height(12.dp))

                // Development-only until the Play Families AI safeguards are complete.
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAiChat() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🦉", fontSize = 24.sp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_ai_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primary,
                            )
                            Text(
                                text = stringResource(R.string.home_ai_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onBackground.copy(alpha = 0.5f),
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = colors.primary,
                        )
                    }
                }
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
