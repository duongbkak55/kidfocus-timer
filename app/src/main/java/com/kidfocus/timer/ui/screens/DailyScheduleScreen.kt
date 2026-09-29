package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.daylog.DayLogCategory
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.ui.viewmodel.*
import com.kidfocus.timer.ui.components.ScheduleExactAlarmReminder
import java.time.LocalDate

@Composable
fun DailyScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onStartTask: (ScheduledTask) -> Unit,
    quickEntryEnabled: Boolean = false,
    onQuickEntry: () -> Unit = {},
    onAddTaskAtTime: (Int, Int) -> Unit = { _, _ -> },
    alarmPermission: ScheduleAlarmPermissionViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    dayLogs: DayLogViewModel = androidx.hilt.navigation.compose.hiltViewModel(),
    editable: Boolean = false,
    onEditActual: () -> Unit = {},
    onCompare: () -> Unit = {},
    onEditPlan: () -> Unit = {},
) {
    val date by dayLogs.date.collectAsState()
    val data by dayLogs.data.collectAsState()
    val syncError by dayLogs.syncError.collectAsState()
    val sleepName = stringResource(R.string.daylog_sleep)
    val wakeName = stringResource(R.string.daylog_wake)
    fun edit() { if (!editable) onEditActual() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.daily_schedule_title), Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
        }
        DayLogTimeline(dayLogs, onEdit = { edit() }, onStart = onStartTask, header = {
            ScheduleExactAlarmReminder(alarmPermission)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { dayLogs.selectDate(date.minusDays(1)) }) { Text(stringResource(R.string.daily_previous_day)) }
                TextButton(onClick = { dayLogs.selectDate(LocalDate.now()) }) { Text(date.toString()) }
                TextButton(onClick = { dayLogs.selectDate(date.plusDays(1)) }) { Text(stringResource(R.string.daily_next_day)) }
            }
            Text(stringResource(R.string.daylog_common_plan), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onEditPlan) { Text(stringResource(R.string.daylog_edit_plan)) }
                TextButton(onClick = onCompare) { Text(stringResource(R.string.daylog_compare_title)) }
            }
            if (quickEntryEnabled) TextButton(onClick = onQuickEntry) { Text(stringResource(R.string.quick_title)) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { dayLogs.openNew(DayLogCategory.OTHER, ""); edit() }, enabled = data.profileId != null) { Text(stringResource(R.string.daylog_add_incidental)) }
                TextButton(onClick = { dayLogs.openNew(DayLogCategory.SLEEP, sleepName); edit() }, enabled = data.profileId != null) { Text(stringResource(R.string.daylog_sleep)) }
                TextButton(onClick = { dayLogs.openNew(DayLogCategory.WAKE, wakeName); edit() }, enabled = data.profileId != null) { Text(stringResource(R.string.daylog_wake)) }
            }
            if (data.profileId == null) Text(stringResource(R.string.daylog_no_profile), Modifier.padding(16.dp))
            if (syncError) Text(stringResource(R.string.daylog_sync_error), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
        })
    }
    if (editable) DayLogEditor(dayLogs)
}
