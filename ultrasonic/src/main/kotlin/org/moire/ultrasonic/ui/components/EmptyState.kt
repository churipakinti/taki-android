/*
 * EmptyState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.ui.theme.TakiTheme

private val ContentMaxWidth = 320.dp // taki-raw-ok: readable line-length cap, not a spacing token

/**
 * Centred empty state (V2 section 18): a 32dp icon, a title, concise copy and at most one
 * action. Quiet - the icon and text recede, no accent surface.
 */
@Composable
fun EmptyState(
    icon: Painter,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(TakiTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            tint = TakiTheme.colors.gray,
            modifier = Modifier.size(TakiTheme.dimensions.iconLg),
        )
        Spacer(Modifier.height(TakiTheme.spacing.md))
        Text(
            text = title,
            style = TakiTheme.type.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = ContentMaxWidth),
        )
        if (message != null) {
            Spacer(Modifier.height(TakiTheme.spacing.xs))
            Text(
                text = message,
                style = TakiTheme.type.caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = ContentMaxWidth),
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(TakiTheme.spacing.md))
            TextButton(onClick = onAction) {
                Text(text = actionLabel, style = TakiTheme.type.titleSmall)
            }
        }
    }
}
