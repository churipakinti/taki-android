/*
 * TakiTypography.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Taki's six typography roles for Compose, mirroring the `TextAppearance.Taki.*` styles in
 * `ultrasonic/src/main/res/values/type.xml`.
 *
 * The XML styles are thin overlays on Material 3 text appearances; the concrete size /
 * line-height / weight below are the *resolved* values (see
 * docs/technical/TAKI_COMPOSE_MIGRATION_PLAN.md section 5.2), pinned here rather than
 * re-derived from Compose's own Material 3 defaults, which can differ from the View ones.
 * `TakiTypographyTest` locks these numbers.
 *
 * Emphasis colour is baked in the same way `type.xml` bakes `android:textColor`: primary
 * roles use ivory (`onSurface`), supporting roles use gray (`onSurfaceVariant`).
 *
 * See docs/design/TAKI_DESIGN_SYSTEM_V2.md section 5.
 */
@Immutable
data class TakiTypography(
    /** `Taki.Hero` - page greeting, Now Playing title, major detail title. */
    val hero: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = PrimaryText,
    ),
    /** `Taki.Title` - row title, album / artist title. */
    val title: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = PrimaryText,
    ),
    /** `Taki.TitleSmall` - compact shelf / card title. */
    val titleSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = PrimaryText,
    ),
    /** `Taki.Body` - body copy. */
    val body: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = PrimaryText,
    ),
    /** `Taki.Caption` - artist, album, duration, counts. */
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = SupportingText,
    ),
    /** `Taki.SectionHeader` - shelf / group headers. Sentence case, never green. */
    val sectionHeader: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = SupportingText,
    ),
) {
    private companion object {
        /** `?attr/colorOnSurface` == `taki_ivory`. */
        val PrimaryText = TakiColors().ivory

        /** `?attr/colorOnSurfaceVariant` == `taki_gray`. */
        val SupportingText = TakiColors().gray
    }
}

/**
 * Ambient [TakiTypography]. Read through `TakiTheme.type`.
 */
val LocalTakiTypography = staticCompositionLocalOf { TakiTypography() }
