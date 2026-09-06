/*
 * TakiIcons.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp

/**
 * Icon conventions for Compose.
 *
 * Taki does not ship a Compose icon set of its own: action glyphs stay the vector drawables
 * already in `res/drawable`, resolved with [painter]. What this object owns is the *sizing*
 * contract - the three semantic sizes from `dimens.xml` / TAKI_DESIGN_SYSTEM_V2.md section 6
 * that a call site should pick from instead of a raw `.dp`.
 *
 * Tint is not decided here; it comes from `LocalContentColor` at the call site (gray by
 * default, accent only for an active semantic state).
 */
object TakiIcons {

    /** `icon_size_sm` (18dp) - secondary inline action. */
    val smallSize: Dp get() = TakiDimensions().iconSm

    /** `icon_size_md` (24dp) - standard action / navigation. */
    val mediumSize: Dp get() = TakiDimensions().iconMd

    /** `icon_size_lg` (32dp) - transport / strong action glyph. */
    val largeSize: Dp get() = TakiDimensions().iconLg

    /** Resolve a drawable resource as a Compose [Painter]. */
    @Composable
    fun painter(@DrawableRes id: Int): Painter = painterResource(id)
}
