/*
 * TakiSectionHeader.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A shelf / group header: a single-line `Taki.SectionHeader` title with an optional
 * trailing text action ("See all") that keeps a 48dp touch target. Height is driven by the
 * type, not fixed (V2 section 10).
 *
 * See docs/design/TAKI_DESIGN_SYSTEM_V2.md sections 10 and 11.
 */
@Composable
fun TakiSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = TakiTheme.type.sectionHeader,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .semantics { heading() },
        )
        if (actionLabel != null && onActionClick != null) {
            Text(
                text = actionLabel,
                style = TakiTheme.type.caption,
                color = TakiTheme.colors.accent,
                modifier = Modifier
                    .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .clickable(role = Role.Button, onClick = onActionClick)
                    .padding(start = TakiTheme.spacing.md),
            )
        }
    }
}
