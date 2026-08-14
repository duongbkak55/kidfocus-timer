package com.kidfocus.timer.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.R

enum class RepeatPreset {
    EVERY_DAY,
    WEEKDAYS,
    WEEKEND,
}

/** Shared presets used by both study schedules and daily routines. */
@Composable
fun RepeatPresetSelector(
    selected: RepeatPreset?,
    onSelect: (RepeatPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        RepeatPreset.EVERY_DAY to R.string.repeat_every_day,
        RepeatPreset.WEEKDAYS to R.string.repeat_weekdays,
        RepeatPreset.WEEKEND to R.string.repeat_weekend,
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (preset, labelRes) ->
            FilterChip(
                selected = selected == preset,
                onClick = { onSelect(preset) },
                label = { Text(stringResource(labelRes)) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
        }
    }
}
