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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.ui.components.SettingSliderRow
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import java.util.Calendar

@Composable
fun TaskEditScreen(
    task: ScheduledTask,
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
) {
    val colors = KidFocusTheme.colors

    var name by remember { mutableStateOf(task.name) }
    var hour by remember { mutableIntStateOf(task.hour) }
    var minute by remember { mutableIntStateOf(task.minute) }
    var daysOfWeek by remember { mutableStateOf(task.daysOfWeek) }
    var focusMinutes by remember { mutableIntStateOf(task.focusDurationMinutes) }
    var breakMinutes by remember { mutableIntStateOf(task.breakDurationMinutes) }
    val isNew = task.id == 0L

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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại", tint = colors.onBackground)
                }
                Text(
                    text = if (isNew) "Tạo lịch mới" else "Chỉnh sửa lịch",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Emoji + Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = task.emoji, fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hoạt động") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Time picker
            SectionLabel("Giờ bắt đầu")
            Spacer(modifier = Modifier.height(8.dp))
            TimePickerRow(
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Day selector
            SectionLabel("Ngày trong tuần")
            Spacer(modifier = Modifier.height(8.dp))
            DaySelector(
                selectedDays = daysOfWeek,
                onDaysChange = { daysOfWeek = it },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Duration sliders
            SectionLabel("Thời lượng")
            Spacer(modifier = Modifier.height(8.dp))
            SettingSliderRow(
                title = "Tập trung",
                value = focusMinutes.toFloat(),
                valueRange = 10f..120f,
                onValueChange = { focusMinutes = it.toInt() },
                valueLabel = "$focusMinutes phút",
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingSliderRow(
                title = "Nghỉ giải lao",
                value = breakMinutes.toFloat(),
                valueRange = 5f..30f,
                onValueChange = { breakMinutes = it.toInt() },
                valueLabel = "$breakMinutes phút",
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = {
                    viewModel.saveTask(
                        task.copy(
                            name = name.ifBlank { task.taskType.displayName },
                            hour = hour,
                            minute = minute,
                            daysOfWeek = daysOfWeek.ifEmpty { TaskType.WEEKDAYS },
                            focusDurationMinutes = focusMinutes,
                            breakDurationMinutes = breakMinutes,
                            enabled = true,
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text("Lưu lịch", fontWeight = FontWeight.Bold, color = colors.onPrimary)
            }

            if (!isNew) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.deleteTask(task); onBack() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Xóa lịch", color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = KidFocusTheme.colors.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun TimePickerRow(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    val colors = KidFocusTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TimeSpinner(value = hour, range = 0..23, onValueChange = onHourChange, label = "Giờ")
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        TimeSpinner(value = minute, range = 0..59, step = 5, onValueChange = onMinuteChange, label = "Phút")
    }
}

@Composable
private fun TimeSpinner(value: Int, range: IntRange, step: Int = 1, onValueChange: (Int) -> Unit, label: String) {
    val colors = KidFocusTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onBackground.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.12f))
                    .clickable {
                        val newVal = value - step
                        onValueChange(if (newVal < range.first) range.last - (range.last % step) else newVal)
                    },
                contentAlignment = Alignment.Center,
            ) { Text("−", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp) }

            Text(
                text = value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.onBackground,
                fontWeight = FontWeight.Bold,
            )

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.12f))
                    .clickable {
                        val newVal = value + step
                        onValueChange(if (newVal > range.last) range.first else newVal)
                    },
                contentAlignment = Alignment.Center,
            ) { Text("+", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun DaySelector(selectedDays: Set<Int>, onDaysChange: (Set<Int>) -> Unit) {
    val colors = KidFocusTheme.colors
    val days = listOf(
        Calendar.MONDAY to "T2",
        Calendar.TUESDAY to "T3",
        Calendar.WEDNESDAY to "T4",
        Calendar.THURSDAY to "T5",
        Calendar.FRIDAY to "T6",
        Calendar.SATURDAY to "T7",
        Calendar.SUNDAY to "CN",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { (day, label) ->
            val selected = day in selectedDays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.primary else colors.surface)
                    .border(1.dp, if (selected) colors.primary else colors.onBackground.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .clickable {
                        onDaysChange(if (selected) selectedDays - day else selectedDays + day)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.onPrimary else colors.onBackground.copy(alpha = 0.7f),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}
