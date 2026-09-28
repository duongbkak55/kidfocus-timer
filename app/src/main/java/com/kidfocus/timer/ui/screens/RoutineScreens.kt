package com.kidfocus.timer.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.R
import com.kidfocus.timer.alarm.ExactAlarmPermission
import com.kidfocus.timer.data.database.RoutineEntity
import com.kidfocus.timer.domain.model.RoutineTime
import com.kidfocus.timer.ui.components.RepeatPreset
import com.kidfocus.timer.ui.components.RepeatPresetSelector
import com.kidfocus.timer.ui.components.TaskTemplatePicker
import com.kidfocus.timer.ui.components.TaskVisual
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.RoutineViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.DayOfWeek
import java.util.Locale
import kotlin.math.roundToInt
import coil.compose.AsyncImage

@Composable
fun RoutineSettingsScreen(
    viewModel: RoutineViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val colors = KidFocusTheme.colors
    val context = LocalContext.current
    val exactPermission = remember(context) { ExactAlarmPermission(context) }
    val routines by viewModel.allRoutines.collectAsState()
    var pendingDelete by remember { mutableStateOf<RoutineEntity?>(null) }
    var exactAlarmAllowed by remember { mutableStateOf(exactPermission.isAllowed()) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var presetResultCount by remember { mutableStateOf<Int?>(null) }

    LifecycleResumeEffect(Unit) {
        exactAlarmAllowed = exactPermission.isAllowed()
        if (exactAlarmAllowed) viewModel.rescheduleAll()
        onPauseOrDispose { }
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
            ScreenHeader(stringResource(R.string.routine_settings_title), onBack)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.routine_settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onBackground.copy(alpha = 0.65f),
            )
            Spacer(Modifier.height(20.dp))

            if (!exactAlarmAllowed) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.warningArc.copy(alpha = 0.14f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.routine_exact_alarm_title),
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Text(
                            stringResource(R.string.routine_exact_alarm_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurface.copy(alpha = 0.7f),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = {
                            exactPermission.openSettings()
                        }) { Text(stringResource(R.string.routine_open_settings)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.routine_add_new))
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showPresetPicker = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.routine_add_preset_pack))
            }
            presetResultCount?.let { addedCount ->
                Text(
                    if (addedCount > 0) {
                        stringResource(R.string.routine_preset_added, addedCount)
                    } else {
                        stringResource(R.string.routine_preset_already_exists)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.primary,
                )
            }

            Spacer(Modifier.height(16.dp))
            if (routines.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.routine_empty),
                        modifier = Modifier.padding(20.dp),
                        color = colors.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            routines.forEach { routine ->
                RoutineManageRow(
                    routine = routine,
                    onEdit = { onEdit(routine.id) },
                    onToggle = { viewModel.setEnabled(routine, it) },
                    onDelete = { pendingDelete = routine },
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    pendingDelete?.let { routine ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.routine_delete_title)) },
            text = { Text(stringResource(R.string.routine_delete_message, routine.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(routine)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }


    if (showPresetPicker) {
        RoutinePresetPackDialog(
            onDismiss = { showPresetPicker = false },
            onSelect = { presetRoutines ->
                viewModel.addPreset(presetRoutines) { count ->
                    presetResultCount = count
                    showPresetPicker = false
                }
            },
        )
    }
}

private data class RoutinePresetPack(
    val title: String,
    val description: String,
    val routines: List<RoutineEntity>,
)

@Composable
private fun RoutinePresetPackDialog(
    onDismiss: () -> Unit,
    onSelect: (List<RoutineEntity>) -> Unit,
) {
    val packs = listOf(
        RoutinePresetPack(
            title = stringResource(R.string.routine_pack_morning),
            description = stringResource(R.string.routine_pack_morning_description),
            routines = listOf(
                presetRoutine(stringResource(R.string.routine_pack_wake_up), "🌞", 7 * 60, RoutineTime.WEEKDAYS_MASK),
                presetRoutine(stringResource(R.string.routine_pack_brush_teeth), "🪥", 7 * 60 + 10, RoutineTime.WEEKDAYS_MASK),
                presetRoutine(stringResource(R.string.routine_pack_breakfast), "🥣", 7 * 60 + 30, RoutineTime.WEEKDAYS_MASK),
                presetRoutine(stringResource(R.string.routine_pack_school_bag), "🎒", 7 * 60 + 45, RoutineTime.WEEKDAYS_MASK),
            ),
        ),
        RoutinePresetPack(
            title = stringResource(R.string.routine_pack_bedtime),
            description = stringResource(R.string.routine_pack_bedtime_description),
            routines = listOf(
                presetRoutine(stringResource(R.string.routine_pack_shower), "🛁", 20 * 60 + 30),
                presetRoutine(stringResource(R.string.routine_pack_brush_teeth), "🪥", 21 * 60),
                presetRoutine(stringResource(R.string.routine_pack_read_book), "📖", 21 * 60 + 30),
                presetRoutine(stringResource(R.string.routine_pack_sleep), "🌙", 22 * 60),
            ),
        ),
        RoutinePresetPack(
            title = stringResource(R.string.routine_pack_homework),
            description = stringResource(R.string.routine_pack_homework_description),
            routines = listOf(
                presetRoutine(stringResource(R.string.routine_pack_prepare_desk), "✏️", 19 * 60, RoutineTime.WEEKDAYS_MASK),
                presetRoutine(stringResource(R.string.routine_pack_do_homework), "📚", 19 * 60 + 45, RoutineTime.WEEKDAYS_MASK, 25),
                presetRoutine(stringResource(R.string.routine_pack_school_bag), "🎒", 20 * 60 + 15, RoutineTime.WEEKDAYS_MASK),
            ),
        ),
        RoutinePresetPack(
            title = stringResource(R.string.routine_pack_meal),
            description = stringResource(R.string.routine_pack_meal_description),
            routines = listOf(
                presetRoutine(stringResource(R.string.routine_pack_wash_hands), "🧼", 18 * 60 + 45),
                presetRoutine(stringResource(R.string.routine_pack_dinner), "🍲", 20 * 60),
            ),
        ),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_choose_preset_pack)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                packs.forEach { pack ->
                    OutlinedButton(
                        onClick = { onSelect(pack.routines) },
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(pack.title, fontWeight = FontWeight.Bold)
                            Text(pack.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun presetRoutine(
    title: String,
    emoji: String,
    deadlineMinutes: Int,
    repeatDaysMask: Int = RoutineTime.EVERY_DAY_MASK,
    timerMinutes: Int? = null,
) = RoutineEntity(
    title = title,
    emoji = emoji,
    deadlineMinutes = deadlineMinutes,
    repeatDaysMask = repeatDaysMask,
    reminderMinutesBefore = 10,
    linkedTimerMinutes = timerMinutes,
)

@Composable
private fun RoutineManageRow(
    routine: RoutineEntity,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TaskVisual(
                    photoUri = routine.photoUri,
                    emoji = routine.emoji,
                    modifier = Modifier.width(48.dp).height(48.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        routine.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        stringResource(
                            R.string.routine_time_days_format,
                            formatTime(routine.deadlineMinutes),
                            formatRepeatDays(routine.repeatDaysMask),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.65f),
                    )
                    val reminderLabel = if (routine.reminderMinutesBefore == 0) {
                        stringResource(R.string.routine_remind_on_time)
                    } else {
                        stringResource(R.string.routine_remind_before, routine.reminderMinutesBefore)
                    }
                    val timerLabel = routine.linkedTimerMinutes?.let {
                        stringResource(R.string.routine_timer_detail, it)
                    }
                    Text(
                        listOfNotNull(reminderLabel, timerLabel).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.primary,
                    )
                }
                Switch(checked = routine.enabled, onCheckedChange = onToggle)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.routine_edit_description, routine.title),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.routine_delete_description, routine.title),
                    )
                }
            }
        }
    }
}

