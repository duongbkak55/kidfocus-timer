package com.kidfocus.timer.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kidfocus.timer.domain.model.AppTheme

/**
 * Extended color tokens specific to KidFocus Timer.
 *
 * These supplement [MaterialTheme.colorScheme] with semantic colors that are
 * meaningful in the timer context (focus, break, warning).
 */
@Immutable
data class KidFocusColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val focusArc: Color,
    val breakArc: Color,
    val warningArc: Color,
)

// ---- Theme palettes -------------------------------------------------------------------------

private val OceanColors = KidFocusColors(
    background = OceanBackground,
    surface = OceanSurface,
    primary = OceanPrimary,
    onPrimary = OceanOnPrimary,
    secondary = OceanSecondary,
    onSecondary = OceanOnSecondary,
    onBackground = OceanOnBackground,
    onSurface = OceanOnSurface,
    focusArc = FocusBlue,
    breakArc = BreakGreen,
    warningArc = WarningAmber,
)

private val ForestColors = KidFocusColors(
    background = ForestBackground,
    surface = ForestSurface,
    primary = ForestPrimary,
    onPrimary = ForestOnPrimary,
    secondary = ForestSecondary,
    onSecondary = ForestOnSecondary,
    onBackground = ForestOnBackground,
    onSurface = ForestOnSurface,
    focusArc = ForestPrimary,
    breakArc = BreakGreen,
    warningArc = WarningAmber,
)

private val SunsetColors = KidFocusColors(
    background = SunsetBackground,
    surface = SunsetSurface,
    primary = SunsetPrimary,
    onPrimary = SunsetOnPrimary,
    secondary = SunsetSecondary,
    onSecondary = SunsetOnSecondary,
    onBackground = SunsetOnBackground,
    onSurface = SunsetOnSurface,
    focusArc = SunsetPrimary,
    breakArc = BreakGreen,
    warningArc = WarningAmber,
)

// ---- CompositionLocal -----------------------------------------------------------------------

/**
 * Provides [KidFocusColors] to the composition tree.
 * Access via [LocalKidFocusColors].current inside any composable.
 */
val LocalKidFocusColors = staticCompositionLocalOf { OceanColors }

// ---- Material3 color scheme builders -------------------------------------------------------

private fun KidFocusColors.toMaterial3Scheme(): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    secondary = secondary,
    onSecondary = onSecondary,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    error = ErrorRed,
    onError = White,
)

// ---- Theme composable -----------------------------------------------------------------------

/**
 * Root composable that applies a [KidFocusColors] palette driven by [appTheme].
 *
 * Usage:
 * ```kotlin
 * KidFocusTheme(appTheme = settings.appTheme) {
 *     AppNavigation(...)
 * }
 * ```
 */
@Composable
fun KidFocusTheme(
    appTheme: AppTheme = AppTheme.OCEAN,
    content: @Composable () -> Unit,
) {
    val kidFocusColors = when (appTheme) {
        AppTheme.OCEAN -> OceanColors
        AppTheme.FOREST -> ForestColors
        AppTheme.SUNSET -> SunsetColors
    }

    CompositionLocalProvider(LocalKidFocusColors provides kidFocusColors) {
        MaterialTheme(
            colorScheme = kidFocusColors.toMaterial3Scheme(),
            typography = KidFocusTypography,
            content = content,
        )
    }
}

/** Convenience accessor for the current [KidFocusColors] from within a composable. */
object KidFocusTheme {
    val colors: KidFocusColors
        @Composable get() = LocalKidFocusColors.current
}
