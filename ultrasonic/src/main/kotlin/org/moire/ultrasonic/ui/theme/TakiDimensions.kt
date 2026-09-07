/*
 * TakiDimensions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Component geometry tokens for Compose: icon sizes, touch targets, list row heights,
 * artwork footprints, and the two depth exceptions.
 *
 * 1:1 transcription of the non-spacing, non-radius dimensions in
 * `ultrasonic/src/main/res/values/dimens.xml`. `TakiTokensTest` cross-checks the values.
 *
 * Sizes from TAKI_DESIGN_SYSTEM_V2.md section 7/10 are added here and to `dimens.xml` in
 * lockstep as the screen that needs them lands (issue #10). Present so far: the Home
 * compact-shelf artwork and the Home featured card. Still absent: 184 / 96 / 364 dp.
 *
 * See docs/design/TAKI_DESIGN_SYSTEM_V2.md sections 6-8.
 */
@Immutable
data class TakiDimensions(
    /** `icon_size_sm` - secondary inline action glyph. */
    val iconSm: Dp = 18.dp,
    /** `icon_size_md` - standard action / navigation glyph. */
    val iconMd: Dp = 24.dp,
    /** `icon_size_lg` - transport / strong action glyph. */
    val iconLg: Dp = 32.dp,
    /** `touch_target_min` - the floor for anything tappable. */
    val touchTargetMin: Dp = 48.dp,
    /** `row_height_sm` - browse rows. */
    val rowSm: Dp = 56.dp,
    /** `row_height_md` - mini-player height. */
    val rowMd: Dp = 64.dp,
    /** `row_height_lg` - transport rows, bottom navigation. */
    val rowLg: Dp = 72.dp,
    /** `artwork_thumb` - compact row thumbnail. */
    val artworkThumb: Dp = 56.dp,
    /** `artwork_mini` - mini-player artwork (V2 section 13). */
    val artworkMini: Dp = 48.dp,
    /** `artwork_shelf_compact` - Home "Recently played" shelf artwork (V2 section 10.1). */
    val artworkShelfCompact: Dp = 104.dp,
    /** `artwork_card` - standard larger card / album shelf artwork. */
    val artworkCard: Dp = 140.dp,
    /** `featured_card_height` - Home featured ("mix") card (V2 section 10.1). */
    val featuredCardHeight: Dp = 156.dp,
    /** `featured_card_artwork` - artwork inside the Home featured card. */
    val featuredCardArtwork: Dp = 124.dp,
    /** `mini_player_height` - the floating mini-player band (V2 section 13). */
    val miniPlayerHeight: Dp = 64.dp,
    /**
     * `mini_player_edge_margin` - the floating mini-player's inset from the screen edges,
     * and the equal gap it leaves above the bottom nav, so it rests optically centred.
     */
    val miniPlayerEdgeMargin: Dp = 16.dp,
    /**
     * `content_inset_floating_chrome` - bottom `contentPadding` a scrollable screen adds so its
     * last item can clear the floating mini-player overlay. Screens still render *behind* it.
     */
    val contentInsetFloatingChrome: Dp = 96.dp,
    /** `elevation_raised` - reserved for a genuinely floating element (mini-player, sheet). */
    val elevationRaised: Dp = 3.dp,
    /** `border_thin` - the 1dp border used only when a border is unavoidable. */
    val borderThin: Dp = 1.dp,
)

/**
 * Ambient [TakiDimensions]. Read through `TakiTheme.dimensions`.
 */
val LocalTakiDimensions = staticCompositionLocalOf { TakiDimensions() }
