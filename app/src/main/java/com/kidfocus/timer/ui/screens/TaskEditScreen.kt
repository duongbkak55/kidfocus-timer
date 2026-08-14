package com.kidfocus.timer.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.ui.components.RepeatPreset
import com.kidfocus.timer.ui.components.RepeatPresetSelector
import com.kidfocus.timer.ui.components.taskTypeLabel
import com.kidfocus.timer.ui.components.SettingSliderRow
import com.kidfocus.timer.ui.components.TaskTemplatePicker
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel
import java.util.Calendar
import coil.compose.AsyncImage

@Composable
fun TaskEditScreen(
    task: ScheduledTask,
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val context = LocalContext.current
    val isNew = task.id == 0L
    val localizedDefaultName = taskTypeLabel(task.taskType)
    val localizedCustomName = taskTypeLabel(TaskType.CUSTOM)
    val savedTasks by viewModel.tasks.collectAsState()

    var selectedType by remember(task.id, task.taskType) { mutableStateOf(task.taskType) }
    var selectedDefaultName by remember(task.id, task.taskType) { mutableStateOf(localizedDefaultName) }
    var emoji by remember(task.id, task.emoji) { mutableStateOf(task.emoji) }
    var name by remember(task.id, task.taskType) {
        mutableStateOf(
            if (isNew && task.name == task.taskType.displayName) localizedDefaultName else task.name
        )
    }
    var hour by remember { mutableIntStateOf(task.hour) }
    var minute by remember { mutableIntStateOf(task.minute) }
    var daysOfWeek by remember { mutableStateOf(task.daysOfWeek) }
    var focusMinutes by remember { mutableIntStateOf(task.focusDurationMinutes) }
    var breakMinutes by remember { mutableIntStateOf(task.breakDurationMinutes) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var photoUri by remember(task.id, task.photoUri) { mutableStateOf(task.photoUri) }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            photoUri = uri.toString()
        }
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colors.onBackground,
                    )
                }
                Text(
                    text = stringResource(if (isNew) R.string.schedule_create_title else R.string.schedule_edit_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = { showTemplatePicker = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.task_choose_suggestion), fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Emoji + Name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = emoji, fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.schedule_activity_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            SectionLabel(stringResource(R.string.task_photo_title))
            Spacer(modifier = Modifier.height(8.dp))
            photoUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.task_photo_description, name),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(stringResource(if (photoUri == null) R.string.task_photo_choose else R.string.task_photo_change))
                }
                if (photoUri != null) {
                    OutlinedButton(
                        onClick = { photoUri = null },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.task_photo_remove))
                    }
                }
            }
            Text(
                text = stringResource(R.string.task_photo_local_only),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Time picker
            SectionLabel(stringResource(R.string.schedule_start_time))
            Spacer(modifier = Modifier.height(8.dp))
            TimePickerRow(
                hour = hour,
                minute = minute,
                onHourChange = { hour = it },
                onMinuteChange = { minute = it },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Day selector
            SectionLabel(stringResource(R.string.schedule_days_of_week))
            Spacer(modifier = Modifier.height(8.dp))
            RepeatPresetSelector(
                selected = when (daysOfWeek) {
                    TaskType.ALL_DAYS -> RepeatPreset.EVERY_DAY
                    TaskType.WEEKDAYS -> RepeatPreset.WEEKDAYS
                    TaskType.WEEKEND -> RepeatPreset.WEEKEND
                    else -> null
                },
                onSelect = { preset ->
                    daysOfWeek = when (preset) {
                        RepeatPreset.EVERY_DAY -> TaskType.ALL_DAYS
                        RepeatPreset.WEEKDAYS -> TaskType.WEEKDAYS
                        RepeatPreset.WEEKEND -> TaskType.WEEKEND
                    }
                },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.repeat_custom_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(6.dp))
            DaySelector(
                selectedDays = daysOfWeek,
                onDaysChange = { daysOfWeek = it },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Duration sliders
            SectionLabel(stringResource(R.string.schedule_duration))
            Spacer(modifier = Modifier.height(8.dp))
            SettingSliderRow(
                title = stringResource(R.string.schedule_focus),
                value = focusMinutes.toFloat(),
                valueRange = 10f..120f,
                onValueChange = { focusMinutes = it.toInt() },
                valueLabel = stringResource(R.string.parent_minutes_format, focusMinutes),
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingSliderRow(
                title = stringResource(R.string.schedule_break),
                value = breakMinutes.toFloat(),
                valueRange = 5f..30f,
                onValueChange = { breakMinutes = it.toInt() },
                valueLabel = stringResource(R.string.parent_minutes_format, breakMinutes),
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Save button
            Button(
                onClick = {
                    viewModel.saveTask(
                        task.copy(
                            taskType = selectedType,
                            name = name.ifBlank { selectedDefaultName },
                            emoji = emoji,
                            hour = hour,
                            minute = minute,
                            daysOfWeek = daysOfWeek.ifEmpty { TaskType.WEEKDAYS },
                            focusDurationMinutes = focusMinutes,
                            breakDurationMinutes = breakMinutes,
                            enabled = true,
                            isCustom = selectedType == TaskType.CUSTOM,
                            photoUri = photoUri,
                        )
                    )
                    onBack()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Text(stringResource(R.string.schedule_save), fontWeight = FontWeight.Bold, color = colors.onPrimary)
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
                    Text(stringResource(R.string.schedule_delete), color = Color(0xFFEF4444), fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showTemplatePicker) {
        TaskTemplatePicker(
            recentTypes = savedTasks.asReversed().map { it.taskType },
            onDismiss = { showTemplatePicker = false },
            onManual = {
                selectedType = TaskType.CUSTOM
                selectedDefaultName = localizedCustomName
                emoji = TaskType.CUSTOM.emoji
                showTemplatePicker = false
            },
            onSelect = { type, localizedName ->
                selectedType = type
                selectedDefaultName = localizedName
                emoji = type.emoji
                name = localizedName
                hour = type.defaultHour
                minute = type.defaultMinute
                daysOfWeek = type.defaultDays
                focusMinutes = type.defaultFocusMinutes
                breakMinutes = type.defaultBreakMinutes
                showTemplatePicker = false
            },
        )
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
        TimeSpinner(
            value = hour,
            range = 0..23,
            onValueChange = onHourChange,
            label = stringResource(R.string.schedule_hour),
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        TimeSpinner(
            value = minute,
            range = 0..59,
            step = 5,
            onValueChange = onMinuteChange,
            label = stringResource(R.string.schedule_minute),
        )
    }
}

@Composable
private fun TimeSpinner(value: Int, range: IntRange, step: Int = 1, onValueChange: (Int) -> Unit, label: String) {
    val colors = KidFocusTheme.colors
    val decreaseDescription = stringResource(R.string.schedule_decrease, label)
    val increaseDescription = stringResource(R.string.schedule_increase, label)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = colors.onBackground.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .semantics {
                        contentDescription = decreaseDescription
                        role = Role.Button
                    }
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
                    .semantics {
                        contentDescription = increaseDescription
                        role = Role.Button
                    }
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
        Triple(Calendar.MONDAY, R.string.weekday_monday_short, R.string.weekday_monday),
        Triple(Calendar.TUESDAY, R.string.weekday_tuesday_short, R.string.weekday_tuesday),
        Triple(Calendar.WEDNESDAY, R.string.weekday_wednesday_short, R.string.weekday_wednesday),
        Triple(Calendar.THURSDAY, R.string.weekday_thursday_short, R.string.weekday_thursday),
        Triple(Calendar.FRIDAY, R.string.weekday_friday_short, R.string.weekday_friday),
        Triple(Calendar.SATURDAY, R.string.weekday_saturday_short, R.string.weekday_saturday),
        Triple(Calendar.SUNDAY, R.string.weekday_sunday_short, R.string.weekday_sunday),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        days.forEach { (day, labelRes, fullLabelRes) ->
            val selected = day in selectedDays
            val label = stringResource(labelRes)
            val fullLabel = stringResource(fullLabelRes)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) colors.primary else colors.surface)
                    .border(1.dp, if (selected) colors.primary else colors.onBackground.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                    .semantics {
                        contentDescription = fullLabel
                        this.selected = selected
                        role = Role.Checkbox
                    }
                    .clickable(role = Role.Checkbox) {
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
