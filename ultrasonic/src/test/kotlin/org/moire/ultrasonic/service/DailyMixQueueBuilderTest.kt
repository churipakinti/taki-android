/*
 * DailyMixQueueBuilderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
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

    @Test
    fun `restores every id by exact lookup, preserving order`() = runTest {
        val ids = (1..30).map { "track-$it" }
        val csv = ids.joinToString(",")

        val restored = restoreTracksByExactId(csv) { id -> Track(id = id) }

        assertEquals(ids, restored.map { it.id })
    }

    @Test
    fun `drops ids that fail to resolve instead of restoring a partial mix`() = runTest {
        val ids = (1..30).map { "track-$it" }
        val csv = ids.joinToString(",")
        val missing = setOf("track-5", "track-19")

        val restored = restoreTracksByExactId(csv) { id ->
            if (id in
                missing
            ) {
                null
            } else {
                Track(id = id)
            }
        }

        assertEquals(ids.size - missing.size, restored.size)
        assertFalse(shouldUseRestoredDailyMix(ids, restored))
    }

    @Test
    fun `restoring a blank id list returns an empty mix without calling the fetcher`() = runTest {
        var calls = 0

        val restored = restoreTracksByExactId("") {
            calls++
            Track(id = it)
        }

        assertTrue(restored.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `attempts restore only for the same day and same server`() {
        assertTrue(
            shouldAttemptDailyMixRestore(
                forceRefresh = false,
                storedDate = "2026-08-12",
                today = "2026-08-12",
                storedServerId = 1,
                serverId = 1
            )
        )
    }

    @Test
    fun `does not restore across a server switch, even on the same day`() {
        assertFalse(
            shouldAttemptDailyMixRestore(
                forceRefresh = false,
                storedDate = "2026-08-12",
                today = "2026-08-12",
                storedServerId = 1,
                serverId = 2
            )
        )
    }

    @Test
    fun `does not restore a mix stored on a previous day`() {
        assertFalse(
            shouldAttemptDailyMixRestore(
                forceRefresh = false,
                storedDate = "2026-08-11",
                today = "2026-08-12",
                storedServerId = 1,
                serverId = 1
            )
        )
    }

    @Test
    fun `never restores when the caller forces a refresh`() {
        assertFalse(
            shouldAttemptDailyMixRestore(
                forceRefresh = true,
                storedDate = "2026-08-12",
                today = "2026-08-12",
                storedServerId = 1,
                serverId = 1
            )
        )
    }
}