@Composable
fun RoutineEditorScreen(
    routineId: Long?,
    viewModel: RoutineViewModel,
    onBack: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val context = LocalContext.current
    val routines by viewModel.allRoutines.collectAsState()
    val existing = routines.firstOrNull { it.id == routineId }

    var title by rememberSaveable { mutableStateOf("") }
    var emoji by rememberSaveable { mutableStateOf("⭐") }
    var deadlineMinutes by rememberSaveable { mutableIntStateOf(20 * 60) }
    var repeatMask by rememberSaveable { mutableIntStateOf(RoutineTime.EVERY_DAY_MASK) }
    var reminderMinutes by rememberSaveable { mutableIntStateOf(15) }
    var enabled by rememberSaveable { mutableStateOf(true) }
    var timerEnabled by rememberSaveable { mutableStateOf(false) }
    var timerMinutes by rememberSaveable { mutableIntStateOf(25) }
    var showTimePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
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

    LaunchedEffect(existing?.id) {
        if (existing != null) {
            title = existing.title
            emoji = existing.emoji
            deadlineMinutes = existing.deadlineMinutes
            repeatMask = existing.repeatDaysMask
            reminderMinutes = existing.reminderMinutesBefore
            enabled = existing.enabled
            timerEnabled = existing.linkedTimerMinutes != null
            timerMinutes = existing.linkedTimerMinutes ?: 25
            photoUri = existing.photoUri
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
            ScreenHeader(
                stringResource(if (routineId == null) R.string.routine_add_title else R.string.routine_edit_title),
                onBack,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedButton(
                onClick = { showTemplatePicker = true },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.task_choose_suggestion), fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(4) },
                    label = { Text(stringResource(R.string.routine_emoji)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.3f),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.routine_task_name)) },
                    placeholder = { Text(stringResource(R.string.routine_task_example)) },
                    singleLine = true,
                    modifier = Modifier.weight(0.7f),
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.task_photo_title), fontWeight = FontWeight.Bold, color = colors.onBackground)
            Spacer(Modifier.height(8.dp))
            photoUri?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = stringResource(R.string.task_photo_description, title),
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
                    ) { Text(stringResource(R.string.task_photo_remove)) }
                }
            }
            Text(
                text = stringResource(R.string.task_photo_local_only),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 6.dp),
            )

            Spacer(Modifier.height(16.dp))

            Text(stringResource(R.string.routine_deadline), fontWeight = FontWeight.Bold, color = colors.onBackground)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showTimePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(formatTime(deadlineMinutes), style = MaterialTheme.typography.titleLarge)
            }

            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.routine_repeat), fontWeight = FontWeight.Bold, color = colors.onBackground)
            Spacer(Modifier.height(8.dp))
            RepeatPresetSelector(
                selected = when (repeatMask) {
                    RoutineTime.EVERY_DAY_MASK -> RepeatPreset.EVERY_DAY
                    RoutineTime.WEEKDAYS_MASK -> RepeatPreset.WEEKDAYS
                    RoutineTime.WEEKEND_MASK -> RepeatPreset.WEEKEND
                    else -> null
                },
                onSelect = { preset ->
                    repeatMask = when (preset) {
                        RepeatPreset.EVERY_DAY -> RoutineTime.EVERY_DAY_MASK
                        RepeatPreset.WEEKDAYS -> RoutineTime.WEEKDAYS_MASK
                        RepeatPreset.WEEKEND -> RoutineTime.WEEKEND_MASK
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.repeat_custom_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onBackground.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(6.dp))
            DayChipRows(repeatMask) { day ->
                val bit = RoutineTime.dayBit(day)
                repeatMask = repeatMask xor bit
            }

            Spacer(Modifier.height(20.dp))
            Text(
                if (reminderMinutes == 0) {
                    stringResource(R.string.routine_remind_on_time)
                } else {
                    stringResource(R.string.routine_remind_before, reminderMinutes)
                },
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Slider(
                value = reminderMinutes.toFloat(),
                onValueChange = { reminderMinutes = ((it / 5f).roundToInt() * 5).coerceIn(0, 60) },
                valueRange = 0f..60f,
                steps = 11,
            )

            Spacer(Modifier.height(12.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = colors.surface) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.routine_link_timer), color = colors.onSurface)
                            Text(
                                stringResource(R.string.routine_link_timer_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurface.copy(alpha = 0.6f),
                            )
                        }
                        Switch(checked = timerEnabled, onCheckedChange = { timerEnabled = it })
                    }
                    if (timerEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.parent_minutes_format, timerMinutes),
                            color = colors.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Slider(
                            value = timerMinutes.toFloat(),
                            onValueChange = { timerMinutes = it.roundToInt() },
                            valueRange = 5f..120f,
                            steps = 114,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.routine_enabled), modifier = Modifier.weight(1f), color = colors.onBackground)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            validationError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    validationError = when {
                        title.isBlank() -> context.getString(R.string.routine_error_name)
                        repeatMask == 0 -> context.getString(R.string.routine_error_days)
                        else -> null
                    }
                    if (validationError == null) {
                        val base = existing ?: RoutineEntity(
                            title = title.trim(),
                            deadlineMinutes = deadlineMinutes,
                            repeatDaysMask = repeatMask,
                        )
                        viewModel.save(
                            base.copy(
                                title = title.trim(),
                                emoji = emoji.ifBlank { "⭐" },
                                deadlineMinutes = deadlineMinutes,
                                repeatDaysMask = repeatMask,
                                reminderMinutesBefore = reminderMinutes,
                                enabled = enabled,
                                linkedTimerMinutes = timerMinutes.takeIf { timerEnabled },
                                photoUri = photoUri,
                            ),
                            onSaved = onBack,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) { Text(stringResource(R.string.routine_save)) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        RoutineTimePickerDialog(
            initialMinutes = deadlineMinutes,
            onDismiss = { showTimePicker = false },
            onConfirm = {
                deadlineMinutes = it
                showTimePicker = false
            },
        )
    }
    if (showTemplatePicker) {
        TaskTemplatePicker(
            onDismiss = { showTemplatePicker = false },
            onManual = { showTemplatePicker = false },
            onSelect = { type, localizedName ->
                title = localizedName
                emoji = type.emoji
                timerMinutes = type.defaultFocusMinutes
                repeatMask = when (type.defaultDays) {
                    com.kidfocus.timer.domain.model.TaskType.ALL_DAYS -> RoutineTime.EVERY_DAY_MASK
                    com.kidfocus.timer.domain.model.TaskType.WEEKDAYS -> RoutineTime.WEEKDAYS_MASK
                    com.kidfocus.timer.domain.model.TaskType.WEEKEND -> RoutineTime.WEEKEND_MASK
                    else -> repeatMask
                }
                showTemplatePicker = false
            },
        )
    }
}

