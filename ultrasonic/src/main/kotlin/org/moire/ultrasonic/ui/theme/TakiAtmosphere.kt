/*
 * TakiAtmosphere.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The small, deliberate set of values that drive Taki's atmospheric depth (V2 sections 7.3 /
 * 13 / 14). This is NOT a general "glass" system - it is reserved for two things:
 *
 *  - featured content: an artwork-derived blurred wash behind the Home daily-mix card
 *    ([org.moire.ultrasonic.ui.components.TakiAtmosphericSurface]) - the card should visibly
 *    inherit the artwork palette, like the approved visual north star, while the text side
 *    stays dark enough for contrast;
 *  - persistent floating chrome: the mini-player and bottom navigation
 *    ([org.moire.ultrasonic.ui.components.TakiFloatingSurface] in Compose; the same alphas
 *    are baked into `taki_surface_floating` / `taki_surface_low_floating` /
 *    `taki_edge_highlight` for the current View mini-player and bottom nav).
 *
 * Normal artwork cards, shelves and list rows stay flat artwork-on-background - do not use
 * these there.
 */
@Suppress("MagicNumber") // this file *is* the definition of these values
object TakiAtmosphere {

    /** Blur applied to the featured card's artwork wash - enough to leave colour, not detail. */
    val featureBlurRadius: Dp = 28.dp

    /** How much of the (blurred) artwork is present before the scrim. Palette, not a picture. */
    const val FEATURE_ARTWORK_ALPHA: Float = 0.55f

    /** Mild saturation lift on the wash so muted covers still read as colour (1.0 = untouched). */
    const val FEATURE_SATURATION: Float = 1.15f

    /** Coil decode size for the atmosphere source - small (cheap, pre-softened), but not tiny. */
    const val ATMOSPHERE_SOURCE_PX: Int = 96

    // A continuous left-to-right dark scrim: dark enough for the title on the start side,
    // easing off so the artwork palette survives on the end (artwork) side. Three stops, no
    // "black block then artwork block".

    /** Scrim at the text (start) edge. */
    const val SCRIM_ALPHA_START: Float = 0.78f

    /** Scrim at [SCRIM_STOP_MID] across the card. */
    const val SCRIM_ALPHA_MID: Float = 0.50f

    /** Scrim at the artwork (end) edge. */
    const val SCRIM_ALPHA_END: Float = 0.24f

    /** Where the mid scrim stop sits (fraction of card width). */
    const val SCRIM_STOP_MID: Float = 0.42f

    /**
     * Opacity of the mini-player surface - it overlays scrollable content, so it is a touch
     * more translucent than the bottom nav. Baked as `taki_surface_floating` (`0xDE`) in XML;
     * it is also the default for [org.moire.ultrasonic.ui.components.TakiFloatingSurface].
     */
    const val MINI_PLAYER_SURFACE_ALPHA: Float = 0.87f

    /**
     * Opacity of the bottom-nav surface - the lower, more grounded floating layer, so it is a
     * little more opaque than the mini-player and keeps its small labels legible over content
     * scrolling behind it. Baked as `taki_surface_low_floating` (`0xED`) in XML.
     */
    const val FLOATING_SURFACE_ALPHA: Float = 0.93f

    /** A 1dp tonal edge on a floating surface. Baked as `taki_edge_highlight` in XML. */
    const val EDGE_HIGHLIGHT_ALPHA: Float = 0.07f
}
