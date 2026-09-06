/*
 * TakiShapes.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Taki's corner-radius scale for Compose.
 *
 * The radius values are a 1:1 transcription of `radius_*` in
 * `ultrasonic/src/main/res/values/dimens.xml`; `TakiTokensTest` cross-checks them.
 * Do not mix multiple radii for the same component class.
 *
 * See docs/design/TAKI_DESIGN_SYSTEM_V2.md section 4.
 */
@Immutable
data class TakiShapes(
    /** `radius_xs` (4dp) - very small inline elements. */
    val xs: Shape = RoundedCornerShape(4.dp),
    /** `radius_sm` (8dp) - standard album artwork, compact cards. */
    val sm: Shape = RoundedCornerShape(8.dp),
    /** `radius_md` (12dp) - cards, bottom panels, hero artwork, mini-player. */
    val md: Shape = RoundedCornerShape(12.dp),
    /** `radius_lg` (20dp) - large / featured surfaces. */
    val lg: Shape = RoundedCornerShape(20.dp),
    /** Circle - play button, round controls, avatars. */
    val circle: Shape = CircleShape,
)

/**
 * Ambient [TakiShapes]. Read through `TakiTheme.shapes`.
 */
val LocalTakiShapes = staticCompositionLocalOf { TakiShapes() }
