/*
 * TakiSpacing.kt
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
 * Taki's 4dp spacing rhythm for Compose.
 *
 * 1:1 transcription of the spacing scale in `ultrasonic/src/main/res/values/dimens.xml`
 * (`space_*`). Every gutter, padding and gap in production Compose UI picks one of these
 * instead of a raw `.dp`. `TakiTokensTest` cross-checks the values against `dimens.xml`.
 *
 * See docs/design/TAKI_DESIGN_SYSTEM_V2.md section 3.
 */
@Immutable
data class TakiSpacing(
    /** `space_xxs` - title to immediate metadata. */
    val xxs: Dp = 2.dp,
    /** `space_xs` - micro internal gap. */
    val xs: Dp = 4.dp,
    /** `space_sm` - icon/text gap, compact items. */
    val sm: Dp = 8.dp,
    /** `space_md` - artwork to text, card internal gap. */
    val md: Dp = 12.dp,
    /** `space_lg` - standard screen gutter / card padding. */
    val lg: Dp = 16.dp,
    /** `space_xl` - section separation. */
    val xl: Dp = 24.dp,
    /** `space_2xl` - major composition separation. */
    val xxl: Dp = 32.dp,
    /** `space_screen_horizontal` - the one screen edge gutter for primary content. */
    val screenHorizontal: Dp = 16.dp,
    /** `space_section_gap` - vertical gap between shelves / detail sections. */
    val sectionGap: Dp = 24.dp,
    /** `space_text_tight` - gap between a title and its immediate subtitle. */
    val textTight: Dp = 2.dp,
)

/**
 * Ambient [TakiSpacing]. Read through `TakiTheme.spacing`.
 */
val LocalTakiSpacing = staticCompositionLocalOf { TakiSpacing() }
