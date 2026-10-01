/*
 * ServerSwatchColors.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.moire.ultrasonic.ui.theme.TakiTheme

private const val LUMINANCE_LIMIT = 0.5

/**
 * The background/foreground pair for a server color swatch: a configured server [color], or the
 * app's own accent as a neutral fallback; the foreground is luminance-picked for contrast. Shared
 * by [ServerSelectorScreen]'s row swatch and [EditServerScreen]'s color picker trigger (issue #10
 * phase 5A3) - reimplemented with Compose tokens instead of the legacy `ServerColor`'s
 * `MaterialColors.getColor`/`harmonizeWithPrimary` calls, which resolve an XML theme attribute
 * (`?attr/colorPrimary`) only present on a real themed Activity, not a plain Compose test host
 * (see the phase 5A2 report for the crash this caused there).
 */
@Composable
fun serverSwatchColors(color: Int?): Pair<Color, Color> {
    val background = color?.let { Color(it) } ?: TakiTheme.colors.accent
    val foreground = if (ColorUtils.calculateLuminance(background.toArgb()) < LUMINANCE_LIMIT) {
        TakiTheme.colors.ivory
    } else {
        TakiTheme.colors.black
    }
    return background to foreground
}
