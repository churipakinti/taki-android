/*
 * PlaybackResumptionTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Issue #37: what a Media3 playback resumption hands back, and that it can never hang. */
@RunWith(RobolectricTestRunner::class)
class PlaybackResumptionTest {

    private fun items(n: Int) = List(n) { MediaItem.Builder().setMediaId("t$it").build() }

    @Test
    fun `an empty queue has nothing to resume`() {
        assertNull(resumptionOf(emptyList(), 0, 1_000L))
    }

    @Test
    fun `the restored queue index and position are returned`() {
        val r = resumptionOf(items(5), 3, 42_000L)!!
        assertEquals(5, r.mediaItems.size)
        assertEquals(3, r.startIndex)
        assertEquals(42_000L, r.startPositionMs)
        assertEquals("t3", r.mediaItems[r.startIndex].mediaId)
    }

    @Test
    fun `an out of range index or negative position is clamped`() {
        assertEquals(1, resumptionOf(items(2), 9, 0L)!!.startIndex)
        assertEquals(0, resumptionOf(items(2), -1, 0L)!!.startIndex)
        assertEquals(0L, resumptionOf(items(2), 0, -5L)!!.startPositionMs)
    }

    @Test
    fun `the resumption timeout is finite and generous enough for a large queue`() {
        assertNotNull(RESUMPTION_TIMEOUT_MS)
        assertTrue(RESUMPTION_TIMEOUT_MS in 5_000L..60_000L)
    }
}
