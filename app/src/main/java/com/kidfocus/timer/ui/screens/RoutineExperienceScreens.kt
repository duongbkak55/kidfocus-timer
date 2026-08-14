package com.kidfocus.timer.ui.screens

import android.view.accessibility.AccessibilityManager

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.RoutineWeeklySummary
import com.kidfocus.timer.domain.model.TodayRoutine
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.components.TaskVisual
import com.kidfocus.timer.ui.viewmodel.RoutineViewModel
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel
import com.kidfocus.timer.ui.learning.LearningTtsController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import java.util.Locale

@Composable
fun RoutineRunnerScreen(
    viewModel: RoutineViewModel,
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
    onStartTimer: (TodayRoutine) -> Unit,
    onOpenProgress: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val routines by viewModel.todayRoutines.collectAsState()
    val summary by viewModel.weeklySummary.collectAsState()
    val current = routines.firstOrNull { !it.isCompleted }
    val currentIndex = current?.let(routines::indexOf) ?: routines.size
    val completedCount = routines.count { it.isCompleted }
    val progress = if (routines.isEmpty()) 0f else completedCount / routines.size.toFloat()
    val settings by settingsViewModel.settings.collectAsState()
    val calmMode = settings?.calmModeEnabled ?: false
    val context = LocalContext.current
    val tts = remember(context.applicationContext) {
        LearningTtsController(context.applicationContext)
    }
    val touchExplorationEnabled = remember(context.applicationContext) {
        context.getSystemService(AccessibilityManager::class.java)
            ?.isTouchExplorationEnabled == true
    }

    DisposableEffect(tts) {
        onDispose { tts.close() }
    }
    LaunchedEffect(current?.routine?.id, settings?.soundEnabled, touchExplorationEnabled) {
        if (current != null && settings?.soundEnabled != false && !touchExplorationEnabled) {
            tts.speak(
                text = current.routine.title,
                languageTag = Locale.getDefault().toLanguageTag(),
                fallbackText = current.routine.title,
                fallbackLanguageTag = "en-US",
                rate = 0.85f,
                pitch = 1.1f,
            )
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RoutineExperienceHeader(stringResource(R.string.routine_runner_title), onBack)
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.calm_mode_title),
                        color = colors.onBackground,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(R.string.calm_mode_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.65f),
                    )
                }
                Switch(
                    checked = calmMode,
                    onCheckedChange = settingsViewModel::setCalmModeEnabled,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.routine_progress_format, completedCount, routines.size),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(10.dp),
                color = colors.primary,
                trackColor = colors.primary.copy(alpha = 0.16f),
            )
            Spacer(Modifier.height(24.dp))

            when {
                routines.isEmpty() -> RoutineRunnerEmpty()
                current == null -> RoutineRunnerCompleted(summary, onOpenProgress)
                else -> {
                    CurrentRoutineCard(
                        routine = current,
                        calmMode = calmMode,
                        onComplete = { viewModel.complete(current) },
                        onStartTimer = { onStartTimer(current) },
                    )
                    routines.drop(currentIndex + 1).firstOrNull { !it.isCompleted }?.let { next ->
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surface,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TaskVisual(
                                    photoUri = next.routine.photoUri,
                                    emoji = next.routine.emoji,
                                    modifier = Modifier.size(52.dp),
                                    emojiSize = 28.sp,
                                )
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.routine_next_step),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.primary,
                                    )
                                    Text(
                                        next.routine.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = colors.onSurface,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CurrentRoutineCard(
    routine: TodayRoutine,
    calmMode: Boolean,
    onComplete: () -> Unit,
    onStartTimer: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.routine_current_step),
                style = MaterialTheme.typography.titleSmall,
                color = colors.primary,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            TaskVisual(
                photoUri = routine.routine.photoUri,
                emoji = routine.routine.emoji,
                modifier = Modifier.size(180.dp),
                emojiSize = 72.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                routine.routine.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (calmMode) stringResource(R.string.calm_mode_encouragement)
                else stringResource(R.string.routine_finish_before, formatTime(routine.routine.deadlineMinutes)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface.copy(alpha = 0.68f),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = if (routine.routine.linkedTimerMinutes != null) onStartTimer else onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    if (routine.routine.linkedTimerMinutes != null) Icons.Default.PlayArrow else Icons.Default.Check,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    routine.routine.linkedTimerMinutes?.let {
                        stringResource(R.string.routine_start_timer_format, it)
                    } ?: stringResource(R.string.routine_mark_done),
                )
            }
        }
    }
}

@Composable
private fun RoutineRunnerEmpty() {
    val colors = KidFocusTheme.colors
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.routine_runner_empty),
            modifier = Modifier.padding(24.dp),
            color = colors.onSurface,
        )
    }
}

@Composable
private fun RoutineRunnerCompleted(
    summary: RoutineWeeklySummary,
    onOpenProgress: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    Surface(shape = RoundedCornerShape(24.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 72.sp, modifier = Modifier.clearAndSetSemantics { })
            Text(
                stringResource(R.string.routine_all_done_title),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.routine_all_done_message, summary.unlockedStickerCount),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface.copy(alpha = 0.68f),
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onOpenProgress) {
                Text(stringResource(R.string.routine_view_stickers))
            }
        }
    }
}

@Composable
fun RoutineProgressScreen(
    viewModel: RoutineViewModel,
    onBack: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val summary by viewModel.weeklySummary.collectAsState()
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            RoutineExperienceHeader(stringResource(R.string.routine_progress_title), onBack)
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.routine_sticker_board),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.routine_sticker_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(12.dp))
            StickerBoard(summary)
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.routine_weekly_summary),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(12.dp))
            WeeklySummaryCard(summary)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StickerBoard(summary: RoutineWeeklySummary) {
    val colors = KidFocusTheme.colors
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RoutineWeeklySummary.STICKER_MILESTONES.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { milestone ->
                        val unlocked = summary.totalCompletedCount >= milestone
                        val accessibilityLabel = if (unlocked) {
                            stringResource(R.string.routine_sticker_unlocked_description, milestone)
                        } else {
                            stringResource(R.string.routine_sticker_locked_description, milestone)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    this.contentDescription = accessibilityLabel
                                },
                        ) {
                            Text(if (unlocked) "⭐" else "🔒", fontSize = 34.sp)
                            Text(
                                stringResource(R.string.routine_sticker_milestone, milestone),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklySummaryCard(summary: RoutineWeeklySummary) {
    val colors = KidFocusTheme.colors
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryRow(stringResource(R.string.routine_week_completed), summary.completedCount.toString())
            SummaryRow(stringResource(R.string.routine_week_on_time), summary.onTimeCount.toString())
            SummaryRow(stringResource(R.string.routine_week_late), summary.lateCount.toString())
            SummaryRow(stringResource(R.string.routine_week_streak), stringResource(R.string.routine_days_format, summary.currentStreakDays))
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    val colors = KidFocusTheme.colors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = colors.onSurface)
        Text(value, color = colors.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RoutineExperienceHeader(title: String, onBack: () -> Unit) {
    val colors = KidFocusTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = colors.onBackground,
            )
        }
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
    }
}
