/*
 * TakiTheme.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

/**
 * The single Compose theme wrapper for Taki.
 *
 * It mirrors - never reinterprets - the existing XML design system:
 *  - the Material 3 [ColorScheme] is the exact attribute mapping from
 *    `res/values/themes.xml` (`UltrasonicTheme.Dark`);
 *  - [TakiColors] / [TakiSpacing] / [TakiShapes] / [TakiTypography] / [TakiDimensions] /
 *    [TakiMotion] are provided as ambients and read back through the [TakiTheme] accessor
 *    object (`TakiTheme.colors`, `TakiTheme.spacing`, ...).
 *
 * Production Compose UI must go through this wrapper and must not use
 * `androidx.compose.material3.MaterialTheme` directly (enforced by `ArchitectureGuardTest`).
 *
 * Taki ships a single dark identity - there is no light scheme and no dynamic colour.
 *
 * See docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md section 5 and
 * docs/design/TAKI_DESIGN_SYSTEM_V2.md section 16.
 */
@Composable
fun TakiTheme(
    colors: TakiColors = TakiColors(),
    content: @Composable () -> Unit,
) {
    val typography = TakiTypography()
    CompositionLocalProvider(
        LocalTakiColors provides colors,
        LocalTakiSpacing provides TakiSpacing(),
        LocalTakiShapes provides TakiShapes(),
        LocalTakiDimensions provides TakiDimensions(),
        LocalTakiTypography provides typography,
        LocalTakiMotion provides TakiMotion(),
    ) {
        MaterialTheme(
            colorScheme = takiColorScheme(colors),
            typography = takiMaterialTypography(typography),
            shapes = TakiMaterialShapes,
            content = content,
        )
    }
}

/**
 * Accessor for the Taki ambients. Mirrors the shape of `MaterialTheme` (a function and an
 * object sharing a name).
 */
object TakiTheme {
    val colors: TakiColors
        @Composable @ReadOnlyComposable get() = LocalTakiColors.current

    val spacing: TakiSpacing
        @Composable @ReadOnlyComposable get() = LocalTakiSpacing.current

    val shapes: TakiShapes
        @Composable @ReadOnlyComposable get() = LocalTakiShapes.current

    val dimensions: TakiDimensions
        @Composable @ReadOnlyComposable get() = LocalTakiDimensions.current

    val type: TakiTypography
        @Composable @ReadOnlyComposable get() = LocalTakiTypography.current

    val motion: TakiMotion
        @Composable @ReadOnlyComposable get() = LocalTakiMotion.current
}

/**
 * The Material 3 dark [ColorScheme], transcribed 1:1 from `UltrasonicTheme.Dark` in
 * `res/values/themes.xml`. `surfaceTint` is pinned to the canvas colour so elevated
 * Material surfaces are not tinted green (depth stays tonal - V2 section 8).
 */
private fun takiColorScheme(c: TakiColors): ColorScheme = darkColorScheme(
    primary = c.accent,
    onPrimary = c.onAccent,
    primaryContainer = c.selectedContainer,
    onPrimaryContainer = c.ivory,
    secondary = c.accentSecondary,
    onSecondary = c.onAccent,
    secondaryContainer = c.selectedNeutral,
    onSecondaryContainer = c.ivory,
    tertiary = c.accentSecondary,
    onTertiary = c.onAccent,
    background = c.black,
    onBackground = c.ivory,
    surface = c.black,
    onSurface = c.ivory,
    surfaceVariant = c.surface,
    onSurfaceVariant = c.gray,
    surfaceTint = c.black,
    surfaceContainerLowest = c.black,
    surfaceContainerLow = c.surfaceLow,
    surfaceContainer = c.surfaceLow,
    surfaceContainerHigh = c.surface,
    surfaceContainerHighest = c.surfaceHigh,
    outline = c.outline,
    outlineVariant = c.divider,
    error = c.error,
    onErrorContainer = c.onErrorContainer,
)

/**
 * Partial Material 3 [Typography] map, so the handful of stock M3 components Taki uses pick
 * up the Taki roles. Taki components read [TakiTheme.type] directly instead.
 */
private fun takiMaterialTypography(t: TakiTypography): Typography = Typography(
    headlineSmall = t.hero,
    titleMedium = t.title,
    titleSmall = t.titleSmall,
    bodyMedium = t.body,
    bodySmall = t.caption,
    labelLarge = t.title,
    labelMedium = t.sectionHeader,
)

/**
 * Material 3 [Shapes] from the `radius_*` scale (`dimens.xml`).
 */
private val TakiMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(20.dp),
)
