package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.R
import com.kidfocus.timer.ui.components.SettingSliderRow

/** A free activity is a session today, never a disguised recurring schedule entry. */
@Composable
fun TodayActivityScreen(onBack: () -> Unit, onStart: (String, Int) -> Unit, onWeekly: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var minutes by rememberSaveable { mutableIntStateOf(25) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
        }
        Text(stringResource(R.string.activity_scope_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.activity_scope_today), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.activity_scope_today_help))
        OutlinedTextField(value = name, onValueChange = { name = it.take(60) },
            label = { Text(stringResource(R.string.schedule_activity_name)) }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("today_activity_name"))
        SettingSliderRow(title = stringResource(R.string.schedule_focus), value = minutes.toFloat(),
            onValueChange = { minutes = it.toInt() }, valueRange = 5f..120f, steps = 22,
            valueLabel = stringResource(R.string.schedule_minutes_short, minutes))
        Button(onClick = { onStart(name.trim(), minutes) }, enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().testTag("today_activity_start")) {
            Text(stringResource(R.string.activity_scope_start))
        }
        HorizontalDivider()
        Text(stringResource(R.string.activity_scope_weekly_help))
        OutlinedButton(onClick = onWeekly, modifier = Modifier.fillMaxWidth().testTag("today_activity_weekly")) {
            Text(stringResource(R.string.activity_scope_weekly))
        }
    }
}
