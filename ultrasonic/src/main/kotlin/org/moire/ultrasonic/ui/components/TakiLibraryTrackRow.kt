/*
 * TakiLibraryTrackRow.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A compact, playback-first song row (issue #10 phase 4F1) - the Compose port of the legacy
 * `LibraryTrackBinder` used by the Media Library "Songs" screen and Liked Songs: artwork
 * thumbnail, a one-line title, a one-line "artist · album" subtitle, an optional like toggle,
 * and a menu affordance that opens the same context menu a long press does. Unlike
 * [TakiTrackRow] (Album Detail's row - no per-track artwork, a track-number leading column, a
 * current-track marker), this row has none of those: the legacy binder it ports doesn't either.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TakiLibraryTrackRow(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    onClick: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    showHeart: Boolean = false,
    liked: Boolean = false,
    onHeartClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onOpenMenu)
            .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TakiArtwork(
            model = artworkModel,
            contentDescription = null,
            size = TakiTheme.dimensions.artworkThumb,
            shape = TakiTheme.shapes.sm,
            placeholder = painterResource(R.drawable.unknown_album),
        )
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TakiTheme.type.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (showHeart) {
            TakiIconButton(
                onClick = { onHeartClick?.invoke() },
                painter = painterResource(
                    if (liked) R.drawable.rating_heart_full else R.drawable.rating_heart_hollow,
                ),
                contentDescription = stringResource(R.string.download_menu_favorite),
                tint = TakiTheme.colors.accentSecondary,
            )
        }
        TakiIconButton(
            onClick = onOpenMenu,
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.search_more),
            tint = TakiTheme.colors.gray,
        )
    }
}
