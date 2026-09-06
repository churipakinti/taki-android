/*
 * TakiIconButton.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A tappable icon that always occupies at least a 48dp touch target regardless of the
 * visual glyph size, with no container (V2 section 6). The glyph recedes to gray by
 * default and only turns accent for an active semantic state ([selected]).
 *
 * [contentDescription] is required - an icon-only control must be announced.
 *
 * See docs/design/TAKI_DESIGN_SYSTEM_V2.md sections 6 and 19.
 */
@Composable
fun TakiIconButton(
    onClick: () -> Unit,
    painter: Painter,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = TakiTheme.dimensions.iconMd,
    enabled: Boolean = true,
    selected: Boolean = false,
    tint: Color = if (selected) TakiTheme.colors.accent else TakiTheme.colors.gray,
) {
    Box(
        modifier = modifier
            .size(TakiTheme.dimensions.touchTargetMin)
            .semantics { this.selected = selected }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}
