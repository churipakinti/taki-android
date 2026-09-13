/*
 * TakiEntryRow.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A generic list-mode row for a browse list (issue #10 phase 4E1): a compact artwork thumbnail,
 * a one-line title and an optional one-line subtitle. Not tied to any one entity - built for
 * Artist List (title only) and designed to be reused as-is by Album List (title + subtitle) in
 * a later phase.
 *
 * [onLongClick] opens the caller's context menu; `combinedClickable` guarantees a long press
 * cannot also fire [onClick], matching [TakiTrackRow]'s row-tap contract.
 */
@Composable
fun TakiEntryRow(
    title: String,
    artworkModel: Any?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    placeholder: Painter = painterResource(R.drawable.unknown_album),
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TakiArtwork(
            model = artworkModel,
            contentDescription = null,
            size = TakiTheme.dimensions.artworkThumb,
            shape = TakiTheme.shapes.sm,
            placeholder = placeholder,
        )
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TakiTheme.type.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
