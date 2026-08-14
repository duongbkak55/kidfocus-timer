package com.kidfocus.timer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.RoutineStatus
import com.kidfocus.timer.domain.model.TodayRoutine
import com.kidfocus.timer.ui.screens.formatTime
import com.kidfocus.timer.ui.theme.KidFocusTheme

@Composable
fun TodayRoutinesSection(
    routines: List<TodayRoutine>,
    onComplete: (TodayRoutine) -> Unit,
    onStartTimer: (TodayRoutine) -> Unit,
    onStartRoutine: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.routine_today_title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onOpenProgress) {
                Text(stringResource(R.string.routine_view_achievements))
            }
        }
        Spacer(Modifier.height(10.dp))
        if (routines.any { !it.isCompleted }) {
            Button(
                onClick = onStartRoutine,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.routine_start_playlist))
            }
            Spacer(Modifier.height(10.dp))
        }
        if (routines.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = colors.surface,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(R.string.routine_today_empty),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface.copy(alpha = 0.65f),
                )
            }
        }
        routines.forEach { routine ->
            TodayRoutineCard(routine, onComplete, onStartTimer)
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TodayRoutineCard(
    todayRoutine: TodayRoutine,
    onComplete: (TodayRoutine) -> Unit,
    onStartTimer: (TodayRoutine) -> Unit,
) {
    val colors = KidFocusTheme.colors
    val statusText = when (todayRoutine.status) {
        RoutineStatus.PENDING -> stringResource(
            R.string.routine_status_before,
            formatTime(todayRoutine.routine.deadlineMinutes),
        )
        RoutineStatus.ON_TIME -> stringResource(R.string.routine_status_on_time)
        RoutineStatus.LATE -> stringResource(R.string.routine_status_late)
        RoutineStatus.MISSED -> stringResource(
            R.string.routine_status_missed,
            formatTime(todayRoutine.routine.deadlineMinutes),
        )
    }
    val statusColor = when (todayRoutine.status) {
        RoutineStatus.ON_TIME -> colors.breakArc
        RoutineStatus.LATE, RoutineStatus.MISSED -> colors.warningArc
        RoutineStatus.PENDING -> colors.primary
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskVisual(
                    photoUri = todayRoutine.routine.photoUri,
                    emoji = todayRoutine.routine.emoji,
                    modifier = Modifier.width(48.dp).height(48.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        todayRoutine.routine.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
                }
                if (todayRoutine.isCompleted) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.routine_completed_description),
                        tint = Color(0xFF22C55E),
                    )
                }
            }

            if (!todayRoutine.isCompleted) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (todayRoutine.routine.linkedTimerMinutes != null) {
                        Button(
                            onClick = { onStartTimer(todayRoutine) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.parent_minutes_format, todayRoutine.routine.linkedTimerMinutes))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onComplete(todayRoutine) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.routine_mark_done))
                        }
                    }
                }
            }
        }
    }
}
