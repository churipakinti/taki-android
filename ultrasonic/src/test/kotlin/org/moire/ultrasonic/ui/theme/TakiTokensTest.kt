/*
 * TakiTokensTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the Compose token layer to the XML design system it mirrors.
 *
 * The test reads `src/main/res/values/colors.xml` and `dimens.xml` directly and fails if a
 * Taki Compose token has drifted from its XML source, or if a new `taki_*` colour was added
 * to XML without a matching Compose token. This is the guard that keeps the two worlds
 * identical while they coexist during the migration (issue #9 / plan section 5).
 */
class TakiTokensTest {

    private val colorsXml = File("src/main/res/values/colors.xml").readText()
    private val dimensXml = File("src/main/res/values/dimens.xml").readText()

    private val takiColors = TakiColors()
    private val spacing = TakiSpacing()
    private val shapes = TakiShapes()
    private val dimensions = TakiDimensions()

    /** `taki_*` colour name in `colors.xml` -> the Compose value that must equal it. */
    private val colorBindings: Map<String, Color> = mapOf(
        "taki_black" to takiColors.black,
        "taki_surface_low" to takiColors.surfaceLow,
        "taki_surface" to takiColors.surface,
        "taki_surface_high" to takiColors.surfaceHigh,
        "taki_ivory" to takiColors.ivory,
        "taki_gray" to takiColors.gray,
        "taki_accent" to takiColors.accent,
        "taki_accent_pressed" to takiColors.accentPressed,
        "taki_accent_secondary" to takiColors.accentSecondary,
        "taki_progress" to takiColors.progress,
        "taki_selected_container" to takiColors.selectedContainer,
        "taki_on_accent" to takiColors.onAccent,
        "taki_selected_neutral" to takiColors.selectedNeutral,
        "taki_outline" to takiColors.outline,
        "taki_divider" to takiColors.divider,
        "taki_surface_floating" to takiColors.surfaceFloating,
        "taki_surface_low_floating" to takiColors.surfaceLowFloating,
        "taki_edge_highlight" to takiColors.edgeHighlight,
        "taki_liked" to takiColors.liked,
        "taki_error" to takiColors.error,
        "taki_on_error_container" to takiColors.onErrorContainer
    )

    @Test
    fun `every Taki colour token equals its colors_xml value`() {
        colorBindings.forEach { (name, composeColor) ->
            val hex = colorHex(name)
            assertEquals(
                "taki color '$name' drifted from colors.xml",
                parseColor(hex),
                composeColor
            )
        }
    }

