/*
 * DailyMixQueueBuilderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.moire.ultrasonic.domain.Track

class DailyMixQueueBuilderTest {
    @Test
    fun `does not accept partial daily mix restores`() {
        val storedIds = (1..30).map { "track-$it" }
        val restoredTracks = storedIds.take(18).map { Track(id = it) }

        assertFalse(shouldUseRestoredDailyMix(storedIds, restoredTracks))
    }

    @Test
    fun `accepts complete daily mix restores`() {
        val storedIds = (1..30).map { "track-$it" }
        val restoredTracks = storedIds.map { Track(id = it) }

        assertTrue(shouldUseRestoredDailyMix(storedIds, restoredTracks))
    }
}
