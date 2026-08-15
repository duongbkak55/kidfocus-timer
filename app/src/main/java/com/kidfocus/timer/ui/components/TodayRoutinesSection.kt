package com.kidfocus.timer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
            Spacer(Modifier.height(8.dp))
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
    val statusIcon = when (todayRoutine.status) {
        RoutineStatus.PENDING -> Icons.Default.AccessTime
        RoutineStatus.ON_TIME -> Icons.Default.CheckCircle
        RoutineStatus.LATE -> Icons.Default.WarningAmber
        RoutineStatus.MISSED -> Icons.Default.ErrorOutline
    }
    val markDoneDescription = stringResource(
        R.string.routine_mark_done_description,
        todayRoutine.routine.title,
    )
    val completedStateDescription = stringResource(R.string.routine_completed_description)
    val notCompletedDescription = stringResource(R.string.routine_not_completed_description)
    val completedItemDescription = stringResource(
        R.string.routine_completed_item_description,
        todayRoutine.routine.title,
    )
    val timerMinutes = todayRoutine.routine.linkedTimerMinutes
    val startTimerDescription = timerMinutes?.let {
        stringResource(
            R.string.routine_start_timer_description,
            it,
            todayRoutine.routine.title,
        )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TaskVisual(
                photoUri = todayRoutine.routine.photoUri,
                emoji = todayRoutine.routine.emoji,
                modifier = Modifier.width(44.dp).height(44.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(
                    todayRoutine.routine.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))

            when {
                todayRoutine.isCompleted -> {
                    Surface(
                        modifier = Modifier
                            .width(48.dp)
                            .height(48.dp)
                            .semantics {
                                role = Role.Checkbox
                                stateDescription = completedStateDescription
                                contentDescription = completedItemDescription
                            },
                        shape = CircleShape,
                        color = Color(0xFF22C55E).copy(alpha = 0.12f),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                            )
                        }
                    }
                }
                timerMinutes != null -> {
                    Button(
                        onClick = { onStartTimer(todayRoutine) },
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(min = 72.dp)
                            .semantics { contentDescription = startTimerDescription.orEmpty() },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("$timerMinutes′")
                    }
                }
                else -> {
                    IconToggleButton(
                        checked = false,
                        onCheckedChange = { checked ->
                            if (checked) onComplete(todayRoutine)
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics {
                                role = Role.Checkbox
                                stateDescription = notCompletedDescription
                                contentDescription = markDoneDescription
                            },
                    ) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}
