package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.schedule.*
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.components.TaskVisual
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import com.kidfocus.timer.ui.viewmodel.ScheduleAlarmPermissionViewModel
import com.kidfocus.timer.ui.components.ScheduleExactAlarmReminder
import java.util.Calendar

private fun buildTimeline(tasks: List<ScheduledTask>, anchors: ScheduleAnchors, day: java.time.DayOfWeek): List<ScheduleTimelineItem> =
    buildScheduleTimeline(tasks, anchors, day).items

@Composable
fun DailyScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onStartTask: (ScheduledTask) -> Unit,
    onActualAndComparison: (java.time.LocalDate) -> Unit = {},
    quickEntryEnabled: Boolean = false,
    onQuickEntry: () -> Unit = {},
    onAddTaskAtTime: (hour: Int, minute: Int) -> Unit = { _, _ -> },
    alarmPermission: ScheduleAlarmPermissionViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
) {
    val colors = KidFocusTheme.colors
    val tasks by viewModel.tasks.collectAsState()
    val anchors by viewModel.anchors.collectAsState()

    val today = Calendar.getInstance()
    // offset from today: 0=today, -1=yesterday, +1=tomorrow (within the week)
    var dayOffset by remember { mutableIntStateOf(0) }

    val displayCal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, dayOffset) }
    val displayDow = displayCal.get(Calendar.DAY_OF_WEEK) // Calendar.MONDAY..SUNDAY

    val nowHour = today.get(Calendar.HOUR_OF_DAY)
    val nowMin = today.get(Calendar.MINUTE)
    val isToday = dayOffset == 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = colors.onBackground)
                }
                Text(
                    text = stringResource(R.string.daily_schedule_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            androidx.compose.material3.TextButton(
                onClick = { onActualAndComparison(java.time.LocalDate.of(
                    displayCal.get(Calendar.YEAR), displayCal.get(Calendar.MONTH) + 1, displayCal.get(Calendar.DAY_OF_MONTH))) },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) { Text(stringResource(R.string.daylog_actual_and_compare)) }

            if (quickEntryEnabled) androidx.compose.material3.TextButton(onClick = onQuickEntry, modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.quick_title))
            }

            ScheduleExactAlarmReminder(alarmPermission)

            // Week strip
            WeekStrip(
                selectedOffset = dayOffset,
                tasks = tasks,
                onDaySelected = { dayOffset = it },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Day label + nav
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = { dayOffset-- }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.daily_previous_day), tint = colors.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isToday) stringResource(R.string.daily_today)
                        else if (dayOffset == 1) stringResource(R.string.daily_tomorrow)
                        else if (dayOffset == -1) stringResource(R.string.daily_yesterday)
                        else dowLabel(displayDow),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "%02d/%02d".format(displayCal.get(Calendar.DAY_OF_MONTH), displayCal.get(Calendar.MONTH) + 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.5f),
                    )
                }
                IconButton(onClick = { dayOffset++ }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.daily_next_day), tint = colors.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val timeline = buildTimeline(tasks, anchors, DayCodec.fromCalendar(displayDow))
            if (timeline.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.daily_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onBackground.copy(alpha = 0.5f),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(timeline) { item ->
                        when (item) {
                            is ScheduleTimelineItem.Task -> {
                                val task = item.task
                                val taskMinutes = item.start
                                val nowMinutes = nowHour * 60 + nowMin
                                val isPast = isToday && taskMinutes + task.focusDurationMinutes < nowMinutes
                                val isCurrent = isToday && taskMinutes <= nowMinutes && nowMinutes < taskMinutes + task.focusDurationMinutes
                                TimelineTaskItem(
                                    task = task,
                                    timeLabel = timelineTime(item.start),
                                    isPast = isPast,
                                    isCurrent = isCurrent,
                                    onStart = { onStartTask(task) },
                                )
                            }
                            is ScheduleTimelineItem.Anchor -> {
                                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                                    Text(stringResource(if (item.kind == ScheduleTimelineItem.Kind.SLEEP) R.string.smart_sleep_block else R.string.smart_school_title),
                                        style = MaterialTheme.typography.titleMedium)
                                    item.label?.let { Text(it) }
                                    Text(stringResource(R.string.smart_time_range, timelineTime(item.start), timelineTime(item.end)))
                                }
                            }
                            is ScheduleTimelineItem.Gap -> {
                                EmptySlotItem(
                                    hour = Math.floorMod(item.start, 1440) / 60,
                                    minute = Math.floorMod(item.start, 1440) % 60,
                                    onAdd = { onAddTaskAtTime(Math.floorMod(item.start, 1440) / 60, Math.floorMod(item.start, 1440) % 60) },
                                )
                            }
                        }
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun WeekStrip(
    selectedOffset: Int,
    tasks: List<ScheduledTask>,
    onDaySelected: (Int) -> Unit,
) {
    val colors = KidFocusTheme.colors

    LazyRow(
        modifier = Modifier.testTag("daily_week_strip")
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items((-3..3).toList()) { offset ->
            val cal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, offset) }
            val dow = cal.get(Calendar.DAY_OF_WEEK)
            val dayNum = cal.get(Calendar.DAY_OF_MONTH)
            val isSelected = offset == selectedOffset
            val hasTasks = tasks.any { it.enabled && dow in it.daysOfWeek }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) colors.primary else colors.surface)
                    .clickable { onDaySelected(offset) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = dowShort(dow),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) colors.onPrimary else colors.onBackground.copy(alpha = 0.5f),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dayNum.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) colors.onPrimary else colors.onBackground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !hasTasks -> colors.surface
                                isSelected -> colors.onPrimary.copy(alpha = 0.7f)
                                else -> colors.primary
                            }
                        ),
                )
            }
        }
    }
}

