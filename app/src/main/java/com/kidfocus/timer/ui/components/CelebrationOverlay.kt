package com.kidfocus.timer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---- Particle model -------------------------------------------------------------------------

private enum class ParticleShape { RECT, CIRCLE, TRIANGLE }

private data class Particle(
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val color: Color,
    val shape: ParticleShape,
    val size: Float,
    val rotationSpeed: Float,
    val initialRotation: Float,
)

private val confettiColors = listOf(
    Color(0xFF2B7FFF),
    Color(0xFF22C55E),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF8B5CF6),
    Color(0xFFF97316),
    Color(0xFF06B6D4),
)

private fun generateParticles(count: Int, canvasWidth: Float, canvasHeight: Float): List<Particle> {
    val rng = Random.Default
    return List(count) {
        val angle = rng.nextFloat() * 360f
        val speed = rng.nextFloat() * 600f + 200f
        val vx = cos(Math.toRadians(angle.toDouble())).toFloat() * speed * 0.5f
        val vy = -(rng.nextFloat() * speed + 100f) // upward initial velocity
        Particle(
            startX = rng.nextFloat() * canvasWidth,
            startY = canvasHeight * 0.3f,
            velocityX = vx,
            velocityY = vy,
            color = confettiColors[rng.nextInt(confettiColors.size)],
            shape = ParticleShape.entries[rng.nextInt(ParticleShape.entries.size)],
            size = rng.nextFloat() * 16f + 8f,
            rotationSpeed = (rng.nextFloat() - 0.5f) * 720f,
            initialRotation = rng.nextFloat() * 360f,
        )
    }
}

// ---- Composable -----------------------------------------------------------------------------

/**
 * Full-screen confetti overlay animated using [Canvas].
 *
 * Particles are generated once per composition and follow simple gravity + rotation
 * physics over a [durationMs] period. The overlay is transparent and non-interactive.
 *
 * @param particleCount Number of confetti particles to emit.
 * @param durationMs Total animation duration in milliseconds.
 * @param onFinished Called when the animation completes.
 */
@Composable
fun CelebrationOverlay(
    modifier: Modifier = Modifier,
    particleCount: Int = 120,
    durationMs: Int = 3000,
    onFinished: () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
        )
        onFinished()
    }

    // Particles are stable per composition – re-seeded only when the overlay recomposes
    val particles = remember {
        // Placeholder dimensions; will be populated on first draw
        mutableListOf<Particle>()
    }
    var particlesInitialized = remember { false }

    Canvas(modifier = modifier.fillMaxSize()) {
        if (!particlesInitialized) {
            particles.addAll(generateParticles(particleCount, size.width, size.height))
            particlesInitialized = true
        }

        val t = progress.value
        val gravity = 980f // pixels per second^2 (scaled to animation time)
        val timeSeconds = t * (durationMs / 1000f)

        particles.forEach { p ->
            val x = p.startX + p.velocityX * timeSeconds
            val y = p.startY + p.velocityY * timeSeconds + 0.5f * gravity * timeSeconds * timeSeconds
            val rotation = p.initialRotation + p.rotationSpeed * timeSeconds
            val alpha = (1f - t * 0.8f).coerceIn(0f, 1f)

            // Cull particles that have fallen below the canvas
            if (y > size.height + p.size) return@forEach

            drawParticle(
                particle = p,
                x = x,
                y = y,
                rotation = rotation,
                alpha = alpha,
            )
        }
    }
}

private fun DrawScope.drawParticle(
    particle: Particle,
    x: Float,
    y: Float,
    rotation: Float,
    alpha: Float,
) {
    val color = particle.color.copy(alpha = alpha)
    val s = particle.size

    withTransform({
        translate(x, y)
        rotate(rotation, pivot = Offset.Zero)
    }) {
        when (particle.shape) {
            ParticleShape.RECT -> drawRect(
                color = color,
                topLeft = Offset(-s / 2f, -s / 4f),
                size = Size(s, s / 2f),
            )
            ParticleShape.CIRCLE -> drawCircle(
                color = color,
                radius = s / 2f,
                center = Offset.Zero,
            )
            ParticleShape.TRIANGLE -> {
                val path = Path().apply {
                    moveTo(0f, -s / 2f)
                    lineTo(s / 2f, s / 2f)
                    lineTo(-s / 2f, s / 2f)
                    close()
                }
                drawPath(path, color = color)
            }
        }
    }
}
