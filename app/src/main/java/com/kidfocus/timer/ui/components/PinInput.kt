package com.kidfocus.timer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.ui.theme.KidFocusTheme

/**
 * PIN dot indicator that shows [filledCount] filled dots out of [totalDigits] total.
 *
 * This is a display-only component; digit input is handled by [PinKeyboard].
 *
 * @param filledCount Number of digits entered so far.
 * @param totalDigits Total PIN length (default 4).
 * @param errorState When true dots animate to a red error color.
 */
@Composable
fun PinDotRow(
    filledCount: Int,
    modifier: Modifier = Modifier,
    totalDigits: Int = 4,
    errorState: Boolean = false,
) {
    val primary = KidFocusTheme.colors.primary
    val dotColor = if (errorState) Color(0xFFEF4444) else primary

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(totalDigits) { index ->
            val filled = index < filledCount
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (filled) dotColor else Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (errorState) dotColor else dotColor.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                    )
            )
        }
    }
}

/**
 * Numeric keypad for PIN entry.
 *
 * @param onDigit Called with the tapped digit (0-9).
 * @param onDelete Called when the backspace key is tapped.
 */
@Composable
fun PinKeyboard(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = KidFocusTheme.colors.primary

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val rows = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        )
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { digit ->
                    PinKey(
                        label = digit.toString(),
                        backgroundColor = primary.copy(alpha = 0.12f),
                        contentColor = primary,
                        onClick = { onDigit(digit) },
                    )
                }
            }
        }
        // Bottom row: empty | 0 | backspace
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(modifier = Modifier.size(72.dp))
            PinKey(
                label = "0",
                backgroundColor = primary.copy(alpha = 0.12f),
                contentColor = primary,
                onClick = { onDigit(0) },
            )
            PinKey(
                label = "\u232B",
                backgroundColor = Color(0xFFEF4444).copy(alpha = 0.12f),
                contentColor = Color(0xFFEF4444),
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun PinKey(
    label: String,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(4.dp)
            .run {
                // Use clickable via semantics to remain accessible
                this
            }
            .let {
                it.then(
                    Modifier.padding(0.dp)
                )
            }
    ) {
        val clickableModifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)

        // Wrap in a clickable surface
        androidx.compose.material3.Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = backgroundColor,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    ),
                    color = contentColor,
                )
            }
        }
    }
}
