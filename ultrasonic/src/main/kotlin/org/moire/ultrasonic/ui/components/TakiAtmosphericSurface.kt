/*
 * TakiAtmosphericSurface.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import org.moire.ultrasonic.ui.theme.TakiAtmosphere
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * A featured surface with artwork-derived atmospheric depth (V2 section 7.3): the given
 * artwork - decoded small, lightly saturated and blurred - fills the card at
 * [TakiAtmosphere.FEATURE_ARTWORK_ALPHA] under a continuous left-to-right dark scrim (darker
 * on the start/text side, easing off so the artwork palette survives on the end side). The
 * card visibly inherits the artwork colour, like the visual north star; the base is always an
 * opaque `surface`, so with no [atmosphereModel] this is just a flat card.
 *
 * Reserved for featured content. Do not use it for shelf items or list rows.
 *
 * Cost: one ~96px Coil decode (cached, reused across recompositions); the saturation matrix,
 * blur and scrim are drawn into the same cached layer - no per-frame bitmap work.
 */
@Composable
fun TakiAtmosphericSurface(
    atmosphereModel: Any?,
    modifier: Modifier = Modifier,
    shape: Shape = TakiTheme.shapes.md,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = TakiTheme.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surface),
        propagateMinConstraints = true,
    ) {
        if (atmosphereModel != null) {
            val saturate = remember {
                ColorFilter.colorMatrix(
                    ColorMatrix().apply { setToSaturation(TakiAtmosphere.FEATURE_SATURATION) },
                )
            }
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(atmosphereModel)
                    .size(TakiAtmosphere.ATMOSPHERE_SOURCE_PX)
                    .crossfade(false)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = saturate,
                modifier = Modifier
                    .matchParentSize()
                    .blur(TakiAtmosphere.featureBlurRadius)
                    .alpha(TakiAtmosphere.FEATURE_ARTWORK_ALPHA),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to colors.black.copy(alpha = TakiAtmosphere.SCRIM_ALPHA_START),
                            TakiAtmosphere.SCRIM_STOP_MID to
                                colors.black.copy(alpha = TakiAtmosphere.SCRIM_ALPHA_MID),
                            1f to colors.black.copy(alpha = TakiAtmosphere.SCRIM_ALPHA_END),
                        ),
                    ),
            )
        }
        content()
    }
}
