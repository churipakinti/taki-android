/*
 * TakiButtonContrastTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.components

import androidx.compose.ui.graphics.Color
import java.io.File
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.moire.ultrasonic.ui.theme.TakiColors

/**
 * Issue #26 phase 5C.1: a bright accent container must carry `onAccent` content, never ivory.
 * Standard WCAG 2.x relative-luminance contrast, over the canonical Taki tokens.
 */
class TakiButtonContrastTest {

    private val c = TakiColors()

    private fun channel(v: Float): Double {
        val d = v.toDouble()
        return if (d <= 0.03928) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    @Test
    fun accentWithOnAccentPassesNormalText() {
        assertTrue(contrast(c.accent, c.onAccent) >= NORMAL_TEXT)
    }

    @Test
    fun accentWithIvoryFailsNormalText() {
        assertFalse(contrast(c.accent, c.ivory) >= NORMAL_TEXT)
    }

    @Test
    fun canonicalNeutralPairingsPass() {
        assertTrue(contrast(c.black, c.ivory) >= NORMAL_TEXT)
        assertTrue(contrast(c.surface, c.ivory) >= NORMAL_TEXT)
        assertTrue(contrast(c.surfaceHigh, c.gray) >= NORMAL_TEXT)
    }

    @Test
    fun primaryPaletteUsesAccentWithOnAccent() {
        val p = takiPrimaryButtonPalette(c)
        assertEquals(c.accent, p.container)
        assertEquals(c.onAccent, p.content)
        assertTrue(contrast(p.container, p.content) >= NORMAL_TEXT)
    }

    @Test
    fun destructivePaletteIsNotGreenAndReadable() {
        val p = takiDestructiveButtonPalette(c)
        assertTrue(p.container != c.accent)
        assertTrue(contrast(p.container, p.content) >= NORMAL_TEXT)
    }

    @Test
    fun secondaryPaletteReadableOnSheetSurface() {
        val p = takiSecondaryButtonPalette(c)
        assertTrue(contrast(c.surface, p.content) >= NORMAL_TEXT)
    }

    @Test
    fun disabledLabelsStayReadableButDifferFromEnabled() {
        for (p in listOf(takiPrimaryButtonPalette(c), takiDestructiveButtonPalette(c))) {
            val over = p.disabledContainer.compositeOver(c.black)
            assertTrue(contrast(over, p.disabledContent.compositeOver(over)) >= LARGE_OR_ICON)
            assertTrue(p.disabledContainer != p.container)
        }
    }

    /** No hand-rolled accent-filled Material buttons outside the shared primitives. */
    @Test
    fun noHandRolledAccentButtonColors() {
        val root = File("src/main/kotlin/org/moire/ultrasonic/ui")
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "TakiButtons.kt" }
            .filter { Regex("""buttonColors\([^)]*accent""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(it.readText()) }
            .map { it.name }
            .toList()
        assertTrue("Use TakiPrimaryButton instead of hand-rolled accent buttons: $offenders", offenders.isEmpty())
    }

    private fun Color.compositeOver(background: Color): Color {
        val a = alpha
        return Color(
            red = red * a + background.red * (1 - a),
            green = green * a + background.green * (1 - a),
            blue = blue * a + background.blue * (1 - a),
        )
    }

    private companion object {
        const val NORMAL_TEXT = 4.5
        const val LARGE_OR_ICON = 3.0
    }
}
