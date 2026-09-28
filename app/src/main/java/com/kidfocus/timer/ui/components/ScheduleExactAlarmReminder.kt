package com.kidfocus.timer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kidfocus.timer.R
import com.kidfocus.timer.ui.viewmodel.ScheduleAlarmPermissionViewModel

/** Lives above the PIN routes so returning from Settings also reschedules behind the gate. */
@Composable
fun ScheduleAlarmPermissionLifecycle(viewModel: ScheduleAlarmPermissionViewModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, viewModel) {
        lifecycle.addObserver(viewModel)
        onDispose { lifecycle.removeObserver(viewModel) }
    }
}

@Composable
fun ScheduleExactAlarmReminder(viewModel: ScheduleAlarmPermissionViewModel) {
    val visible by viewModel.reminderVisible.collectAsState()
    val unavailable by viewModel.settingsUnavailable.collectAsState()
    if (!visible) return
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.schedule_exact_alarm_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.routine_exact_alarm_description), style = MaterialTheme.typography.bodyMedium)
            if (unavailable) Text(stringResource(R.string.schedule_exact_alarm_unavailable))
            // Stacked actions also fit large fonts and narrow screens.
            OutlinedButton(onClick = viewModel::requestPermission) { Text(stringResource(R.string.routine_open_settings)) }
            TextButton(onClick = viewModel::dismissReminder) { Text(stringResource(R.string.schedule_exact_alarm_dismiss)) }
        }
    }
}
