/*
 * TakiColors.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Taki's canonical colour palette for Compose.
 *
 * These values are a 1:1 transcription of `ultrasonic/src/main/res/values/colors.xml`
 * (`taki_*`). They must stay identical to the XML while both worlds coexist during the
 * Compose migration - `TakiTokensTest` reads `colors.xml` and fails if any value drifts.
 * Do not introduce a second, Compose-only palette here or at call sites.
 *
 * See docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md section 5.2 and
 * docs/design/TAKI_DESIGN_SYSTEM_V2.md section 2.
 */
@Immutable
@Suppress("MagicNumber") // literal transcription of colors.xml; TakiTokensTest is the guard
data class TakiColors(
    /** `taki_black` - main app background / canvas. */
    val black: Color = Color(0xFF090B09),
    /** `taki_surface_low` - persistent low-emphasis surfaces, navigation, bottom panels. */
    val surfaceLow: Color = Color(0xFF111410),
    /** `taki_surface` - interactive cards, mini-player, panels. */
    val surface: Color = Color(0xFF171A16),
    /** `taki_surface_high` - pressed / temporarily elevated surface. */
    val surfaceHigh: Color = Color(0xFF1D211B),
    /** `taki_ivory` - primary text and high-emphasis icons. */
    val ivory: Color = Color(0xFFF1F2ED),
    /** `taki_gray` - secondary text and icons. */
    val gray: Color = Color(0xFFA7AAA2),
    /** `taki_accent` - brand / selected semantic state. Never a fill colour. */
    val accent: Color = Color(0xFFB7D63C),
    /** `taki_accent_pressed` - pressed accent. */
    val accentPressed: Color = Color(0xFF91AD30),
    /** `taki_accent_secondary` - secondary accent role. */
    val accentSecondary: Color = Color(0xFF91AD30),
    /** `taki_progress` - quiet playback progress. */
    val progress: Color = Color(0xFF7C9229),
    /** `taki_selected_container` - low-intensity green selection container. */
    val selectedContainer: Color = Color(0xFF293217),
    /** `taki_on_accent` - content on bright accent. */
    val onAccent: Color = Color(0xFF11130D),
    /** `taki_selected_neutral` - neutral selected state (~8% white). */
    val selectedNeutral: Color = Color(0x14FFFFFF),
    /** `taki_outline` - rare outline (~12% white). */
    val outline: Color = Color(0x1FFFFFFF),
    /** `taki_divider` - divider (~5% white). */
    val divider: Color = Color(0x0DFFFFFF),
    /** `taki_surface_floating` - `surface` at the mini-player's opacity (see TakiAtmosphere). */
    val surfaceFloating: Color = Color(0xDE171A16),
    /** `taki_surface_low_floating` - `surfaceLow` at the bottom-nav opacity. */
    val surfaceLowFloating: Color = Color(0xED111410),
    /** `taki_edge_highlight` - a 1dp tonal edge on a floating surface (~7% ivory). */
    val edgeHighlight: Color = Color(0x12F1F2ED),
    /** `taki_error` - error. */
    val error: Color = Color(0xFFFFB4AB),
    /** `taki_on_error_container` - error-container content. */
    val onErrorContainer: Color = Color(0xFFFFDAD6),
)

/**
 * Ambient [TakiColors]. Read through `TakiTheme.colors`, never re-created ad hoc.
 */
val LocalTakiColors = staticCompositionLocalOf { TakiColors() }
