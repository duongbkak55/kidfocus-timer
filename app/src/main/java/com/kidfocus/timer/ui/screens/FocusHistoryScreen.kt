package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.FocusHistory
import com.kidfocus.timer.domain.model.FocusHistoryRow
import com.kidfocus.timer.ui.theme.KidFocusTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Read-only for children. Deletion controls are supplied only by the PIN-gated parent route. */
@Composable
fun FocusHistoryScreen(
    history: FocusHistory?,
    onBack: () -> Unit,
    onDeleteOne: ((Long) -> Unit)? = null,
    onDeleteAll: (() -> Unit)? = null,
) {
    val colors = KidFocusTheme.colors
    var pendingDelete by remember { mutableStateOf<Long?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().background(colors.background).verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = colors.onBackground)
            }
            Text(stringResource(R.string.history_title), style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold, color = colors.onBackground)
        }
        Spacer(Modifier.height(20.dp))

        if (history != null) {
            Surface(shape = RoundedCornerShape(22.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(stringResource(R.string.history_this_week), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold, color = colors.onSurface)
                    Spacer(Modifier.height(16.dp))
                    val max = history.bars.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
                    val weekdays = listOf(R.string.history_mon, R.string.history_tue, R.string.history_wed,
                        R.string.history_thu, R.string.history_fri, R.string.history_sat, R.string.history_sun)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Bottom) {
                        history.bars.forEachIndexed { index, bar ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${bar.minutes}", style = MaterialTheme.typography.labelSmall, color = colors.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Box(Modifier.height(68.dp), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().height((8 + 60 * bar.minutes / max).dp)
                                        .background(if (bar.date == today) colors.primary else colors.primary.copy(alpha = 0.35f),
                                            RoundedCornerShape(6.dp)))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(weekdays[index]), style = MaterialTheme.typography.labelSmall,
                                    color = colors.onSurface)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.history_week_totals, history.weekMinutes, history.weekSessions),
                        style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
                }
            }
            Spacer(Modifier.height(20.dp))

            if (history.days.isEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 48.dp).testTag("history_empty"),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🦉", fontSize = 64.sp)
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.titleMedium,
                        color = colors.onBackground)
                }
            } else {
                history.days.forEach { day ->
                    val dayTitle = when (day.date) {
                        today -> stringResource(R.string.history_today)
                        today.minusDays(1) -> stringResource(R.string.history_yesterday)
                        else -> day.date.format(DateTimeFormatter.ofPattern("dd/MM"))
                    }
                    Text(dayTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                        color = colors.primary, modifier = Modifier.padding(vertical = 8.dp))
                    day.rows.forEach { row ->
                        HistoryRow(row, onDeleteOne?.let { { pendingDelete = row.id } })
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                if (onDeleteAll != null && history.days.any { day -> day.rows.any { !it.routine } }) {
                    TextButton(onClick = { confirmAll = true }, modifier = Modifier.testTag("history_delete_all")) {
                        Text(stringResource(R.string.history_clear_all))
                    }
                }
            }
        }
    }

    if (pendingDelete != null || confirmAll) {
        AlertDialog(onDismissRequest = { pendingDelete = null; confirmAll = false },
            title = { Text(stringResource(R.string.history_delete_confirm)) },
            text = { Text(stringResource(if (confirmAll) R.string.history_clear_all_confirm else R.string.history_delete_one_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    if (confirmAll) onDeleteAll?.invoke() else pendingDelete?.let { onDeleteOne?.invoke(it) }
                    pendingDelete = null; confirmAll = false
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null; confirmAll = false }) {
                Text(stringResource(R.string.cancel))
            } })
    }
}

@Composable
private fun HistoryRow(row: FocusHistoryRow, onDelete: (() -> Unit)?) {
    val colors = KidFocusTheme.colors
    Surface(shape = RoundedCornerShape(16.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (row.routine) "⭐" else if (row.completed) "✓" else "◷",
                fontSize = 23.sp, color = colors.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title ?: stringResource(R.string.history_free_focus),
                    style = MaterialTheme.typography.titleSmall, color = colors.onSurface,
                    fontWeight = FontWeight.SemiBold)
                val time = Instant.ofEpochMilli(row.timestampMillis).atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
                val detail = if (row.routine) stringResource(R.string.history_routine_done)
                    else stringResource(R.string.history_duration, row.durationSeconds?.div(60) ?: 0)
                Text("$time  ·  $detail", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface.copy(alpha = 0.7f))
                if (!row.completed) Text(stringResource(R.string.history_stopped_early),
                    style = MaterialTheme.typography.labelMedium, color = colors.primary)
            }
            if (onDelete != null && row.id != null) {
                TextButton(onClick = onDelete, modifier = Modifier.testTag("history_delete_${row.id}")) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    }
}
