package com.kidfocus.timer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.ui.theme.KidFocusTheme

/**
 * A single setting row displaying a [title] and an optional [subtitle],
 * with a [Switch] on the trailing edge.
 *
 * @param title Primary label for the setting.
 * @param subtitle Optional secondary description.
 * @param checked Current switch state.
 * @param onCheckedChange Callback when the user toggles the switch.
 */
@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val colors = KidFocusTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary,
                    uncheckedTrackColor = colors.onSurface.copy(alpha = 0.2f),
                ),
            )
        }
    }
}

/**
 * A setting row that shows a [Slider] for selecting a numeric value.
 *
 * @param title Setting label.
 * @param value Current value.
 * @param valueRange Allowed range for the slider.
 * @param steps Number of discrete steps (0 = continuous).
 * @param onValueChange Callback on each drag update.
 * @param valueLabel Formatted string of the current value, shown on the trailing edge.
 */
@Composable
fun SettingSliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    valueLabel: String = value.toInt().toString(),
) {
    val colors = KidFocusTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.primary,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = colors.primary,
                    activeTrackColor = colors.primary,
                    inactiveTrackColor = colors.primary.copy(alpha = 0.2f),
                ),
            )
        }
    }
}

/**
 * A simple informational row with a leading [icon] text (emoji works well) and a [title].
 *
 * @param icon Emoji or short string displayed as a leading icon.
 * @param title Primary label.
 * @param subtitle Optional detail text.
 * @param trailingContent Optional composable slot rendered at the trailing edge.
 */
@Composable
fun SettingRow(
    icon: String,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val colors = KidFocusTheme.colors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurface,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
            if (trailingContent != null) {
                trailingContent()
            }
        }
    }
}
