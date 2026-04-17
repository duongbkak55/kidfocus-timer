package com.kidfocus.timer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.domain.model.AppTheme
import com.kidfocus.timer.ui.theme.ForestBackground
import com.kidfocus.timer.ui.theme.ForestPrimary
import com.kidfocus.timer.ui.theme.ForestSecondary
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.theme.OceanBackground
import com.kidfocus.timer.ui.theme.OceanPrimary
import com.kidfocus.timer.ui.theme.OceanSecondary
import com.kidfocus.timer.ui.theme.SunsetBackground
import com.kidfocus.timer.ui.theme.SunsetPrimary
import com.kidfocus.timer.ui.theme.SunsetSecondary
import com.kidfocus.timer.ui.viewmodel.SettingsViewModel

private data class ThemePreview(
    val theme: AppTheme,
    val background: Color,
    val primary: Color,
    val secondary: Color,
)

private val themePreviewList = listOf(
    ThemePreview(AppTheme.OCEAN, OceanBackground, OceanPrimary, OceanSecondary),
    ThemePreview(AppTheme.FOREST, ForestBackground, ForestPrimary, ForestSecondary),
    ThemePreview(AppTheme.SUNSET, SunsetBackground, SunsetPrimary, SunsetSecondary),
)

/**
 * Theme selection screen showing a card preview for each [AppTheme].
 */
@Composable
fun ThemeScreen(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val colors = KidFocusTheme.colors
    val settings by settingsViewModel.settings.collectAsState()
    val currentTheme = settings?.appTheme ?: AppTheme.OCEAN

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = colors.onBackground,
                    )
                }
                Text(
                    text = "Chọn chủ đề",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onBackground,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Chọn màu sắc bạn thích nhất",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onBackground.copy(alpha = 0.6f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                themePreviewList.forEach { preview ->
                    ThemeCard(
                        preview = preview,
                        isSelected = currentTheme == preview.theme,
                        onSelect = { settingsViewModel.setTheme(preview.theme) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    preview: ThemePreview,
    isSelected: Boolean,
    onSelect: () -> Unit,
) {
    val borderColor = if (isSelected) preview.primary else Color.Transparent

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = preview.background,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onSelect),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Color swatches
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(preview.primary, preview.secondary, preview.background.copy(alpha = 1f)).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                        )
                    }
                }

                Column {
                    Text(
                        text = preview.theme.emoji + " " + preview.theme.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = preview.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = preview.theme.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodySmall,
                        color = preview.primary.copy(alpha = 0.6f),
                    )
                }
            }

            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(preview.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Đã chọn",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
