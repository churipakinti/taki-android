package org.moire.ultrasonic.util

import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsQualityTest {
    @Test
    fun `uses requested default quality values`() {
        assertEquals(256, DEFAULT_MAX_BITRATE_MOBILE)
        assertEquals(0, DEFAULT_MAX_BITRATE_WIFI)
        assertEquals(0, DEFAULT_MAX_BITRATE_PINNING)
    }

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

    @Test
    fun `maps original quality to omitted Subsonic bitrate`() {
        assertEquals(null, 0.toSubsonicMaxBitRate())
        assertEquals(null, (-1).toSubsonicMaxBitRate())
    }

    @Test
    fun `keeps bitrate limits for transcoded qualities`() {
        assertEquals(96, 96.toSubsonicMaxBitRate())
        assertEquals(160, 160.toSubsonicMaxBitRate())
        assertEquals(256, 256.toSubsonicMaxBitRate())
        assertEquals(320, 320.toSubsonicMaxBitRate())
    }
}
