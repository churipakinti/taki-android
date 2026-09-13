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
    /** `library_card_height` - Library "Your music" 2-column primary card (V2 section 11.3). */
    val libraryCardHeight: Dp = 80.dp,
    /** `search_field_height` - the Search screen's tonal input container (V2 section 11). */
    val searchFieldHeight: Dp = 52.dp,
    /** `album_hero_artwork_min` - the detail hero cover's responsive floor (V2 sections 7/12). */
    val albumHeroArtworkMin: Dp = 240.dp,
    /** `album_hero_artwork_max` - the detail hero cover's responsive cap on a phone. */
    val albumHeroArtworkMax: Dp = 320.dp,
    /** `detail_primary_action` - the primary Play button on a detail screen (V2 section 6). */
    val detailPrimaryAction: Dp = 64.dp,
    /** `track_row_min_height` - a detail track row; grows past this for a wrapped long title. */
    val trackRowMinHeight: Dp = 56.dp,
    /** `track_number_column` - the narrow leading number / now-playing-glyph column. */
    val trackNumberColumn: Dp = 28.dp,
    /** `screen_header_height` - the lightweight screen-owned top row (back + optional title)
     *  a Compose back-nav sub-screen draws instead of the Material app bar (issue #10 phase 4B). */
    val screenHeaderHeight: Dp = 56.dp,
    /** `artist_hero_artwork` - the Artist Detail identity hero, a fixed centred rounded square
     *  (issue #10 phase 4C). Smaller than the Album Detail responsive hero. */
    val artistHeroArtwork: Dp = 220.dp,
    /** `collection_hero_cover` - one member cover in the Collection Detail identity mark
     *  (issue #10 phase 4B): three of these fanned horizontally, front one centred. */
    val collectionHeroCover: Dp = 112.dp,
    /** `collection_hero_overlap_step` - how far each peeking back cover is offset from the
     *  centred front cover in the identity fan. */
    val collectionHeroOverlapStep: Dp = 36.dp,
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
