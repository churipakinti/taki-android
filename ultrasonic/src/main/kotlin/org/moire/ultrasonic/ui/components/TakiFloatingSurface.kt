/*
 * TakiFloatingSurface.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A piece of persistent floating chrome (mini-player, bottom navigation): a lightly
 * translucent surface tone plus an optional 1dp tonal top edge - no Material drop shadow,
 * no bright border, no glassmorphism (V2 sections 1.2 / 13 / 14). Translucency comes from a
 * baked colour ([org.moire.ultrasonic.ui.theme.TakiColors.surfaceFloating] etc.), not a
 * runtime backdrop blur, so it stays cheap and predictable.
 *
 * Not yet wired into Home - the mini-player and bottom nav are still View-based and use the
 * matching baked XML colours. This is the Compose form for when the mini-player migrates
 * (migration plan section 8, step 6).
 */
@Composable
fun TakiFloatingSurface(
    modifier: Modifier = Modifier,
    shape: Shape = TakiTheme.shapes.md,
    color: Color = TakiTheme.colors.surfaceFloating,
    topEdge: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val edgeColor = TakiTheme.colors.edgeHighlight
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .then(
                if (topEdge) {
                    Modifier.drawWithContent {
                        drawContent()
                        val h = 1.dp.toPx() // taki-raw-ok: 1px hairline edge, not a spacing token
                        drawLine(
                            color = edgeColor,
                            start = Offset(0f, h / 2f),
                            end = Offset(size.width, h / 2f),
                            strokeWidth = h,
                        )
                    }
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}
