package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.daylog.*
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.ui.viewmodel.DayLogViewModel
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToInt

fun logTime(minute: Int): String = "%02d:%02d".format(java.util.Locale.ROOT, Math.floorMod(minute, 1440) / 60, Math.floorMod(minute, 1440) % 60)

@Composable
internal fun planName(plan: DayPlanItem): String = when (plan.category) {
    DayLogCategory.SLEEP -> stringResource(R.string.daylog_planned_sleep)
    DayLogCategory.WAKE -> stringResource(R.string.daylog_planned_wake)
    else -> plan.name
}

@Composable
fun DayLogTimeline(viewModel: DayLogViewModel, onEdit: () -> Unit, onStart: (ScheduledTask) -> Unit, header: @Composable () -> Unit = {}) {
    val data by viewModel.data.collectAsState()
    val date by viewModel.date.collectAsState()
    val plans = planForDate(date, data.tasks, data.anchors)
    val actual = data.entries.filter { !it.deleted && it.date == date }
    val moments = (plans.map { it.startMinute } + actual.map { it.startMinute }).distinct().sorted()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            header()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.daylog_plan), Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.daylog_actual), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            }
        }
        if (moments.isEmpty()) item { Text(stringResource(R.string.daylog_unrecorded), Modifier.padding(16.dp)) }
        items(moments, key = { it }) { minute ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    plans.filter { it.startMinute == minute }.forEach { plan ->
                        val displayName = planName(plan)
                        OutlinedCard(Modifier.fillMaxWidth().clickable { viewModel.openPlan(plan.copy(name = displayName)); onEdit() }) {
                            Column(Modifier.padding(10.dp)) {
                                Text(logTime(plan.startMinute), style = MaterialTheme.typography.labelMedium)
                                Text(planName(plan), fontWeight = FontWeight.SemiBold)
                                plan.durationMinutes?.let { duration ->
                                    if (duration > 0) Text(stringResource(R.string.daylog_duration, duration), style = MaterialTheme.typography.bodySmall)
                                    if (plan.startMinute + duration >= 1440) Text(stringResource(R.string.daylog_next_day_end, logTime(plan.startMinute + duration)), style = MaterialTheme.typography.bodySmall)
                                }
                                Text(stringResource(R.string.daylog_record_plan), style = MaterialTheme.typography.labelSmall)
                                data.tasks.firstOrNull { it.id == plan.taskId }?.let { task ->
                                    TextButton(onClick = { onStart(task) }) { Text(stringResource(R.string.daily_start)) }
                                }
                            }
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    actual.filter { it.startMinute == minute }.forEach { entry ->
                        OutlinedCard(Modifier.fillMaxWidth().clickable { viewModel.openActual(entry); onEdit() }) {
                            Column(Modifier.padding(10.dp)) {
                                Text(logTime(entry.startMinute), style = MaterialTheme.typography.labelMedium)
                                Text(when(entry.category) { DayLogCategory.SLEEP -> stringResource(R.string.daylog_sleep); DayLogCategory.WAKE -> stringResource(R.string.daylog_wake); else -> entry.name }, fontWeight = FontWeight.SemiBold)
                                Text(entry.endMinute?.let { end ->
                                    if (end < entry.startMinute) stringResource(R.string.daylog_next_day_end, logTime(end))
                                    else stringResource(R.string.daylog_end_at, logTime(end))
                                } ?: stringResource(R.string.daylog_open), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(when(entry.source) { DayLogSource.TIMER -> R.string.daylog_source_timer; DayLogSource.ROUTINE -> R.string.daylog_source_routine; DayLogSource.MANUAL -> R.string.daylog_source_manual; DayLogSource.AI -> R.string.daylog_source_ai }), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun DayLogEditor(viewModel: DayLogViewModel) {
    val entry by viewModel.draft.collectAsState()
    val error by viewModel.error.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val draft = entry ?: return
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var start by remember(draft.id) { mutableStateOf(logTime(draft.startMinute)) }
    var end by remember(draft.id) { mutableStateOf(draft.endMinute?.let(::logTime).orEmpty()) }
    var category by remember(draft.id) { mutableStateOf(draft.category) }
    AlertDialog(onDismissRequest = { if (!saving) viewModel.cancelEdit() },
        title = { Text(stringResource(R.string.daylog_edit_title)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(draft.date.toString())
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.daylog_name)) }, singleLine = true)
            OutlinedTextField(start, { start = it }, label = { Text(stringResource(R.string.daylog_start)) }, singleLine = true)
            OutlinedTextField(end, { end = it }, label = { Text(stringResource(R.string.daylog_end)) }, singleLine = true)
            Text(stringResource(R.string.daylog_midnight_hint), style = MaterialTheme.typography.bodySmall)
            if (draft.taskId == null) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { expanded = true }) { Text(categoryLabel(category)) }
                    DropdownMenu(expanded, { expanded = false }) {
                        DayLogCategory.entries.forEach { option -> DropdownMenuItem(text = { Text(categoryLabel(option)) }, onClick = { category = option; expanded = false }) }
                    }
                }
            }
            if (error != null) Text(stringResource(errorResource(error!!)), color = MaterialTheme.colorScheme.error)
            if (viewModel.isExisting()) TextButton(onClick = viewModel::deleteDraft, enabled = !saving) { Text(stringResource(R.string.daylog_delete)) }
        } },
        confirmButton = { TextButton(onClick = { viewModel.save(name, start, end, category) }, enabled = !saving) { Text(stringResource(R.string.daylog_save)) } },
        dismissButton = { TextButton(onClick = viewModel::cancelEdit, enabled = !saving) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun categoryLabel(category: DayLogCategory): String = stringResource(when (category) {
    DayLogCategory.STUDY -> R.string.daylog_category_study
    DayLogCategory.HYGIENE -> R.string.daylog_category_hygiene
    DayLogCategory.CHORES -> R.string.daylog_category_chores
    DayLogCategory.ENTERTAINMENT -> R.string.daylog_category_play
    DayLogCategory.SCHOOL -> R.string.smart_school_title
    DayLogCategory.SLEEP -> R.string.daylog_sleep
    DayLogCategory.WAKE -> R.string.daylog_wake
    DayLogCategory.ROUTINE -> R.string.daylog_source_routine
    DayLogCategory.OTHER -> R.string.daylog_category_other
})
private fun errorResource(error: com.kidfocus.timer.ui.viewmodel.DayLogError): Int = when(error) {
    com.kidfocus.timer.ui.viewmodel.DayLogError.INVALID -> R.string.daylog_invalid
    com.kidfocus.timer.ui.viewmodel.DayLogError.NO_PROFILE -> R.string.daylog_no_profile
    com.kidfocus.timer.ui.viewmodel.DayLogError.PROFILE_CHANGED -> R.string.daylog_profile_changed
    com.kidfocus.timer.ui.viewmodel.DayLogError.STORAGE -> R.string.daylog_storage_error
}

@Composable
fun WeeklyComparisonScreen(viewModel: DayLogViewModel, onBack: () -> Unit) {
    val data by viewModel.data.collectAsState()
    val week by viewModel.week.collectAsState()
    val now = LocalTime.now()
    val current = compareWeek(week, data.tasks, data.anchors, data.entries, nowMinute = now.hour * 60 + now.minute)
    val previous = compareWeek(week.minusWeeks(1), data.tasks, data.anchors, data.entries, nowMinute = now.hour * 60 + now.minute)
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.daylog_compare_title), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { viewModel.moveWeek(-1) }) { Text(stringResource(R.string.daylog_previous_week)) }
            TextButton(onClick = viewModel::thisWeek) { Text(stringResource(R.string.daylog_this_week)) }
            TextButton(onClick = { viewModel.moveWeek(1) }) { Text(stringResource(R.string.daylog_next_week)) }
        }
        Text("$week – ${week.plusDays(6)}", Modifier.padding(horizontal = 16.dp))
        Text(stringResource(R.string.daylog_common_plan), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { WeekSummaryCard(current.summary, previous.summary) }
            items(current.days, key = { it.date.toString() }) { day ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(day.date.toString(), fontWeight = FontWeight.Bold)
                    if (!day.hasData) Text(stringResource(R.string.daylog_unrecorded))
                    day.rows.forEach { row ->
                        Text("${planName(row.plan)} · ${logTime(row.plan.startMinute)}")
                        Text(stringResource(when(row.status) {
                            DayComparisonStatus.ON_TIME -> R.string.daylog_on_time
                            DayComparisonStatus.LATE -> R.string.daylog_late
                            DayComparisonStatus.EARLY -> R.string.daylog_early
                            DayComparisonStatus.MISSED -> R.string.daylog_missed
                            DayComparisonStatus.NO_DATA -> R.string.daylog_no_data
                        }, kotlin.math.abs(row.startDelta ?: 0)), style = MaterialTheme.typography.bodySmall)
                        row.durationDelta?.let { difference ->
                            Text(stringResource(when { difference > 0 -> R.string.daylog_longer; difference < 0 -> R.string.daylog_shorter; else -> R.string.daylog_same_duration }, kotlin.math.abs(difference)), style = MaterialTheme.typography.bodySmall)
                        }
                        if (row.actual != null && row.actualDuration == null) Text(stringResource(R.string.daylog_open), style = MaterialTheme.typography.bodySmall)
                    }
                    day.incidental.forEach { entry -> Text(stringResource(R.string.daylog_incidental_item, entry.name, logTime(entry.startMinute))) }
                } }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun WeekSummaryCard(summary: WeekSummary, previous: WeekSummary) {
    fun trend(value: Double?, before: Double?): String = if (value == null || before == null) "" else when { value > before -> " ↑"; value < before -> " ↓"; else -> " =" }
    val noData = stringResource(R.string.daylog_no_data)
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.daylog_summary), fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.daylog_recorded_days, summary.recordedDays))
        Text(stringResource(R.string.daylog_completion, summary.completedPercent?.let { "$it%" } ?: noData) + trend(summary.completedPercent?.toDouble(), previous.completedPercent?.toDouble()))
        Text(stringResource(R.string.daylog_delay_mean, summary.averageStartDelay?.roundToInt()?.toString() ?: noData) + trend(summary.averageStartDelay, previous.averageStartDelay))
        Text(stringResource(R.string.daylog_bed_mean, summary.averageActualBed?.roundToInt()?.let(::logTime) ?: noData, summary.averagePlannedBed?.roundToInt()?.let(::logTime) ?: noData) + trend(summary.averageActualBed, previous.averageActualBed))
        Text(stringResource(R.string.daylog_sleep_deficit, summary.sleepDeficit?.toString() ?: noData) + trend(summary.sleepDeficit?.toDouble(), previous.sleepDeficit?.toDouble()))
        Text(stringResource(R.string.daylog_previous_comparison), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.daylog_top_drifts), fontWeight = FontWeight.SemiBold)
        summary.biggestDrifts.forEach { row -> Text(stringResource(R.string.daylog_drift_item, planName(row.plan), row.plan.date.toString(), kotlin.math.abs(row.startDelta ?: 0), kotlin.math.abs(row.durationDelta ?: 0))) }
    } }
}
