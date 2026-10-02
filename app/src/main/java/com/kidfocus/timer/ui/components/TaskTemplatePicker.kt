package com.kidfocus.timer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.R
import com.kidfocus.timer.domain.model.TaskCategory
import com.kidfocus.timer.domain.model.TaskType
import java.util.Calendar

/** One shared picker for focus schedules and deadline routines. */
@Composable
fun TaskTemplatePicker(
    recentTypes: List<TaskType> = emptyList(),
    onDismiss: () -> Unit,
    onSelect: (type: TaskType, localizedName: String) -> Unit,
    onManual: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val all = TaskType.entries.filter { it != TaskType.CUSTOM }
    val todayDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val today = all.filter { todayDay in it.defaultDays }.take(6)
    val popularOrder = listOf(
        TaskType.LEARNING_GAMES, TaskType.HOMEWORK, TaskType.TEST_PRACTICE, TaskType.READING, TaskType.DINNER,
        TaskType.BATH, TaskType.BRUSH_TEETH, TaskType.SLEEP,
    )
    val normalized = query.trim().lowercase()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                stringResource(R.string.task_picker_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.task_picker_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.task_picker_search)) },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onManual,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text(stringResource(R.string.task_picker_manual), Modifier.padding(start = 8.dp))
            }
        }

        LazyColumn(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (normalized.isNotEmpty()) {
                val matches = all.filter {
                    it.displayName.lowercase().contains(normalized) ||
                        taskSearchAliases(it).contains(normalized)
                }
                item { TemplateSectionHeader(stringResource(R.string.task_picker_results)) }
                items(matches, key = { it.name }) { TemplateRow(it, onSelect) }
                if (matches.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.task_picker_no_results),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                }
            } else {
                item { TemplateSectionHeader(stringResource(R.string.task_picker_today)) }
                items(today, key = { "today_${it.name}" }) { TemplateRow(it, onSelect) }

                val recent = recentTypes.distinct().filter { it != TaskType.CUSTOM }.take(5)
                if (recent.isNotEmpty()) {
                    item { TemplateSectionHeader(stringResource(R.string.task_picker_recent)) }
                    items(recent, key = { "recent_${it.name}" }) { TemplateRow(it, onSelect) }
                }

                item { TemplateSectionHeader(stringResource(R.string.task_picker_popular)) }
                items(popularOrder, key = { "popular_${it.name}" }) { TemplateRow(it, onSelect) }

                TaskCategory.entries.forEach { category ->
                    item { TemplateSectionHeader(taskCategoryLabel(category)) }
                    items(all.filter { it.category == category }, key = { "all_${it.name}" }) {
                        TemplateRow(it, onSelect)
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun TemplateSectionHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).semantics { heading() },
    )
}

@Composable
private fun TemplateRow(type: TaskType, onSelect: (TaskType, String) -> Unit) {
    val label = taskTypeLabel(type)
    ListItem(
        headlineContent = { Text(label, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(stringResource(R.string.task_picker_default_time, type.defaultHour, type.defaultMinute))
        },
        leadingContent = { Text(type.emoji, style = MaterialTheme.typography.headlineSmall) },
        modifier = Modifier.clickable { onSelect(type, label) },
    )
    HorizontalDivider()
}

private fun taskSearchAliases(type: TaskType): String = when (type) {
    TaskType.BREAKFAST, TaskType.LUNCH, TaskType.DINNER -> "meal food an com bua"
    TaskType.SLEEP -> "bed bedtime ngu"
    TaskType.BATH, TaskType.BRUSH_TEETH -> "clean hygiene tam danh rang"
    TaskType.HOMEWORK, TaskType.MORNING_STUDY, TaskType.AFTERNOON_STUDY -> "learn study hoc bai"
    TaskType.TEST_PRACTICE -> "test exam practice kiem tra luyen de lam de"
    TaskType.LEARNING_GAMES -> "learn game math vietnamese english hoc toan tieng viet luyen tap"
    else -> ""
}