@Composable
private fun DayChipRows(selectedMask: Int, onToggle: (DayOfWeek) -> Unit) {
    val days = listOf(
        Triple(DayOfWeek.MONDAY, R.string.weekday_monday_short, R.string.weekday_monday),
        Triple(DayOfWeek.TUESDAY, R.string.weekday_tuesday_short, R.string.weekday_tuesday),
        Triple(DayOfWeek.WEDNESDAY, R.string.weekday_wednesday_short, R.string.weekday_wednesday),
        Triple(DayOfWeek.THURSDAY, R.string.weekday_thursday_short, R.string.weekday_thursday),
        Triple(DayOfWeek.FRIDAY, R.string.weekday_friday_short, R.string.weekday_friday),
        Triple(DayOfWeek.SATURDAY, R.string.weekday_saturday_short, R.string.weekday_saturday),
        Triple(DayOfWeek.SUNDAY, R.string.weekday_sunday_short, R.string.weekday_sunday),
    )
    days.chunked(4).forEach { rowDays ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rowDays.forEach { (day, labelRes, fullLabelRes) ->
                val fullLabel = stringResource(fullLabelRes)
                FilterChip(
                    selected = selectedMask and RoutineTime.dayBit(day) != 0,
                    onClick = { onToggle(day) },
                    label = { Text(stringResource(labelRes)) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = fullLabel },
                )
            }
            repeat(4 - rowDays.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun RoutineTimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialMinutes / 60,
        initialMinute = initialMinutes % 60,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routine_choose_deadline)) },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) {
                Text(stringResource(R.string.choose))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ScreenHeader(title: String, onBack: () -> Unit) {
    val colors = KidFocusTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
        )
    }
}

