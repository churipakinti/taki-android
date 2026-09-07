/*
 * LibraryPrimaryCard.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A compact personal-library destination card for Library's "Your music" grid (V2 section
 * 11.3): ~184x80dp, a quiet `surfaceLow` tone lifted just off the page, a receding 24dp
 * semantic glyph, an ivory title and optional gray [secondary] metadata. No elevation, no
 * shadow, no border, no artwork - it establishes hierarchy over the browse rows below
 * without competing with real album artwork elsewhere.
 *
 * The whole card is one tappable, merged-semantics target. Width is the caller's job (use
 * `Modifier.weight(1f)` in a two-column row); height is fixed to `library_card_height`.
 *
 * [iconTint] defaults to the normal receding gray; a caller may pass a restrained semantic
 * accent (e.g. `TakiTheme.colors.liked`) for the glyph only - the surface never changes.
 */
@Composable
fun LibraryPrimaryCard(
    label: String,
    leadingPainter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    iconTint: Color = TakiTheme.colors.gray,
) {
    Column(
        modifier = modifier
            .height(TakiTheme.dimensions.libraryCardHeight)
            .clip(TakiTheme.shapes.md)
            .background(TakiTheme.colors.surfaceLow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(TakiTheme.spacing.md)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            painter = leadingPainter,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = TakiTheme.type.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondary != null) {
                Text(
                    text = secondary,
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
