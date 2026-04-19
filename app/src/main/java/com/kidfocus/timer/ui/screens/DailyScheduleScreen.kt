package com.kidfocus.timer.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import java.util.Calendar

@Composable
fun DailyScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onStartTask: (ScheduledTask) -> Unit,
) {
    val colors = KidFocusTheme.colors
    val tasks by viewModel.tasks.collectAsState()

    val today = Calendar.getInstance()
    // offset from today: 0=today, -1=yesterday, +1=tomorrow (within the week)
    var dayOffset by remember { mutableIntStateOf(0) }

    val displayCal = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, dayOffset) }
    val displayDow = displayCal.get(Calendar.DAY_OF_WEEK) // Calendar.MONDAY..SUNDAY

    val tasksForDay = tasks
        .filter { it.enabled && displayDow in it.daysOfWeek }
        .sortedWith(compareBy({ it.hour }, { it.minute }))

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại", tint = colors.onBackground)
                }
                Text(
                    text = "Lịch trong ngày",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

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
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Ngày trước", tint = colors.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isToday) "Hôm nay" else if (dayOffset == 1) "Ngày mai" else if (dayOffset == -1) "Hôm qua" else dowVietnamese(displayDow),
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
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Ngày sau", tint = colors.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (tasksForDay.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Không có lịch hôm nay!",
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
                    items(tasksForDay) { task ->
                        val taskMinutes = task.hour * 60 + task.minute
                        val nowMinutes = nowHour * 60 + nowMin
                        val isPast = isToday && taskMinutes + task.focusDurationMinutes < nowMinutes
                        val isCurrent = isToday && taskMinutes <= nowMinutes && nowMinutes < taskMinutes + task.focusDurationMinutes

                        TimelineTaskItem(
                            task = task,
                            isPast = isPast,
                            isCurrent = isCurrent,
                            onStart = { onStartTask(task) },
                        )
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
    val today = Calendar.getInstance()

    LazyRow(
        modifier = Modifier
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
            text = task.timeFormatted,
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
                Text(text = task.emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
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
                                Text("Đang diễn ra", style = MaterialTheme.typography.labelSmall, color = colors.onPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Text(
                        text = "${task.focusDurationMinutes} phút tập trung · ${task.breakDurationMinutes} phút nghỉ",
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
                            contentDescription = "Bắt đầu",
                            tint = if (isCurrent) colors.onPrimary else colors.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun dowVietnamese(dow: Int) = when (dow) {
    Calendar.MONDAY -> "Thứ Hai"
    Calendar.TUESDAY -> "Thứ Ba"
    Calendar.WEDNESDAY -> "Thứ Tư"
    Calendar.THURSDAY -> "Thứ Năm"
    Calendar.FRIDAY -> "Thứ Sáu"
    Calendar.SATURDAY -> "Thứ Bảy"
    Calendar.SUNDAY -> "Chủ Nhật"
    else -> ""
}

private fun dowShort(dow: Int) = when (dow) {
    Calendar.MONDAY -> "T2"
    Calendar.TUESDAY -> "T3"
    Calendar.WEDNESDAY -> "T4"
    Calendar.THURSDAY -> "T5"
    Calendar.FRIDAY -> "T6"
    Calendar.SATURDAY -> "T7"
    Calendar.SUNDAY -> "CN"
    else -> ""
}
