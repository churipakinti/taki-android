package org.moire.ultrasonic.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsQualityTest {
    @Test
    fun `keeps supported qualities and original`() {
        listOf(0, 96, 160, 256, 320).forEach {
            assertEquals(it, normalizeBitrateQuality(it))
        }
    }

    @Test
    fun `maps legacy qualities to nearest simplified tier`() {
        assertEquals(96, normalizeBitrateQuality(32))
        assertEquals(96, normalizeBitrateQuality(112))
        assertEquals(160, normalizeBitrateQuality(192))
        assertEquals(320, normalizeBitrateQuality(500))
    }
}
