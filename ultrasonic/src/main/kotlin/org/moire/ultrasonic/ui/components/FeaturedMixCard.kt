/*
 * FeaturedMixCard.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

// V2 section 10.1: "primary play target 48dp, visual circle 40-44dp". The 48dp target comes
// from minimumInteractiveComponentSize(); this is only the visible disc.
private val PlayDiscSize = 44.dp // taki-raw-ok

/**
 * The one featured element near the top of Home (V2 section 10.1): a 380 x 156dp card,
 * 20dp radius, 16dp padding, a left text column and right-aligned 124dp artwork. It carries
 * the screen's single primary play action - an ivory circle with a dark glyph, because green
 * is a signal, not a fill (V2 section 2.3). [onSecondary] is a quiet, receding extra action
 * (Home uses it to regenerate the mix).
 *
 * Depth (V2 section 7.3): the same [artworkModel], decoded tiny and blurred, is washed behind
 * the content under a dark horizontal scrim by [TakiAtmosphericSurface] - darker on the text
 * side, a little colour kept on the artwork side. The foreground artwork stays sharp and the
 * play button stays ivory.
 */
@Composable
fun FeaturedMixCard(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    onPlay: () -> Unit,
    playContentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSecondary: (() -> Unit)? = null,
    secondaryPainter: Painter? = null,
    secondaryContentDescription: String? = null,
) {
    TakiAtmosphericSurface(
        atmosphereModel = artworkModel,
        shape = TakiTheme.shapes.lg,
        modifier = modifier
            .height(TakiTheme.dimensions.featuredCardHeight)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(TakiTheme.spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = title,
                        style = TakiTheme.type.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(TakiTheme.spacing.xxs))
                    Text(
                        text = subtitle,
                        style = TakiTheme.type.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayDisc(onClick = onPlay, contentDescription = playContentDescription)
                    if (onSecondary != null && secondaryPainter != null) {
                        TakiIconButton(
                            onClick = onSecondary,
                            painter = secondaryPainter,
                            contentDescription = secondaryContentDescription.orEmpty(),
                            iconSize = TakiTheme.dimensions.iconSm,
                        )
                    }
                }
            }
            Spacer(Modifier.width(TakiTheme.spacing.md))
            TakiArtwork(
                model = artworkModel,
                contentDescription = null,
                size = TakiTheme.dimensions.featuredCardArtwork,
                shape = TakiTheme.shapes.sm,
            )
        }
    }
}

@Composable
private fun PlayDisc(
    onClick: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(PlayDiscSize)
            .clip(CircleShape)
            .background(TakiTheme.colors.ivory)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.media_start),
            contentDescription = null,
            tint = TakiTheme.colors.black,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
    }
}
