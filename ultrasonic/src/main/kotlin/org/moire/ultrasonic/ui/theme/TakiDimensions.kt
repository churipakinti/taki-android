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
 * Artwork sizes that only exist in TAKI_DESIGN_SYSTEM_V2.md section 7 (104 / 124 / 156 /
 * 184 / 96 / 364 dp) are intentionally NOT added here yet - they arrive with the screen
 * that needs them (issue #10), added to `dimens.xml` and this class in lockstep.
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
    /** `artwork_card` - standard larger card artwork. */
    val artworkCard: Dp = 140.dp,
    /** `elevation_raised` - reserved for a genuinely floating element (mini-player, sheet). */
    val elevationRaised: Dp = 3.dp,
    /** `border_thin` - the 1dp border used only when a border is unavoidable. */
    val borderThin: Dp = 1.dp,
)

/**
 * Ambient [TakiDimensions]. Read through `TakiTheme.dimensions`.
 */
val LocalTakiDimensions = staticCompositionLocalOf { TakiDimensions() }
