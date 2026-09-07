/*
 * AlbumShelfItem.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * One artwork-first item in a horizontal Home shelf (V2 sections 7 / 10.1): a square cover,
 * an 8dp gap, a one-line title and a one-line supporting caption. No card, no shadow - the
 * artwork carries the colour.
 *
 * [artworkSize] selects the shelf weight: `artworkShelfCompact` (104dp) for "Recently
 * played", `artworkCard` (140dp) for album-oriented shelves.
 */
@Composable
fun AlbumShelfItem(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    artworkSize: Dp = TakiTheme.dimensions.artworkCard,
) {
    Column(
        modifier = modifier
            .width(artworkSize)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        TakiArtwork(
            model = artworkModel,
            contentDescription = null,
            size = artworkSize,
            shape = TakiTheme.shapes.sm,
        )
        Spacer(Modifier.height(TakiTheme.spacing.sm))
        Text(
            text = title,
            style = TakiTheme.type.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = TakiTheme.type.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
