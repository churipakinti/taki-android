/*
 * DetailPrimaryPlayButton.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The one obviously-primary action on a detail screen (V2 §2.3 / §6): an ivory circle
 * (`detail_primary_action` 64dp) with a dark `media_start` glyph - a calm high-contrast
 * affordance, never a promotional green fill. Shared by Album Detail and Artist Detail; the
 * `contentDescription` names what it plays.
 */
@Composable
fun DetailPrimaryPlayButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(TakiTheme.dimensions.detailPrimaryAction)
            .background(TakiTheme.colors.ivory, CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.media_start),
            contentDescription = contentDescription,
            tint = TakiTheme.colors.black,
            modifier = Modifier.size(TakiTheme.dimensions.iconLg),
        )
    }
}
