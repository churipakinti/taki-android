/*
 * TakiTrackRow.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A single track row for the detail screens (Album Detail now; Collection Detail next). Quiet
 * and compact per V2 §1/§5: transparent background, no card, no divider, ivory title over a
 * gray metadata line, a narrow gray number column, a trailing gray duration. The title is
 * allowed a second line for long classical work names before it ellipsizes, and the row grows
 * with it rather than clipping (V2 "reflow rather than overlap").
 *
 * [isCurrent] marks the track that playback is on: the number column becomes a small
 * "now playing" glyph and the title turns accent - state is not communicated by colour alone
 * (V2 §19), the glyph carries it.
 *
 * [onLongClick] opens the caller's context menu; `combinedClickable` guarantees a long press
 * does not also fire [onClick]. Touch target stays >= [TakiTheme.dimensions.trackRowMinHeight].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TakiTrackRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    number: String? = null,
    artist: String? = null,
    duration: String? = null,
    isCurrent: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .defaultMinSize(minHeight = TakiTheme.dimensions.trackRowMinHeight)
            .padding(
                horizontal = TakiTheme.spacing.screenHorizontal,
                vertical = TakiTheme.spacing.sm,
            )
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrackLeadingColumn(number = number, isCurrent = isCurrent)
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = TakiTheme.type.body,
                color = if (isCurrent) TakiTheme.colors.accent else TakiTheme.colors.ivory,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!artist.isNullOrEmpty()) {
                Text(
                    text = artist,
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!duration.isNullOrEmpty()) {
            Spacer(Modifier.width(TakiTheme.spacing.sm))
            Text(text = duration, style = TakiTheme.type.caption, maxLines = 1)
        }
        if (trailing != null) {
            Spacer(Modifier.width(TakiTheme.spacing.xs))
            trailing()
        }
    }
}

/** The narrow leading column: a track number, or a small "now playing" glyph for [isCurrent]. */
@Composable
private fun TrackLeadingColumn(number: String?, isCurrent: Boolean) {
    Box(
        modifier = Modifier.width(TakiTheme.dimensions.trackNumberColumn),
        contentAlignment = Alignment.Center,
    ) {
        if (isCurrent) {
            Icon(
                painter = painterResource(R.drawable.ic_queue_playing),
                contentDescription = null,
                tint = TakiTheme.colors.accent,
                modifier = Modifier.width(TakiTheme.dimensions.iconSm),
            )
        } else if (number != null) {
            Text(
                text = number,
                style = TakiTheme.type.caption,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A quiet "Disc N" marker between two discs' tracks on a multi-disc album, with the same
 * per-disc Play / Download affordances the legacy `DiscHeaderBinder` carried. Never shown for
 * single-disc albums.
 */
@Composable
fun TakiDiscHeader(
    label: String,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    playContentDescription: String,
    downloadContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = TakiTheme.spacing.screenHorizontal,
                end = TakiTheme.spacing.screenHorizontal,
                top = TakiTheme.spacing.lg,
                bottom = TakiTheme.spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = TakiTheme.type.sectionHeader,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TakiIconButton(
                onClick = onPlay,
                painter = painterResource(R.drawable.media_start),
                contentDescription = playContentDescription,
                iconSize = TakiTheme.dimensions.iconSm,
            )
            TakiIconButton(
                onClick = onDownload,
                painter = painterResource(R.drawable.ic_menu_download),
                contentDescription = downloadContentDescription,
                iconSize = TakiTheme.dimensions.iconSm,
            )
        }
    }
}
