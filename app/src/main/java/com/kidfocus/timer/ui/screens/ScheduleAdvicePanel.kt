package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.schedule.*
import com.kidfocus.timer.ui.components.scheduleDaysLabel
import com.kidfocus.timer.ui.viewmodel.*
import java.time.LocalDate

@Composable
internal fun ScheduleAdvicePanel(current: SmartScheduleUiState, busy: Boolean, access: ScheduleAccessViewModel, viewModel: SmartScheduleViewModel) {
    val config by access.config.collectAsState()
    val account by access.account.collectAsState()
    val signingIn by access.signingIn.collectAsState()
    val signInFailed by access.signInFailed.collectAsState()
    val advice by viewModel.advice.collectAsState()
    val context = LocalContext.current
    var expanded by remember(current.profile.id) { mutableStateOf(false) }
    var confirmRemove by remember(current.profile.id) { mutableStateOf<Set<Int>?>(null) }
    LaunchedEffect(advice.usage) { advice.usage?.let(access::updateUsage) }
    if (config.scheduleEnabled && config.enabled && current.findings.isNotEmpty()) {
        Button(onClick = { expanded = !expanded }, enabled = !busy) { Text(stringResource(R.string.advise_title, config.scheduleAdviseCost)) }
    }
    if (expanded && config.scheduleEnabled && config.enabled) {
        Text(stringResource(R.string.advise_privacy), style = MaterialTheme.typography.bodySmall)
        NoteTag.entries.forEach { tag ->
            FilterChip(selected = tag in advice.tags, onClick = { viewModel.toggleTag(tag) }, enabled = !busy,
                label = { Text(stringResource(tagLabel(tag))) })
        }
        OutlinedTextField(value = advice.note, onValueChange = viewModel::editNote, enabled = !busy,
            label = { Text(stringResource(R.string.advise_note)) }, supportingText = { Text(stringResource(R.string.advise_note_hint, advice.note.length)) }, modifier = Modifier.fillMaxWidth())
        if (!account.isSignedIn) {
            Button(onClick = { access.signIn(context) }, enabled = !signingIn && !busy) { Text(stringResource(R.string.quick_sign_in)) }
            if (signInFailed) Text(stringResource(R.string.quick_sign_in_failed), color = MaterialTheme.colorScheme.error)
        } else {
            // ADVISE uses the shared credit/question budget, not the PARSE-only free cap.
            val available = minOf(config.usage.remainingQuestions, config.usage.remainingCredits / config.scheduleAdviseCost.coerceAtLeast(1))
            Text(stringResource(R.string.advise_usage, available, config.scheduleAdviseCost))
            Button(onClick = viewModel::requestAdvice, enabled = !busy && current.findings.isNotEmpty() && available > 0) { Text(stringResource(R.string.advise_send)) }
        }
    }
    advice.error?.let { error -> Text(stringResource(when (error) {
        AdviceError.QUOTA -> R.string.advise_quota
        AdviceError.SIGN_IN -> R.string.quick_sign_in
        AdviceError.DISABLED -> R.string.advise_disabled
        AdviceError.STALE -> R.string.advise_stale
        AdviceError.APPLY -> R.string.smart_error
        AdviceError.NETWORK -> R.string.advise_network_error
    }), color = MaterialTheme.colorScheme.error) }
    advice.advice?.let { result ->
        Text(result.summary, style = MaterialTheme.typography.titleMedium)
        val stale = advice.expected != current.schedule
        if (stale) Text(stringResource(R.string.advise_stale), color = MaterialTheme.colorScheme.error)
        advice.rows.forEach { row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(row.reason)
                    row.rejection?.let { rejection ->
                        Text(stringResource(R.string.advise_removed, stringResource(when (rejection) {
                            AdviceRejection.INVALID -> R.string.advise_invalid
                            AdviceRejection.HIGH_INCREASED -> R.string.advise_high_conflict
                            AdviceRejection.SCHOOL_PROTECTED -> R.string.advise_school_protected
                            AdviceRejection.STUDY_PROTECTED -> R.string.advise_study_protected
                        })))
                    } ?: run {
                        row.changes.forEach { change -> Text(adviceChangeLabel(change, current)) }
                        Text(if (row.fixed.isEmpty()) stringResource(R.string.advise_no_fix) else stringResource(R.string.advise_fixes,
                            row.fixed.map { ruleLabel(it) }.joinToString()))
                        if (row.requiresPlan) {
                            Text(stringResource(R.string.advise_gradual_help, SchedulePlan.STEP_MINUTES, SchedulePlan.STEP_DAYS.toInt()))
                            Button(onClick = { viewModel.startPlan(row.index) }, enabled = !busy && !stale && current.plan == null) { Text(stringResource(R.string.advise_gradual)) }
                        } else {
                            Row {
                                Checkbox(modifier = Modifier.semantics { contentDescription = row.reason }, checked = row.index in advice.selected, onCheckedChange = { viewModel.select(row.index, it) }, enabled = !busy && !stale)
                                TextButton(onClick = { if (row.removes) confirmRemove = setOf(row.index) else viewModel.applyAdvice(setOf(row.index)) }, enabled = !busy && !stale) { Text(stringResource(R.string.smart_apply)) }
                            }
                        }
                    }
                }
            }
        }
        Button(onClick = {
            if (advice.rows.any { it.index in advice.selected && it.removes }) confirmRemove = advice.selected
            else viewModel.applyAdvice(advice.selected)
        }, enabled = !busy && !stale && advice.selected.isNotEmpty()) { Text(stringResource(R.string.advise_apply_selected)) }
    }
    confirmRemove?.let { indices ->
        AlertDialog(onDismissRequest = { confirmRemove = null }, title = { Text(stringResource(R.string.advise_remove_title)) },
            text = { Text(stringResource(R.string.advise_remove_confirm)) },
            confirmButton = { TextButton(onClick = { confirmRemove = null; viewModel.applyAdvice(indices, removalsConfirmed = true) }) { Text(stringResource(R.string.advise_confirm)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
internal fun SchedulePlanBanner(current: SmartScheduleUiState, busy: Boolean, viewModel: SmartScheduleViewModel) {
    val plan = current.plan ?: return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val times = plan.timesAt(plan.nextStep).entries.groupBy { it.value }.map { (time, entries) ->
                "${scheduleDaysLabel(DayCodec.toCalendar(entries.map { it.key }.toSet()))}: $time"
            }.joinToString(" · ")
            val kind = stringResource(if (plan.kind == PlanKind.BED) R.string.smart_bed else R.string.smart_wake)
            Text(stringResource(R.string.advise_plan_step, plan.nextStep, plan.totalSteps, kind, times))
            if (plan.due(LocalDate.now())) Button(onClick = viewModel::applyPlanStep, enabled = !busy) { Text(stringResource(R.string.smart_apply)) }
            else Text(stringResource(R.string.advise_plan_due, plan.nextOn.toString()))
            TextButton(onClick = viewModel::cancelPlan, enabled = !busy) { Text(stringResource(R.string.advise_cancel_plan)) }
        }
    }
}

@Composable
private fun adviceChangeLabel(change: ScheduleChange, current: SmartScheduleUiState): String {
    val days = when (change) { is ScheduleChange.TaskChange -> change.days; is ScheduleChange.SetBed -> change.times.keys; is ScheduleChange.SetWake -> change.times.keys; else -> emptySet() }
    val names = (change as? ScheduleChange.TaskChange)?.let { edit -> current.schedule.tasks.find { it.id == edit.taskId }?.name }.orEmpty()
    val action = when (change) {
        is ScheduleChange.MoveTask -> stringResource(R.string.smart_suggest_move, change.start.toString())
        is ScheduleChange.ResizeTask -> stringResource(R.string.smart_suggest_resize, change.durationMinutes)
        is ScheduleChange.RemoveTask -> stringResource(R.string.advise_remove_task)
        is ScheduleChange.SetBed -> stringResource(R.string.smart_suggest_bed, change.times.values.first().toString())
        is ScheduleChange.SetWake -> stringResource(R.string.advise_suggest_wake, change.times.values.first().toString())
        else -> ""
    }
    return "${scheduleDaysLabel(DayCodec.toCalendar(days))} · $names $action"
}

internal fun tagLabel(tag: NoteTag): Int = when (tag) {
    NoteTag.DAYTIME_SLEEPY -> R.string.advise_tag_sleepy
    NoteTag.HARD_TO_WAKE -> R.string.advise_tag_wake
    NoteTag.TANTRUM_EVENING -> R.string.advise_tag_evening
    NoteTag.LONG_HOMEWORK -> R.string.advise_tag_homework
    NoteTag.LITTLE_PLAY -> R.string.advise_tag_play
}

@Composable
private fun ruleLabel(rule: RuleId): String = stringResource(when (rule) {
    RuleId.SLEEP_SHORT -> R.string.advise_rule_sleep
    RuleId.SOCIAL_JETLAG -> R.string.advise_rule_weekend
    RuleId.SCREEN_BEFORE_BED -> R.string.smart_screen_before_bed
    RuleId.OVERLAP -> R.string.smart_overlap
    RuleId.LATE_HOMEWORK -> R.string.smart_late_homework
    RuleId.FOCUS_TOO_LONG -> R.string.advise_rule_focus
    RuleId.NO_FREE_TIME -> R.string.smart_no_free_time
    RuleId.MORNING_LATE_PATTERN -> R.string.advise_rule_morning
})
