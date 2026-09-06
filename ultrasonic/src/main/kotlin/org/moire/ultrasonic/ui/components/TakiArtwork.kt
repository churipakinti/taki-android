/*
 * TakiArtwork.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * Square cover art with Taki's rules baked in: 1:1, centre-crop, one of the Taki corner
 * radii, no shadow, and a quiet neutral placeholder (surface + simple icon, never a green
 * field) whenever there is nothing to show.
 *
 * [model] is any Coil-loadable value (URL, file, resource id, ...) or `null`. Wiring the
 * app's shared `ImageLoader` is deferred to the first screen migration (issue #10); until
 * then [AsyncImage] uses the Coil singleton loader.
 *
 * See docs/design/TAKI_DESIGN_SYSTEM_V2.md section 7.
 */
@Composable
fun TakiArtwork(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = TakiTheme.dimensions.artworkCard,
    shape: Shape = TakiTheme.shapes.sm,
    placeholder: Painter = painterResource(R.drawable.unknown_album),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(TakiTheme.colors.surfaceLow),
        contentAlignment = Alignment.Center,
    ) {
        if (model == null) {
            Image(
                painter = placeholder,
                contentDescription = contentDescription,
                modifier = Modifier.size(size * PLACEHOLDER_ICON_FRACTION),
            )
        } else {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val PLACEHOLDER_ICON_FRACTION = 0.4f