@Composable
private fun TimelineTaskItem(
    task: ScheduledTask,
    timeLabel: String,
    isPast: Boolean,
    isCurrent: Boolean,
    onStart: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val alpha = if (isPast) 0.4f else 1f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Time label
        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onBackground.copy(alpha = 0.5f * alpha),
            modifier = Modifier
                .width(48.dp)
                .padding(top = 14.dp),
        )

        // Timeline line + dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrent) colors.primary
                        else if (isPast) colors.onBackground.copy(alpha = 0.2f)
                        else colors.primary.copy(alpha = 0.4f)
                    )
                    .then(
                        if (isCurrent) Modifier.border(2.dp, colors.primary.copy(alpha = 0.3f), CircleShape)
                        else Modifier
                    )
                    .padding(top = 14.dp),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(60.dp)
                    .background(colors.onBackground.copy(alpha = 0.08f)),
            )
        }

        // Task card
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isCurrent) colors.primary.copy(alpha = 0.12f)
                    else colors.surface.copy(alpha = if (isPast) 0.5f else 1f)
                )
                .then(
                    if (isCurrent) Modifier.border(1.5.dp, colors.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    else Modifier
                )
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.testTag("daily_task_visual_${task.id}")) {
                    TaskVisual(
                        photoUri = task.photoUri,
                        emoji = task.emoji,
                        modifier = Modifier.size(40.dp),
                        emojiSize = 22.sp,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = task.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onBackground.copy(alpha = alpha),
                        )
                        if (isCurrent) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(stringResource(R.string.daily_current), style = MaterialTheme.typography.labelSmall, color = colors.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.daily_task_duration,
                            task.focusDurationMinutes,
                            task.breakDurationMinutes,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.5f * alpha),
                    )
                }
                if (!isPast) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = if (isCurrent) 1f else 0.15f))
                            .clickable { onStart() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.daily_start),
                            tint = if (isCurrent) colors.onPrimary else colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySlotItem(hour: Int, minute: Int, onAdd: () -> Unit) {
    val colors = KidFocusTheme.colors
    val timeStr = "%02d:%02d".format(hour, minute)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Time label
        Text(
            text = timeStr,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onBackground.copy(alpha = 0.25f),
            modifier = Modifier.width(48.dp),
        )

        // Dotted line connector
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colors.onBackground.copy(alpha = 0.1f)),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(40.dp)
                    .background(colors.onBackground.copy(alpha = 0.06f)),
            )
        }

        // Add button card
        OutlinedCard(
            modifier = Modifier
                .weight(1f)
                .clickable { onAdd() },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.25f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.daily_add_at, timeStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary.copy(alpha = 0.6f),
                )
            }
        }
    }
}

private fun dowLabel(dow: Int) = java.text.DateFormatSymbols.getInstance().weekdays
    .getOrElse(dow) { "" }

private fun dowShort(dow: Int) = java.text.DateFormatSymbols.getInstance().shortWeekdays
    .getOrElse(dow) { "" }

@Composable
private fun timelineTime(minute: Int): String {
    val time = timeAtMinute(minute).toString()
    return when {
        minute >= 1440 -> stringResource(R.string.smart_next_day_time, time)
        minute < 0 -> stringResource(R.string.smart_previous_day_time, time)
        else -> time
    }
}
