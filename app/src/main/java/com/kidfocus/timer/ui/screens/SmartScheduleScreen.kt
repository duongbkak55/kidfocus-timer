package com.kidfocus.timer.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.schedule.*
import com.kidfocus.timer.ui.components.scheduleDaysLabel
import com.kidfocus.timer.ui.viewmodel.ScheduleEvent
import com.kidfocus.timer.ui.viewmodel.SmartScheduleViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SmartScheduleScreen(onBack: () -> Unit, onQuickEntry: () -> Unit = {}, access: com.kidfocus.timer.ui.viewmodel.ScheduleAccessViewModel = hiltViewModel(), viewModel: SmartScheduleViewModel = hiltViewModel()) {
    val config by access.config.collectAsState()
    val account by access.account.collectAsState()
    LaunchedEffect(Unit) { access.refresh() }
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.smart_saved)
    val restored = stringResource(R.string.smart_restored)
    val error = stringResource(R.string.smart_error)
    val undo = stringResource(R.string.smart_undo)
    LaunchedEffect(viewModel, saved, restored, error, undo) {
        viewModel.events.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            if (event == ScheduleEvent.SAVED) {
                scope.launch {
                    val timer = launch { delay(10_000); snackbar.currentSnackbarData?.dismiss() }
                    val result = snackbar.showSnackbar(saved, undo, duration = SnackbarDuration.Indefinite)
                    timer.cancel()
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                }
            } else scope.launch { snackbar.showSnackbar(if (event == ScheduleEvent.RESTORED) restored else error) }
        }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.smart_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
        }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = state
        if (current == null) { CircularProgressIndicator(Modifier.padding(padding)); return@Scaffold }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(current.profile.name, style = MaterialTheme.typography.titleLarge)
            if (config.scheduleEnabled) {
                Button(onClick = onQuickEntry, enabled = !busy) { Text(stringResource(R.string.quick_title)) }
                if (account.isSignedIn) ScheduleUsage(config.usage, config.scheduleParseCost)
            }
            AnchorsEditor(current.profile.id, current.schedule.anchors, busy, viewModel::saveAnchors)
            if (current.canUndo) OutlinedButton(onClick = viewModel::undo, enabled = !busy) { Text(stringResource(R.string.smart_restore_previous)) }
            Text(stringResource(R.string.smart_advice_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.smart_aasm), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.smart_aasm_source), style = MaterialTheme.typography.bodySmall)
            if (current.findings.isEmpty()) Text(stringResource(R.string.smart_no_findings))
            current.findings.forEach { finding ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(when (finding.severity) {
                            Severity.HIGH -> R.string.smart_high
                            Severity.MEDIUM -> R.string.smart_medium
                            Severity.LOW -> R.string.smart_low
                        }), style = MaterialTheme.typography.labelLarge)
                        Text(scheduleDaysLabel(DayCodec.toCalendar(finding.days)))
                        Text(findingMessage(finding))
                        current.schedule.tasks.filter { it.id in finding.taskIds }.forEach { Text(it.name) }
                        finding.suggestion?.let { suggestion ->
                            Text(when (suggestion) {
                                is ScheduleChange.AddTask, is ScheduleChange.SetAnchors -> stringResource(R.string.quick_preview)
                                is ScheduleChange.SetBed -> stringResource(R.string.smart_suggest_bed, suggestion.times.values.first().toString())
                                is ScheduleChange.MoveTask -> stringResource(R.string.smart_suggest_move, suggestion.start.toString())
                                is ScheduleChange.ResizeTask -> stringResource(R.string.smart_suggest_resize, suggestion.durationMinutes)
                            })
                            Button(onClick = { viewModel.apply(finding) }, enabled = !busy) { Text(stringResource(R.string.smart_apply)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun findingMessage(finding: Finding): String = when (finding.ruleId) {
    RuleId.SLEEP_SHORT -> stringResource(R.string.smart_sleep_short, finding.params.getValue("actual"), finding.params.getValue("minimum"))
    RuleId.SOCIAL_JETLAG -> stringResource(R.string.smart_jetlag, finding.params.getValue("difference"))
    RuleId.SCREEN_BEFORE_BED -> stringResource(R.string.smart_screen_before_bed)
    RuleId.OVERLAP -> stringResource(R.string.smart_overlap)
    RuleId.LATE_HOMEWORK -> stringResource(R.string.smart_late_homework)
    RuleId.FOCUS_TOO_LONG -> stringResource(R.string.smart_focus_long, finding.params.getValue("maximum"))
    RuleId.NO_FREE_TIME -> stringResource(R.string.smart_no_free_time)
    RuleId.MORNING_LATE_PATTERN -> stringResource(R.string.smart_morning_late, finding.params.getValue("count"))
}

@Composable
private fun AnchorsEditor(profileId: String, anchors: ScheduleAnchors, busy: Boolean, onSave: (ScheduleAnchors) -> Unit) {
    var draft by remember(profileId, anchors) { mutableStateOf(anchors) }
    val weekdays = DayOfWeek.entries.take(5).toSet()
    val weekend = DayOfWeek.entries.drop(5).toSet()
    Text(stringResource(R.string.smart_hours_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.smart_bed_help), style = MaterialTheme.typography.bodySmall)
    listOf(weekdays, weekend).forEach { days ->
        Text(scheduleDaysLabel(DayCodec.toCalendar(days)), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeButton(R.string.smart_wake, draft.wake[days.first()], !busy, Modifier.weight(1f)) { time -> draft = draft.copy(wake = draft.wake + days.associateWith { time }) }
            TimeButton(R.string.smart_bed, draft.bed[days.first()], !busy, Modifier.weight(1f)) { time -> draft = draft.copy(bed = draft.bed + days.associateWith { time }) }
        }
    }
    var individual by remember { mutableStateOf(false) }
    TextButton(onClick = { individual = !individual }) { Text(stringResource(R.string.smart_edit_each_day)) }
    if (individual) DayOfWeek.entries.forEach { day ->
        Text(scheduleDaysLabel(setOf(DayCodec.toCalendar(day))))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeButton(R.string.smart_wake, draft.wake[day], !busy, Modifier.weight(1f)) { draft = draft.copy(wake = draft.wake + (day to it)) }
            TimeButton(R.string.smart_bed, draft.bed[day], !busy, Modifier.weight(1f)) { draft = draft.copy(bed = draft.bed + (day to it)) }
        }
    }
    Text(stringResource(R.string.smart_school_title), style = MaterialTheme.typography.titleMedium)
    draft.school.forEachIndexed { index, block ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                OutlinedTextField(value = block.label, onValueChange = { label ->
                    draft = draft.copy(school = draft.school.mapIndexed { i, b -> if (i == index) b.copy(label = label.take(80)) else b })
                }, label = { Text(stringResource(R.string.smart_school_label)) }, enabled = !busy, singleLine = true)
                Text(scheduleDaysLabel(DayCodec.toCalendar(block.days)))
                Row {
                    TimeButton(R.string.smart_start, block.start, !busy, Modifier.weight(1f)) { time ->
                        draft = draft.copy(school = draft.school.mapIndexed { i, b -> if (i == index) b.copy(start = time) else b })
                    }
                    TimeButton(R.string.smart_end, block.end, !busy, Modifier.weight(1f)) { time ->
                        draft = draft.copy(school = draft.school.mapIndexed { i, b -> if (i == index) b.copy(end = time) else b })
                    }
                }
                DaySelector(block.days, !busy) { days -> draft = draft.copy(school = draft.school.mapIndexed { i, b -> if (i == index) b.copy(days = days) else b }) }
                TextButton(onClick = { draft = draft.copy(school = draft.school.filterIndexed { i, _ -> i != index }) }, enabled = !busy) { Text(stringResource(R.string.smart_remove_school)) }
            }
        }
    }
    val schoolLabel = stringResource(R.string.smart_school_default)
    OutlinedButton(onClick = { draft = draft.copy(school = draft.school + Block(weekdays, LocalTime.of(7, 15), LocalTime.of(16, 30), schoolLabel)) }, enabled = !busy) {
        Text(stringResource(R.string.smart_add_school))
    }
    var commuteText by remember(profileId, anchors) { mutableStateOf(anchors.commuteMinutes.toString()) }
    val commute = commuteText.toIntOrNull()
    val validCommute = commute != null && commute in 0..180
    OutlinedTextField(value = commuteText, onValueChange = { text ->
        if (text.length <= 3 && text.all(Char::isDigit)) commuteText = text
    }, label = { Text(stringResource(R.string.smart_commute)) }, enabled = !busy, singleLine = true,
        isError = !validCommute, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
    val candidate = draft.copy(commuteMinutes = commute ?: 0)
    Button(onClick = { onSave(candidate) }, enabled = !busy && candidate != anchors && validCommute && runCatching { candidate.validate() }.isSuccess) { Text(stringResource(R.string.smart_save_hours)) }
}

@Composable
private fun DaySelector(days: Set<DayOfWeek>, enabled: Boolean, onChange: (Set<DayOfWeek>) -> Unit) {
    // Two rows avoid clipping on narrow phones and with large text.
    DayOfWeek.entries.chunked(4).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { day ->
                FilterChip(selected = day in days, onClick = { onChange(if (day in days) days - day else days + day) },
                    enabled = enabled, label = { Text(scheduleDaysLabel(setOf(DayCodec.toCalendar(day)))) })
            }
        }
    }
}

@Composable
private fun TimeButton(label: Int, value: LocalTime?, enabled: Boolean, modifier: Modifier = Modifier, onChange: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val initial = value ?: LocalTime.of(6, 30)
        TimePickerDialog(context, { _, hour, minute -> onChange(LocalTime.of(hour, minute)) }, initial.hour, initial.minute, true).show()
    }, enabled = enabled, modifier = modifier) {
        Text(stringResource(R.string.smart_time_field, stringResource(label), value?.toString() ?: stringResource(R.string.smart_not_set)))
    }
}
