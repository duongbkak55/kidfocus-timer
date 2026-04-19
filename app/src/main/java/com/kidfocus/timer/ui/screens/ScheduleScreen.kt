package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.domain.model.ScheduledTask
import com.kidfocus.timer.domain.model.TaskCategory
import com.kidfocus.timer.domain.model.TaskType
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ScheduleViewModel

@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    onBack: () -> Unit,
    onEditTask: (ScheduledTask) -> Unit,
    onNewTask: (TaskType) -> Unit,
) {
    val colors = KidFocusTheme.colors
    val tasks by viewModel.tasks.collectAsState()
    val byCategory = TaskType.byCategory()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = colors.onBackground,
                    )
                }
                Text(
                    text = "Lịch học",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Predefined tasks grouped by category
                byCategory.forEach { (category, types) ->
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        CategoryHeader(category)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(types) { type ->
                        val existing = tasks.find { !it.isCustom && it.taskType == type }
                        PredefinedTaskCard(
                            type = type,
                            existingTask = existing,
                            onAdd = { onNewTask(type) },
                            onEdit = { existing?.let { onEditTask(it) } },
                            onToggle = { existing?.let { viewModel.toggleEnabled(it) } },
                        )
                    }
                }

                // Custom tasks section
                val customTasks = tasks.filter { it.isCustom }
                if (customTasks.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        CategoryHeader(label = "⭐  Lịch tùy chỉnh")
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    items(customTasks) { task ->
                        ScheduledTaskCard(
                            task = task,
                            onEdit = { onEditTask(task) },
                            onToggle = { viewModel.toggleEnabled(task) },
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { onNewTask(TaskType.CUSTOM) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = colors.primary,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Tạo lịch mới", tint = colors.onPrimary)
        }
    }
}

@Composable
private fun CategoryHeader(category: TaskCategory? = null, label: String? = null) {
    val colors = KidFocusTheme.colors
    val text = label ?: "${category!!.emoji}  ${category.displayName}"
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = colors.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun PredefinedTaskCard(
    type: TaskType,
    existingTask: ScheduledTask?,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val hasTask = existingTask != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = type.emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                if (hasTask) {
                    Text(
                        text = "${existingTask!!.timeFormatted} • ${existingTask.daysLabel} • ${existingTask.focusDurationMinutes}p",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.55f),
                    )
                } else {
                    val h = type.defaultHour.toString().padStart(2, '0')
                    val m = type.defaultMinute.toString().padStart(2, '0')
                    Text(
                        text = "$h:$m • ${type.defaultFocusMinutes} phút",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onBackground.copy(alpha = 0.35f),
                    )
                }
            }

            if (hasTask) {
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Sửa", tint = colors.primary, modifier = Modifier.size(18.dp))
                }
                Switch(
                    checked = existingTask!!.enabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.primary,
                        checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                    ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(colors.primary.copy(alpha = 0.12f))
                        .clickable { onAdd() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "+ Thêm",
                        color = colors.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledTaskCard(
    task: ScheduledTask,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = task.emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${task.timeFormatted} • ${task.daysLabel} • ${task.focusDurationMinutes}p",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onBackground.copy(alpha = 0.55f),
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "Sửa", tint = colors.primary, modifier = Modifier.size(18.dp))
            }
            Switch(
                checked = task.enabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.primary,
                    checkedTrackColor = colors.primary.copy(alpha = 0.4f),
                ),
            )
        }
    }
}
