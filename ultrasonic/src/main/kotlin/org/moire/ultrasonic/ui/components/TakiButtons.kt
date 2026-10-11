/*
 * TakiButtons.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

@file:Suppress("MatchingDeclarationName") // the file holds the button primitives; the palette type is their only class

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.ui.theme.TakiColors
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The foreground/background pairs of Taki's text-labelled buttons (issue #26 phase 5C.1).
 *
 * Kept as plain colours so the contrast of every pairing is unit-testable
 * (`TakiButtonContrastTest`). The standing rule: a bright accent container always carries
 * `onAccent` content, never ivory/white.
 */

data class TakiButtonPalette(
    val container: Color,
    val content: Color,
    val disabledContainer: Color,
    val disabledContent: Color,
)

private const val DISABLED_CONTENT_ALPHA = 0.7f

/** Accent-filled primary action: `accent` + `onAccent`. */
fun takiPrimaryButtonPalette(c: TakiColors) = TakiButtonPalette(
    container = c.accent,
    content = c.onAccent,
    disabledContainer = c.selectedNeutral,
    disabledContent = c.gray.copy(alpha = DISABLED_CONTENT_ALPHA),
)

/** Destructive action: the error tone, never green. */
fun takiDestructiveButtonPalette(c: TakiColors) = TakiButtonPalette(
    container = c.error,
    content = c.black,
    disabledContainer = c.selectedNeutral,
    disabledContent = c.gray.copy(alpha = DISABLED_CONTENT_ALPHA),
)

/** Neutral secondary action: outlined, ivory label on the surface behind it. */
fun takiSecondaryButtonPalette(c: TakiColors) = TakiButtonPalette(
    container = Color.Transparent,
    content = c.ivory,
    disabledContainer = Color.Transparent,
    disabledContent = c.gray.copy(alpha = DISABLED_CONTENT_ALPHA),
)

@Composable
private fun TakiButtonPalette.colors() = ButtonDefaults.buttonColors(
    containerColor = container,
    contentColor = content,
    disabledContainerColor = disabledContainer,
    disabledContentColor = disabledContent,
)

/**
 * The label inside a Taki button. Taki's text roles hard-code their own colour, which would
 * silently override the button's `contentColor` (ivory text on an accent fill); the label colour
 * is therefore always taken from [LocalContentColor] explicitly.
 */
@Composable
private fun ButtonLabel(text: String) {
    Text(text = text, style = TakiTheme.type.titleSmall, color = LocalContentColor.current)
}

/** The screen's single accent-filled primary action (accent container, `onAccent` label). */
@Composable
fun TakiPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
        enabled = enabled,
        colors = takiPrimaryButtonPalette(TakiTheme.colors).colors(),
    ) { ButtonLabel(text) }
}

/** A destructive confirmation (delete): error-toned, never accent green. */
@Composable
fun TakiDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
        enabled = enabled,
        colors = takiDestructiveButtonPalette(TakiTheme.colors).colors(),
    ) { ButtonLabel(text) }
}

/** A quiet, neutral secondary action that sits beside a [TakiPrimaryButton]. */
@Composable
fun TakiSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val palette = takiSecondaryButtonPalette(TakiTheme.colors)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin),
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = palette.content,
            disabledContentColor = palette.disabledContent,
        ),
        border = BorderStroke(
            width = 1.dp, // taki-raw-ok: hairline outline stroke
            color = TakiTheme.colors.gray.copy(alpha = SECONDARY_BORDER_ALPHA),
        ),
    ) { ButtonLabel(text) }
}

private const val SECONDARY_BORDER_ALPHA = 0.4f
