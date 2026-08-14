package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.R
import com.kidfocus.timer.data.database.ChildProfileEntity
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.viewmodel.ChildProfileViewModel
import com.kidfocus.timer.ui.components.childProfileName

@Composable
fun ChildProfilePickerScreen(
    viewModel: ChildProfileViewModel,
    onBack: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.activeProfile.collectAsState()
    ProfileScreenLayout(stringResource(R.string.profile_choose_title), onBack) {
        Text(
            stringResource(R.string.profile_choose_description),
            color = KidFocusTheme.colors.onBackground.copy(alpha = 0.68f),
        )
        Spacer(Modifier.height(16.dp))
        profiles.forEach { profile ->
            ProfileSelectionRow(
                profile = profile,
                selected = profile.id == active.id,
                onSelect = { viewModel.select(profile.id, onBack) },
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun ChildProfileSettingsScreen(
    viewModel: ChildProfileViewModel,
    onBack: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsState()
    val active by viewModel.activeProfile.collectAsState()
    var editing by remember { mutableStateOf<ChildProfileEntity?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var archiveCandidate by remember { mutableStateOf<ChildProfileEntity?>(null) }
    var archiveBlocked by remember { mutableStateOf(false) }

    ProfileScreenLayout(stringResource(R.string.profile_manage_title), onBack) {
        Text(
            stringResource(R.string.profile_manage_description),
            color = KidFocusTheme.colors.onBackground.copy(alpha = 0.68f),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                editing = null
                showEditor = true
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.profile_add))
        }
        Spacer(Modifier.height(16.dp))
        profiles.forEach { profile ->
            ProfileManageRow(
                profile = profile,
                selected = profile.id == active.id,
                onSelect = { viewModel.select(profile.id) },
                onEdit = {
                    editing = profile
                    showEditor = true
                },
                onArchive = { archiveCandidate = profile },
            )
            Spacer(Modifier.height(10.dp))
        }
        if (archiveBlocked) {
            Text(
                stringResource(R.string.profile_keep_one),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showEditor) {
        ChildProfileEditorDialog(
            existing = editing,
            onDismiss = { showEditor = false },
            onSave = { name, emoji, ageBand ->
                viewModel.save(editing?.id, name, emoji, ageBand) {
                    showEditor = false
                }
            },
        )
    }

    archiveCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = { archiveCandidate = null },
            title = { Text(stringResource(R.string.profile_archive_title)) },
            text = { Text(stringResource(R.string.profile_archive_message, childProfileName(candidate))) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.archive(candidate) { archived ->
                        archiveBlocked = !archived
                        archiveCandidate = null
                    }
                }) { Text(stringResource(R.string.profile_archive)) }
            },
            dismissButton = {
                TextButton(onClick = { archiveCandidate = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ProfileSelectionRow(
    profile: ChildProfileEntity,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val displayName = childProfileName(profile)
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primary.copy(alpha = 0.14f) else colors.surface,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(profile.avatarEmoji, fontSize = 34.sp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(displayName, fontWeight = FontWeight.Bold, color = colors.onSurface)
                Text(profileAgeLabel(profile.ageBand), color = colors.onSurface.copy(alpha = 0.65f))
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Composable
private fun ProfileManageRow(
    profile: ChildProfileEntity,
    selected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val displayName = childProfileName(profile)
    Surface(shape = RoundedCornerShape(18.dp), color = colors.surface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            ProfileSelectionRow(profile, selected, onSelect)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.profile_edit_description, displayName),
                    )
                }
                IconButton(onClick = onArchive) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.profile_archive_description, displayName),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildProfileEditorDialog(
    existing: ChildProfileEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    val initialName = existing?.let { childProfileName(it) }.orEmpty()
    var name by remember(existing?.id) { mutableStateOf(initialName) }
    var emoji by remember(existing?.id) { mutableStateOf(existing?.avatarEmoji ?: "🐣") }
    var ageBand by remember(existing?.id) { mutableStateOf(existing?.ageBand ?: "4-5") }
    val emojis = listOf("🐣", "🐼", "🦊", "🐰", "🐯", "🐬")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) R.string.profile_add_title else R.string.profile_edit_title))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text(stringResource(R.string.profile_name)) },
                    singleLine = true,
                )
                Text(stringResource(R.string.profile_avatar), fontWeight = FontWeight.Bold)
                emojis.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = emoji == option,
                                onClick = { emoji = option },
                                label = { Text(option) },
                            )
                        }
                    }
                }
                Text(stringResource(R.string.profile_age_level), fontWeight = FontWeight.Bold)
                listOf("2-3", "4-5", "l1", "l2", "l3").chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            FilterChip(
                                selected = ageBand == option,
                                onClick = { ageBand = option },
                                label = { Text(profileAgeLabel(option)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, emoji, ageBand) }) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun profileAgeLabel(ageBand: String): String = when (ageBand) {
    "2-3" -> stringResource(R.string.profile_age_2_3)
    "4-5" -> stringResource(R.string.profile_age_4_5)
    "l1" -> stringResource(R.string.profile_grade_1)
    "l2" -> stringResource(R.string.profile_grade_2)
    else -> stringResource(R.string.profile_grade_3)
}

@Composable
private fun ProfileScreenLayout(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = KidFocusTheme.colors
    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onBackground,
                    modifier = Modifier.semantics { heading() },
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
