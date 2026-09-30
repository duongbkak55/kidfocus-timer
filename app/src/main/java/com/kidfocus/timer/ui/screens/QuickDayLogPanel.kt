package com.kidfocus.timer.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.R
import com.kidfocus.timer.data.remote.AiConfig
import com.kidfocus.timer.data.remote.AiUsage
import com.kidfocus.timer.ui.viewmodel.*
import java.time.LocalDate

@Composable
fun QuickDayLogPanel(viewModel: QuickDayLogViewModel, data: DayLogData, date: LocalDate,
    config: AiConfig, signedIn: Boolean, onUsage: (AiUsage) -> Unit = {}, ownerId: String? = null) {
    val state by viewModel.state.collectAsState()
    var expanded by rememberSaveable { mutableStateOf(false) }
    var micAvailable by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(data.profileId, date, ownerId) { viewModel.context(data.profileId, date, ownerId) }
    LaunchedEffect(state.usage, ownerId) { if (state.ownerId == ownerId) state.usage?.let(onUsage) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
            viewModel.editText(listOf(state.text, it).filter(String::isNotBlank).joinToString("\n"))
        }
    }
    val enabled = config.enabled && config.scheduleEnabled && signedIn && data.profileId != null
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { expanded = !expanded }) { Text(stringResource(R.string.log_today_title)) }
        if (expanded) {
            Text(date.toString(), style = MaterialTheme.typography.labelMedium)
            if (!config.scheduleEnabled || !config.enabled) Text(stringResource(R.string.quick_disabled))
            else if (!signedIn) Text(stringResource(R.string.log_sign_in))
            ScheduleUsage(config.usage, config.scheduleLogCost)
            OutlinedTextField(state.text, viewModel::editText, Modifier.fillMaxWidth().testTag("log_text"),
                label = { Text(stringResource(R.string.log_input)) }, minLines = 2, maxLines = 6, enabled = !state.busy,
                supportingText = { Text("${state.text.length}/2000") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)) }
                    catch (_: ActivityNotFoundException) { micAvailable = false }
                    catch (_: SecurityException) { micAvailable = false }
                }, enabled = !state.busy && micAvailable) { Text(stringResource(R.string.log_speak)) }
                Button(onClick = { viewModel.preview(data, date) }, enabled = enabled && !state.busy && state.text.isNotBlank()) {
                    Text(pluralStringResource(R.plurals.log_preview, config.scheduleLogCost, config.scheduleLogCost))
                }
            }
            if (!micAvailable) Text(stringResource(R.string.log_mic_unavailable))
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.preview?.let { preview ->
                Text(stringResource(R.string.log_review_hint), style = MaterialTheme.typography.bodySmall)
                preview.questions.forEach { Text(it) }
                if (preview.entries.isEmpty() && preview.questions.isEmpty()) Text(stringResource(R.string.log_empty))
                preview.entries.forEachIndexed { index, e ->
                    val plan = e.planRef?.let { state.request?.plans?.get(it) }
                    OutlinedCard(Modifier.fillMaxWidth().testTag("log_candidate_$index").toggleable(index in state.selected, enabled = !state.busy, role = Role.Checkbox) { viewModel.select(index, it) }) {
                        Row(Modifier.padding(8.dp)) {
                            Checkbox(index in state.selected, onCheckedChange = null)
                            Column(Modifier.weight(1f)) {
                                Text("${e.date} · ${e.name}")
                                Text(logTime(e.startMinute) + (e.endMinute?.let { end -> " – " + logTime(end) + (if (end < e.startMinute) " (+1)" else "") } ?: ""))
                                Text(if (plan == null) stringResource(R.string.log_incidental) else stringResource(R.string.log_matched, planName(plan)), style = MaterialTheme.typography.bodySmall)
                                if (index in state.future) Text(stringResource(R.string.log_not_yet), color = MaterialTheme.colorScheme.error)
                                if (!e.selectedByDefault) Text(stringResource(R.string.log_low_confidence), color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                Button(onClick = { viewModel.save(data) }, enabled = !state.busy && state.selected.isNotEmpty(), modifier = Modifier.testTag("log_save")) {
                    Text(stringResource(R.string.log_save))
                }
            }
        }
        if (state.saved) Text(stringResource(R.string.log_saved))
        if (state.restored) Text(stringResource(R.string.log_restored))
        if (state.undoBatch.isNotEmpty() && state.undoBatch.first().profileId == data.profileId) TextButton(onClick = viewModel::undo, enabled = !state.busy,
            modifier = Modifier.testTag("log_undo")) { Text(stringResource(R.string.smart_undo)) }
        state.error?.let { error -> Text(stringResource(when(error) {
            LogError.QUOTA -> R.string.log_quota
            LogError.SIGN_IN -> R.string.log_sign_in
            LogError.DISABLED -> R.string.quick_disabled
            LogError.PROFILE_CHANGED -> R.string.daylog_profile_changed
            LogError.STALE -> R.string.log_stale
            LogError.STORAGE -> R.string.daylog_storage_error
            LogError.NETWORK -> R.string.log_failed
        }), color = MaterialTheme.colorScheme.error) }
    }
}
