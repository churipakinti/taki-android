/*
 * LibraryBrowseRow.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

private const val CHEVRON_ROTATION_DEGREES = -90f

/**
 * A quiet 56dp "browse into a collection" row: transparent background, a receding leading
 * glyph, a single-line title, an optional count, and a trailing chevron. The whole row is
 * one tappable, merged-semantics target (V2 section 11.4). These rows are deliberately
 * understated so the personal destinations above them read as more important - do not turn
 * them into cards or pills.
 */
@Composable
fun LibraryBrowseRow(
    label: String,
    leadingPainter: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    showDivider: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TakiTheme.dimensions.rowSm)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = TakiTheme.spacing.screenHorizontal)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = leadingPainter,
                contentDescription = null,
                tint = TakiTheme.colors.gray,
                modifier = Modifier.size(TakiTheme.dimensions.iconMd),
            )
            Spacer(Modifier.width(TakiTheme.spacing.md))
            Text(
                text = label,
                style = TakiTheme.type.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = TakiTheme.type.caption,
                )
                Spacer(Modifier.width(TakiTheme.spacing.sm))
            }
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = TakiTheme.colors.gray,
                modifier = Modifier
                    .size(TakiTheme.dimensions.iconSm)
                    .rotate(CHEVRON_ROTATION_DEGREES),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                thickness = TakiTheme.dimensions.borderThin,
                color = TakiTheme.colors.divider,
                modifier = Modifier.padding(start = TakiTheme.spacing.screenHorizontal),
            )
        }
    }
}
