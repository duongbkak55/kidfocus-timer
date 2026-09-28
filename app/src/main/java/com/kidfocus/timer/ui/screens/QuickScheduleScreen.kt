package com.kidfocus.timer.ui.screens

import android.app.Activity
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kidfocus.timer.R
import coil.compose.AsyncImage
import com.kidfocus.timer.data.remote.canImportImage
import com.kidfocus.timer.data.remote.AiUsage
import com.kidfocus.timer.domain.schedule.*
import com.kidfocus.timer.ui.components.scheduleDaysLabel
import com.kidfocus.timer.ui.viewmodel.*
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun speechIntent(language: String) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
    .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

@Composable
fun ScheduleUsage(usage: AiUsage, parseCost: Int = 1) {
    val days = usage.earlyAccessUntil?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0) + 86_399_999) / 86_400_000 } ?: 0
    val remaining = minOf(usage.remainingQuestions, usage.remainingCredits / parseCost.coerceAtLeast(1), usage.remainingScheduleParses ?: Int.MAX_VALUE)
    Text(if (usage.tier == "early") stringResource(R.string.quick_usage_early, days, remaining)
        else stringResource(R.string.quick_usage, remaining), style = MaterialTheme.typography.bodySmall)
}

@Composable
fun QuickScheduleScreen(onBack: () -> Unit, access: ScheduleAccessViewModel, viewModel: QuickScheduleViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val config by access.config.collectAsState()
    val account by access.account.collectAsState()
    val signingIn by access.signingIn.collectAsState()
    val signInFailed by access.signInFailed.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val current by viewModel.current.collectAsState()
    val context = LocalContext.current
    val canImportImage = config.canImportImage(account.isSignedIn)
    var imageSource by remember { mutableStateOf(false) }
    var cropping by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia(), viewModel::pickResult)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture(), viewModel::captureResult)
    val exit = { viewModel.discardImages(); onBack() }
    BackHandler(onBack = exit)
    LaunchedEffect(canImportImage, state.busy) { if (!canImportImage && !state.busy) viewModel.discardImages() }
    if (imageSource) AlertDialog(onDismissRequest = { imageSource = false }, title = { Text(stringResource(R.string.quick_image)) },
        text = { Text(stringResource(R.string.quick_image_source)) },
        confirmButton = { TextButton(onClick = {
            imageSource = false; viewModel.beginImageSelection()
            try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            catch (_: Exception) { viewModel.imageSelectionFailed() }
        }) { Text(stringResource(R.string.quick_image_library)) } },
        dismissButton = { TextButton(onClick = {
            imageSource = false
            viewModel.createCapture()?.let { uri ->
                try { camera.launch(uri) } catch (_: Exception) { viewModel.imageSelectionFailed() }
            }
        }) { Text(stringResource(R.string.quick_image_camera)) } })
    if (cropping) state.image?.let { image -> ScheduleImageCropDialog(image, { cropping = false }) {
        cropping = false; viewModel.cropImage(it)
    } }
    val locale = Locale.getDefault().toLanguageTag()
    var speechLanguage by remember { mutableStateOf("vi-VN") }
    var micAvailable by remember { mutableStateOf(speechIntent("vi-VN").resolveActivity(context.packageManager) != null) }
    var retrySpeech by remember { mutableStateOf(false) }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { recognized ->
                viewModel.editText(listOf(state.text.trim(), recognized).filter { it.isNotBlank() }.joinToString("\n"))
            }
        } else if (result.resultCode != Activity.RESULT_CANCELED && speechLanguage == "vi-VN" && locale != "vi-VN") retrySpeech = true
    }
    LaunchedEffect(retrySpeech) {
        if (retrySpeech) {
            retrySpeech = false
            speechLanguage = locale
            try { voice.launch(speechIntent(locale)) } catch (_: ActivityNotFoundException) { micAvailable = false }
        }
    }
    LaunchedEffect(Unit) { access.refresh() }
    LaunchedEffect(state.error) { if ((state.error == QuickScheduleError.DISABLED || state.error == QuickScheduleError.IMAGE_TIER)) access.refresh() }
    LaunchedEffect(state.usage) { state.usage?.let(access::updateUsage) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val saved = stringResource(R.string.smart_saved)
    val restored = stringResource(R.string.smart_restored)
    val undo = stringResource(R.string.smart_undo)
    LaunchedEffect(viewModel, saved, restored, undo) {
        viewModel.events.collect { event ->
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                if (event == ScheduleEvent.SAVED) {
                    val timer = launch { delay(10_000); snackbar.currentSnackbarData?.dismiss() }
                    val result = snackbar.showSnackbar(saved, undo, duration = SnackbarDuration.Indefinite)
                    timer.cancel()
                    if (result == SnackbarResult.ActionPerformed) viewModel.undo()
                } else snackbar.showSnackbar(restored)
            }
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.quick_title)) }, navigationIcon = {
        IconButton(onClick = exit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
    }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            profile?.let { Text(it.name, style = MaterialTheme.typography.titleLarge) }
            Text(stringResource(R.string.quick_privacy), style = MaterialTheme.typography.bodySmall)
            if (!config.scheduleEnabled) {
                Text(stringResource(R.string.quick_disabled))
                TextButton(onClick = access::refresh) { Text(stringResource(R.string.quick_retry)) }
                return@Column
            }
            if (!account.isSignedIn) {
                Text(stringResource(R.string.quick_sign_in_help))
                Button(onClick = { access.signIn(context) }, enabled = account.configured && !signingIn) { Text(stringResource(R.string.quick_sign_in)) }
                if (signInFailed) Text(stringResource(R.string.quick_sign_in_failed), color = MaterialTheme.colorScheme.error)
            } else ScheduleUsage(config.usage, if (state.image == null) config.scheduleParseCost else config.scheduleImageCost)
            OutlinedTextField(value = state.text, onValueChange = viewModel::editText, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.quick_input)) }, minLines = 4, maxLines = 10,
                enabled = !state.busy, supportingText = { Text("${state.text.length}/2000") })
            if (micAvailable) OutlinedButton(onClick = {
                speechLanguage = "vi-VN"
                try { voice.launch(speechIntent(speechLanguage)) } catch (_: ActivityNotFoundException) { micAvailable = false }
            }, enabled = !state.busy) { Text(stringResource(R.string.quick_mic)) }
            if (canImportImage) OutlinedButton(onClick = { imageSource = true }, enabled = !state.busy) { Text(stringResource(R.string.quick_image)) }
            state.image?.let { image ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AsyncImage(image.file, stringResource(R.string.quick_image_preview), Modifier.fillMaxWidth().heightIn(max = 240.dp))
                        Text(stringResource(R.string.quick_image_privacy))
                        Text(stringResource(R.string.quick_image_cost, config.scheduleImageCost))
                        Row(Modifier.fillMaxWidth().toggleable(state.imageConfirmed, enabled = !state.busy, role = Role.Checkbox,
                            onValueChange = viewModel::confirmImage)) {
                            Checkbox(state.imageConfirmed, onCheckedChange = null, enabled = !state.busy)
                            Text(stringResource(R.string.quick_image_confirm), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { cropping = true }, enabled = !state.busy) { Text(stringResource(R.string.quick_image_crop)) }
                            TextButton(onClick = viewModel::discardImages, enabled = !state.busy) { Text(stringResource(R.string.quick_image_remove)) }
                        }
                    }
                }
            }
            Text(stringResource(R.string.quick_examples), style = MaterialTheme.typography.labelLarge)
            listOf(R.string.quick_example_one, R.string.quick_example_two, R.string.quick_example_three).forEach { resource ->
                val example = stringResource(resource)
                TextButton(onClick = { viewModel.editText(example) }, enabled = !state.busy) { Text(example) }
            }
            Button(onClick = viewModel::parse, enabled = account.isSignedIn && !state.busy && (if (state.image != null) canImportImage && state.imageConfirmed else state.text.isNotBlank()) && current != null) {
                Text(stringResource(if (state.busy) R.string.quick_wait else R.string.quick_parse))
            }
            state.error?.let { error ->
                Text(stringResource(when (error) {
                    QuickScheduleError.QUOTA -> R.string.quick_error_quota
                    QuickScheduleError.PARSE -> R.string.quick_error_parse
                    QuickScheduleError.SIGN_IN -> R.string.quick_sign_in_help
                    QuickScheduleError.DISABLED -> R.string.quick_disabled
                    QuickScheduleError.STALE -> R.string.quick_error_stale
                    QuickScheduleError.APPLY -> R.string.smart_error
                    QuickScheduleError.IMAGE -> R.string.quick_image_error
                    QuickScheduleError.IMAGE_TIER -> R.string.quick_image_tier
                    QuickScheduleError.NETWORK -> R.string.quick_error_network
                }), color = MaterialTheme.colorScheme.error)
            }
            state.draft?.let { draft ->
                val expected = state.expected
                val profileId = state.profileId
                val valid = draft.items.filter { it.selected }.all { runCatching { it.validate() }.isSuccess }
                val warnings = remember(draft, expected, state.ageBand) {
                    if (valid && expected != null && profileId != null) runCatching { draft.warnings(profileId, expected, state.ageBand) }.getOrDefault(emptyMap()) else emptyMap()
                }
                Text(stringResource(R.string.quick_preview), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.quick_review_help))
                draft.questions.forEach { Text("• $it") }
                draft.items.forEach { item -> DraftRow(item, warnings[item.key].orEmpty(), !state.busy, viewModel::editItem) }
                val stale = current != expected
                if (stale) Text(stringResource(R.string.quick_error_stale), color = MaterialTheme.colorScheme.error)
                val changes = if (valid && expected != null && profileId != null) runCatching { draft.changes(profileId, expected) } else null
                if (changes?.isFailure == true) Text(stringResource(R.string.quick_duplicate_hours), color = MaterialTheme.colorScheme.error)
                val hasChanges = changes?.getOrNull()?.isNotEmpty() == true
                Button(onClick = viewModel::apply, enabled = hasChanges && !state.busy && !stale && account.isSignedIn) { Text(stringResource(R.string.smart_apply)) }
            }
            if (state.saved) OutlinedButton(onClick = viewModel::undo, enabled = !state.busy) { Text(stringResource(R.string.smart_restore_previous)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftRow(item: DraftItem, warnings: Set<RuleId>, enabled: Boolean, onChange: (DraftItem) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(value = item.selected, enabled = enabled, role = Role.Checkbox,
                onValueChange = { onChange(item.copy(selected = it)) })) {
                Checkbox(checked = item.selected, onCheckedChange = null, enabled = enabled)
                Column(Modifier.weight(1f)) {
                    Text(when (item.kind) {
                        DraftKind.TASK -> "${item.emoji} ${item.name}"
                        DraftKind.WAKE -> stringResource(R.string.smart_wake)
                        DraftKind.BED -> stringResource(R.string.smart_bed)
                        DraftKind.SCHOOL -> item.name
                    }, style = MaterialTheme.typography.titleMedium)
                    if (item.confidence < 0.6) Text(stringResource(R.string.quick_check), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(scheduleDaysLabel(DayCodec.toCalendar(item.days)))
            DayOfWeek.entries.chunked(4).forEach { row ->
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    row.forEach { day -> FilterChip(selected = day in item.days, enabled = enabled,
                        onClick = { onChange(item.copy(days = if (day in item.days) item.days - day else item.days + day)) },
                        label = { Text(scheduleDaysLabel(setOf(DayCodec.toCalendar(day)))) }) }
                }
            }
            DraftTime(R.string.smart_start, item.start, enabled) { onChange(item.copy(start = it)) }
            if (item.kind == DraftKind.SCHOOL) DraftTime(R.string.smart_end, checkNotNull(item.end), enabled) { onChange(item.copy(end = it)) }
            if (item.kind == DraftKind.TASK) {
                var duration by remember(item.key, item.taskId) { mutableStateOf(item.durationMin.toString()) }
                OutlinedTextField(value = duration, onValueChange = { value ->
                    if (value.length <= 3 && value.all(Char::isDigit)) {
                        duration = value
                        onChange(item.copy(durationMin = value.toIntOrNull() ?: 0))
                    }
                }, label = { Text(stringResource(R.string.quick_duration)) }, enabled = enabled, singleLine = true,
                    isError = item.durationMin !in 5..120, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (item.selected && runCatching { item.validate() }.isFailure) Text(stringResource(R.string.quick_invalid_item), color = MaterialTheme.colorScheme.error)
            warnings.forEach { rule -> Text(stringResource(if (rule == RuleId.OVERLAP) R.string.quick_warn_overlap else R.string.quick_warn_sleep), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun DraftTime(label: Int, value: LocalTime, enabled: Boolean, onChange: (LocalTime) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        TimePickerDialog(context, { _, hour, minute -> onChange(LocalTime.of(hour, minute)) }, value.hour, value.minute, true).show()
    }, enabled = enabled) { Text(stringResource(R.string.smart_time_field, stringResource(label), value.toString())) }
}