    @Test
    fun `no taki_ colour in colors_xml is missing a Compose token`() {
        val xmlNames = Regex("""<color name="(taki_[a-z_]+)"""")
            .findAll(colorsXml)
            .map { it.groupValues[1] }
            .toSet()
        val uncovered = xmlNames - colorBindings.keys
        assertTrue(
            "colors.xml has taki_* colours with no TakiColors token: $uncovered",
            uncovered.isEmpty()
        )
    }

    @Test
    fun `spacing tokens equal the space_ scale in dimens_xml`() {
        assertEquals(dimenDp("space_xxs").dp, spacing.xxs)
        assertEquals(dimenDp("space_xs").dp, spacing.xs)
        assertEquals(dimenDp("space_sm").dp, spacing.sm)
        assertEquals(dimenDp("space_md").dp, spacing.md)
        assertEquals(dimenDp("space_lg").dp, spacing.lg)
        assertEquals(dimenDp("space_xl").dp, spacing.xl)
        assertEquals(dimenDp("space_2xl").dp, spacing.xxl)
        // Semantic aliases resolve to another dimen ref in XML.
        assertEquals(dimenDp("space_lg").dp, spacing.screenHorizontal)
        assertEquals(dimenDp("space_xl").dp, spacing.sectionGap)
        assertEquals(dimenDp("space_xxs").dp, spacing.textTight)
    }

    @Test
    fun `shape tokens equal the radius_ scale in dimens_xml`() {
        assertEquals(RoundedCornerShape(dimenDp("radius_xs").dp), shapes.xs)
        assertEquals(RoundedCornerShape(dimenDp("radius_sm").dp), shapes.sm)
        assertEquals(RoundedCornerShape(dimenDp("radius_md").dp), shapes.md)
        assertEquals(RoundedCornerShape(dimenDp("radius_lg").dp), shapes.lg)
    }

    @Test
    fun `dimension tokens equal dimens_xml`() {
        assertEquals(dimenDp("icon_size_sm").dp, dimensions.iconSm)
        assertEquals(dimenDp("icon_size_md").dp, dimensions.iconMd)
        assertEquals(dimenDp("icon_size_lg").dp, dimensions.iconLg)
        assertEquals(dimenDp("touch_target_min").dp, dimensions.touchTargetMin)
        assertEquals(dimenDp("row_height_sm").dp, dimensions.rowSm)
        assertEquals(dimenDp("row_height_md").dp, dimensions.rowMd)
        assertEquals(dimenDp("row_height_lg").dp, dimensions.rowLg)
        assertEquals(dimenDp("artwork_thumb").dp, dimensions.artworkThumb)
        assertEquals(dimenDp("artwork_mini").dp, dimensions.artworkMini)
        assertEquals(dimenDp("artwork_shelf_compact").dp, dimensions.artworkShelfCompact)
        assertEquals(dimenDp("artwork_card").dp, dimensions.artworkCard)
        assertEquals(dimenDp("featured_card_height").dp, dimensions.featuredCardHeight)
        assertEquals(dimenDp("featured_card_artwork").dp, dimensions.featuredCardArtwork)
        assertEquals(dimenDp("library_card_height").dp, dimensions.libraryCardHeight)
        assertEquals(dimenDp("search_field_height").dp, dimensions.searchFieldHeight)
        assertEquals(dimenDp("album_hero_artwork_min").dp, dimensions.albumHeroArtworkMin)
        assertEquals(dimenDp("album_hero_artwork_max").dp, dimensions.albumHeroArtworkMax)
        assertEquals(dimenDp("detail_primary_action").dp, dimensions.detailPrimaryAction)
        assertEquals(dimenDp("track_row_min_height").dp, dimensions.trackRowMinHeight)
        assertEquals(dimenDp("track_number_column").dp, dimensions.trackNumberColumn)
        assertEquals(dimenDp("mini_player_height").dp, dimensions.miniPlayerHeight)
        assertEquals(dimenDp("mini_player_edge_margin").dp, dimensions.miniPlayerEdgeMargin)
        assertEquals(dimenDp("content_inset_floating_chrome").dp, dimensions.contentInsetFloatingChrome)
        assertEquals(dimenDp("elevation_raised").dp, dimensions.elevationRaised)
        assertEquals(dimenDp("border_thin").dp, dimensions.borderThin)
    }

    @Test
    fun `floating chrome colours encode the TakiAtmosphere alphas over the base surfaces`() {
        val miniPlayerAlpha = Math.round(TakiAtmosphere.MINI_PLAYER_SURFACE_ALPHA * ALPHA_MAX)
        val bottomNavAlpha = Math.round(TakiAtmosphere.FLOATING_SURFACE_ALPHA * ALPHA_MAX)
        val edgeAlpha = Math.round(TakiAtmosphere.EDGE_HIGHLIGHT_ALPHA * ALPHA_MAX)

        // surface_floating = surface RGB @ MINI_PLAYER_SURFACE_ALPHA
        assertEquals(takiColors.surface.red, takiColors.surfaceFloating.red, 0f)
        assertEquals(takiColors.surface.green, takiColors.surfaceFloating.green, 0f)
        assertEquals(takiColors.surface.blue, takiColors.surfaceFloating.blue, 0f)
        assertEquals(miniPlayerAlpha, Math.round(takiColors.surfaceFloating.alpha * ALPHA_MAX))
        // surface_low_floating = surfaceLow RGB @ FLOATING_SURFACE_ALPHA
        assertEquals(takiColors.surfaceLow.red, takiColors.surfaceLowFloating.red, 0f)
        assertEquals(bottomNavAlpha, Math.round(takiColors.surfaceLowFloating.alpha * ALPHA_MAX))
        // edge_highlight = ivory RGB @ EDGE_HIGHLIGHT_ALPHA
        assertEquals(takiColors.ivory.red, takiColors.edgeHighlight.red, 0f)
        assertEquals(edgeAlpha, Math.round(takiColors.edgeHighlight.alpha * ALPHA_MAX))
    }

    private fun colorHex(name: String): String =
        // `[^>]*` tolerates attributes on the tag (e.g. tools:ignore) - the value must still
        // be an inline hex literal, never a @color/ reference.
        Regex("""<color name="$name"[^>]*>(#[0-9A-Fa-f]+)</color>""")
            .find(colorsXml)
            ?.groupValues?.get(1)
            ?: error("colors.xml has no <color name=\"$name\">")

    private fun parseColor(hex: String): Color {
        val digits = hex.removePrefix("#")
        val argb = when (digits.length) {
            RGB_HEX_LENGTH -> "FF$digits"
            ARGB_HEX_LENGTH -> digits
            else -> error("Unexpected colour literal '$hex'")
        }
        return Color(argb.toLong(HEX_RADIX))
    }

    /** Reads `<dimen name="x">Ndp</dimen>`, following one level of `@dimen/...` aliasing. */
    private fun dimenDp(name: String): Int {
        val raw = Regex("""<dimen name="$name">([^<]+)</dimen>""")
            .find(dimensXml)
            ?.groupValues?.get(1)
            ?.trim()
            ?: error("dimens.xml has no <dimen name=\"$name\">")
        if (raw.startsWith("@dimen/")) return dimenDp(raw.removePrefix("@dimen/"))
        return raw.removeSuffix("dp").toInt()
    }

    private companion object {
        const val RGB_HEX_LENGTH = 6
        const val ARGB_HEX_LENGTH = 8
        const val HEX_RADIX = 16
        const val ALPHA_MAX = 255
    }
}
