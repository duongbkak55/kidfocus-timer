package com.kidfocus.timer.ui.screens

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.daylog.DayLogCategory
import com.kidfocus.timer.ui.viewmodel.ChildLogError
import com.kidfocus.timer.ui.viewmodel.ChildQuickLogViewModel
import com.kidfocus.timer.ui.viewmodel.ScheduleAccessViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun childLogEmoji(category: DayLogCategory): String = when (category) {
    DayLogCategory.SLEEP -> "🛏️"
    DayLogCategory.WAKE -> "☀️"
    DayLogCategory.STUDY, DayLogCategory.SCHOOL -> "📚"
    DayLogCategory.HYGIENE -> "🪥"
    DayLogCategory.CHORES -> "🧹"
    DayLogCategory.ENTERTAINMENT -> "🎮"
    DayLogCategory.ROUTINE -> "⭐"
    DayLogCategory.OTHER -> "📝"
}

/** Used by both the child route and the isolated lock-screen activity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildQuickLogScreen(
    viewModel: ChildQuickLogViewModel,
    access: ScheduleAccessViewModel,
    lockScreen: Boolean,
    onBack: () -> Unit,
    onFinished: () -> Unit = onBack,
    onVoiceActivityChanged: (Boolean) -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val child by viewModel.context.collectAsState()
    val config by access.config.collectAsState()
    val account by access.account.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var micAvailable by remember { mutableStateOf(true) }
    val savedLabel = stringResource(R.string.child_log_saved)
    val undoLabel = stringResource(R.string.smart_undo)
    val exit = { viewModel.clearDraft(); onBack() }
    BackHandler(onBack = exit)
    LaunchedEffect(Unit) { access.refresh() }
    val savedIds = state.saved.map { it.id }
    LaunchedEffect(savedIds) {
        if (savedIds.isNotEmpty()) {
            access.refresh()
            val timer = launch { delay(10_000); snackbar.currentSnackbarData?.dismiss() }
            val result = snackbar.showSnackbar(savedLabel, undoLabel, duration = SnackbarDuration.Indefinite)
            timer.cancel()
            if (result == SnackbarResult.ActionPerformed) viewModel.undo { if (lockScreen) onFinished() }
            else if (lockScreen) onFinished()
        }
    }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onVoiceActivityChanged(false)
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(viewModel::editText)
        } else if (lockScreen && result.resultCode != Activity.RESULT_CANCELED) micAvailable = false
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.child_log_title)) }, navigationIcon = {
        IconButton(onClick = exit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (lockScreen && !child.lockScreenAllowed) {
                Text(stringResource(R.string.child_log_parent_disabled), style = MaterialTheme.typography.titleLarge)
                return@Column
            }
            if (child.profileId == null) {
                Text(stringResource(R.string.child_log_unlock_to_select), style = MaterialTheme.typography.titleLarge)
                return@Column
            }
            Text(child.avatar, style = MaterialTheme.typography.headlineLarge)
            if (!account.isSignedIn || !config.enabled || !config.scheduleEnabled || state.error == ChildLogError.UNAVAILABLE) {
                Text(stringResource(R.string.child_log_ask_parent), style = MaterialTheme.typography.titleMedium)
                return@Column
            }
            OutlinedTextField(value = state.text, onValueChange = viewModel::editText,
                modifier = Modifier.fillMaxWidth().testTag("child_log_text"),
                label = { Text(stringResource(R.string.child_log_prompt)) }, minLines = 3, maxLines = 5,
                enabled = !state.busy, supportingText = { Text("${state.text.length}/300") })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (micAvailable) OutlinedButton(onClick = {
                    try {
                        onVoiceActivityChanged(true)
                        voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
                    } catch (_: ActivityNotFoundException) { onVoiceActivityChanged(false); micAvailable = false }
                    catch (_: SecurityException) { onVoiceActivityChanged(false); micAvailable = false }
                }, enabled = !state.busy) { Text(stringResource(R.string.child_log_speak)) }
                Button(onClick = { viewModel.send(config, lockScreen) }, enabled = !state.busy && state.text.isNotBlank(),
                    modifier = Modifier.testTag("child_log_send")) { Text(stringResource(R.string.child_log_send)) }
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.saved.forEach { entry ->
                ElevatedCard(Modifier.fillMaxWidth()) { Text("${childLogEmoji(entry.category)} ${entry.name} · ${logTime(entry.startMinute)}",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge) }
            }
            state.unclear.forEach { name -> Text(stringResource(R.string.child_log_unclear, name), maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error) }
            if (state.unclear.isNotEmpty()) Text(stringResource(R.string.child_log_say_time))
            state.question?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            when (state.error) {
                ChildLogError.QUOTA -> Text(stringResource(R.string.child_log_quota), color = MaterialTheme.colorScheme.error)
                ChildLogError.NETWORK, ChildLogError.STORAGE, ChildLogError.PROFILE_CHANGED ->
                    Text(stringResource(R.string.child_log_retry), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
    }
}
