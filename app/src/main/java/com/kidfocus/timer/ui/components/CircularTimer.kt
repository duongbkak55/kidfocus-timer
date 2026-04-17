package com.kidfocus.timer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kidfocus.timer.ui.theme.KidFocusTheme
import com.kidfocus.timer.ui.theme.WarningAmber

/**
 * Full-screen circular countdown timer rendered on a [Canvas].
 *
 * The arc animates smoothly between progress values using [animateFloatAsState].
 * When [isWarning] is true the arc color transitions to [WarningAmber].
 *
 * @param progress Fraction from 0.0 (empty) to 1.0 (full).
 * @param timeText Formatted MM:SS string displayed in the center.
 * @param arcColor Base color of the progress arc when not in warning state.
 * @param isWarning True when fewer than 30 seconds remain — switches arc to warning color.
 * @param size Outer diameter of the circular widget.
 * @param strokeWidth Thickness of the arc stroke.
 */
@Composable
fun CircularTimer(
    progress: Float,
    timeText: String,
    arcColor: Color,
    isWarning: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 280.dp,
    strokeWidth: Dp = 16.dp,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 800),
        label = "timer_progress",
    )

    val animatedArcColor by animateColorAsState(
        targetValue = if (isWarning) WarningAmber else arcColor,
        animationSpec = tween(durationMillis = 500),
        label = "timer_arc_color",
    )

    val trackColor = animatedArcColor.copy(alpha = 0.18f)
    val onBackground = KidFocusTheme.colors.onBackground

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(this.size.width - strokePx, this.size.height - strokePx)
            val topLeft = Offset(inset, inset)

            // Background track arc
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )

            // Progress arc – sweeps clockwise from the 12-o'clock position
            drawArc(
                color = animatedArcColor,
                startAngle = -90f,
                sweepAngle = animatedProgress * 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }

        // Time text in the center
        Text(
            text = timeText,
            style = MaterialTheme.typography.displayMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = if (size >= 260.dp) 56.sp else 40.sp,
            ),
            color = if (isWarning) WarningAmber else onBackground,
        )
    }
}
