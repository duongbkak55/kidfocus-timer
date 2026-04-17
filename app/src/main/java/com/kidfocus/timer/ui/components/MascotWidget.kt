package com.kidfocus.timer.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kidfocus.timer.domain.model.TimerPhase
import com.kidfocus.timer.ui.theme.BreakGreen
import com.kidfocus.timer.ui.theme.FocusBlue
import com.kidfocus.timer.ui.theme.WarningAmber

/**
 * Animated owl mascot drawn with [Canvas].
 *
 * The mascot changes expression based on the current [TimerPhase] and gently
 * bobs up and down while the timer is running.
 *
 * @param phase Current timer phase driving the mascot's expression.
 * @param isRunning When true the mascot plays a continuous bounce animation.
 * @param size Bounding box size of the mascot widget.
 */
@Composable
fun MascotWidget(
    phase: TimerPhase,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 120.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mascot")
    val bounceOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isRunning) -8f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mascot_bounce",
    )

    val blinkProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "mascot_blink",
    )

    // Eye openness: fully open except for a brief blink window
    val eyeOpenness = if (blinkProgress > 0.92f) 0.1f else 1f

    val bodyColor = when (phase) {
        is TimerPhase.Focus -> FocusBlue.copy(alpha = 0.9f)
        is TimerPhase.Break -> BreakGreen.copy(alpha = 0.9f)
        is TimerPhase.Idle -> Color(0xFF94A3B8)
    }
    val accentColor = when (phase) {
        is TimerPhase.Focus -> Color(0xFFBFDBFE)
        is TimerPhase.Break -> Color(0xFFBBF7D0)
        is TimerPhase.Idle -> Color(0xFFCBD5E1)
    }

    Canvas(modifier = modifier.size(size)) {
        translate(top = bounceOffset) {
            drawOwl(
                bodyColor = bodyColor,
                accentColor = accentColor,
                eyeOpenness = eyeOpenness,
                isWarning = phase is TimerPhase.Focus,
            )
        }
    }
}

private fun DrawScope.drawOwl(
    bodyColor: Color,
    accentColor: Color,
    eyeOpenness: Float,
    isWarning: Boolean,
) {
    val w = size.width
    val h = size.height

    // Body
    drawOval(
        color = bodyColor,
        topLeft = Offset(w * 0.15f, h * 0.2f),
        size = Size(w * 0.7f, h * 0.75f),
    )

    // Head (circle on top)
    drawCircle(
        color = bodyColor,
        radius = w * 0.28f,
        center = Offset(w * 0.5f, h * 0.3f),
    )

    // Ear tufts
    drawCircle(color = bodyColor, radius = w * 0.1f, center = Offset(w * 0.33f, h * 0.08f))
    drawCircle(color = bodyColor, radius = w * 0.1f, center = Offset(w * 0.67f, h * 0.08f))

    // Belly / chest patch
    drawOval(
        color = accentColor,
        topLeft = Offset(w * 0.3f, h * 0.48f),
        size = Size(w * 0.4f, h * 0.4f),
    )

    // Eye whites
    val eyeHalfH = w * 0.1f * eyeOpenness
    drawOval(
        color = Color.White,
        topLeft = Offset(w * 0.28f, h * 0.21f - eyeHalfH),
        size = Size(w * 0.17f, eyeHalfH * 2f),
    )
    drawOval(
        color = Color.White,
        topLeft = Offset(w * 0.55f, h * 0.21f - eyeHalfH),
        size = Size(w * 0.17f, eyeHalfH * 2f),
    )

    // Pupils
    val pupilR = w * 0.055f * eyeOpenness
    drawCircle(color = Color(0xFF1E293B), radius = pupilR, center = Offset(w * 0.365f, h * 0.21f))
    drawCircle(color = Color(0xFF1E293B), radius = pupilR, center = Offset(w * 0.635f, h * 0.21f))

    // Beak
    val beakColor = WarningAmber
    val beakPath = androidx.compose.ui.graphics.Path().apply {
        moveTo(w * 0.5f, h * 0.33f)
        lineTo(w * 0.43f, h * 0.41f)
        lineTo(w * 0.57f, h * 0.41f)
        close()
    }
    drawPath(beakPath, color = beakColor)
}
