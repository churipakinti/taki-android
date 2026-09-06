/*
 * TakiTypographyTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the six `Taki.*` typography roles to the resolved size / line-height / weight /
 * emphasis-colour from TAKI_COMPOSE_MIGRATION_PLAN.md section 5.2 (the `type.xml` styles
 * plus their Material 3 parents). If Compose's own Material 3 defaults shift, these numbers
 * do not.
 */
class TakiTypographyTest {

    private val type = TakiTypography()
    private val ivory = TakiColors().ivory
    private val gray = TakiColors().gray

    @Test
    fun `hero role`() {
        assertEquals(24.sp, type.hero.fontSize)
        assertEquals(32.sp, type.hero.lineHeight)
        assertEquals(FontWeight.Medium, type.hero.fontWeight)
        assertEquals(ivory, type.hero.color)
    }

    @Test
    fun `title role`() {
        assertEquals(16.sp, type.title.fontSize)
        assertEquals(24.sp, type.title.lineHeight)
        assertEquals(FontWeight.Medium, type.title.fontWeight)
        assertEquals(ivory, type.title.color)
    }

    @Test
    fun `titleSmall role`() {
        assertEquals(14.sp, type.titleSmall.fontSize)
        assertEquals(20.sp, type.titleSmall.lineHeight)
        assertEquals(FontWeight.Medium, type.titleSmall.fontWeight)
        assertEquals(ivory, type.titleSmall.color)
    }

    @Test
    fun `body role`() {
        assertEquals(14.sp, type.body.fontSize)
        assertEquals(20.sp, type.body.lineHeight)
        assertEquals(FontWeight.Normal, type.body.fontWeight)
        assertEquals(ivory, type.body.color)
    }

    @Test
    fun `caption role is supporting emphasis`() {
        assertEquals(12.sp, type.caption.fontSize)
        assertEquals(16.sp, type.caption.lineHeight)
        assertEquals(FontWeight.Light, type.caption.fontWeight)
        assertEquals(gray, type.caption.color)
    }

    @Test
    fun `sectionHeader role is supporting emphasis, medium weight`() {
        assertEquals(14.sp, type.sectionHeader.fontSize)
        assertEquals(20.sp, type.sectionHeader.lineHeight)
        assertEquals(FontWeight.Medium, type.sectionHeader.fontWeight)
        assertEquals(gray, type.sectionHeader.color)
    }
}
