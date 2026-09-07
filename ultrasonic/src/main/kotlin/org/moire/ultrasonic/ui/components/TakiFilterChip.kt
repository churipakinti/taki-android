/*
 * TakiFilterChip.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A pill for a filter or a quiet navigation shortcut (V2 sections 10.1 / 15). The visible
 * pill stays compact (a `TitleSmall` line with 12/8dp padding, fully rounded via
 * `shapes.lg`); [minimumInteractiveComponentSize] grows the touch envelope to >=48dp
 * without inflating the visual.
 *
 * Unselected recedes to a neutral surface with a hairline outline. Selected is the preferred
 * neutral mode (`selectedNeutral` + ivory); the bright-accent selected state is reserved for
 * a screen with exactly one accent control, so it is opt-in via [accentWhenSelected].
 */
@Composable
fun TakiFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentWhenSelected: Boolean = false,
) {
    val colors = TakiTheme.colors
    val container = when {
        selected && accentWhenSelected -> colors.accent
        selected -> colors.selectedNeutral
        else -> colors.surfaceLow
    }
    val content = if (selected && accentWhenSelected) colors.onAccent else colors.ivory

    Surface(
        onClick = onClick,
        selected = selected,
        shape = TakiTheme.shapes.lg,
        color = container,
        contentColor = content,
        border = if (selected) null else BorderStroke(TakiTheme.dimensions.borderThin, colors.outline),
        modifier = modifier.minimumInteractiveComponentSize(),
    ) {
        Text(
            text = label,
            style = TakiTheme.type.titleSmall,
            maxLines = 1,
            modifier = Modifier.padding(
                horizontal = TakiTheme.spacing.md,
                vertical = TakiTheme.spacing.sm,
            ),
        )
    }
}