internal fun formatTime(minutes: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minutes / 60, minutes % 60)

@Composable
internal fun formatRepeatDays(mask: Int): String {
    val shortLabels = mapOf(
        DayOfWeek.MONDAY to stringResource(R.string.weekday_monday_short),
        DayOfWeek.TUESDAY to stringResource(R.string.weekday_tuesday_short),
        DayOfWeek.WEDNESDAY to stringResource(R.string.weekday_wednesday_short),
        DayOfWeek.THURSDAY to stringResource(R.string.weekday_thursday_short),
        DayOfWeek.FRIDAY to stringResource(R.string.weekday_friday_short),
        DayOfWeek.SATURDAY to stringResource(R.string.weekday_saturday_short),
        DayOfWeek.SUNDAY to stringResource(R.string.weekday_sunday_short),
    )
    return when (mask) {
        RoutineTime.EVERY_DAY_MASK -> stringResource(R.string.repeat_every_day)
        RoutineTime.WEEKDAYS_MASK -> stringResource(R.string.repeat_weekdays)
        RoutineTime.WEEKEND_MASK -> stringResource(R.string.repeat_weekend)
        else -> DayOfWeek.values().asList()
            .filter { mask and RoutineTime.dayBit(it) != 0 }
            .joinToString(", ") { shortLabels.getValue(it) }
    }
}
