/*
 * IncompleteTrackRemovalTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test

/**
 * Issue #2 -- switching Online -> Offline must not cut off the track that is already playing.
 *
 * [MediaPlayerManager.removeIncompleteTracksFromPlaylist] used to drop every non-downloaded
 * item on a server switch, the currently playing streamed track included, which is what
 * interrupted playback. [incompleteTrackIndicesToRemove] is the extracted decision: it keeps
 * the item Media3 is actively playing and drops the rest of the non-downloaded ones.
 */
class IncompleteTrackRemovalTest {

    @Test
    fun `keeps the currently playing track even though it is not downloaded`() {
        // queue: [dl, streaming(playing), streaming, dl]
        val downloaded = listOf(true, false, false, true)

        incompleteTrackIndicesToRemove(downloaded, currentIndex = 1) shouldBeEqualTo listOf(2)
    }

    @Test
    fun `drops every other non-downloaded track`() {
        val downloaded = listOf(false, false, true, false, false)

        incompleteTrackIndicesToRemove(downloaded, currentIndex = 2) shouldBeEqualTo
            listOf(0, 1, 3, 4)
    }

    @Test
    fun `when the current track is downloaded nothing is spared`() {
        val downloaded = listOf(false, true, false)

        incompleteTrackIndicesToRemove(downloaded, currentIndex = 1) shouldBeEqualTo listOf(0, 2)
    }

    @Test
    fun `nothing playing (index -1) removes all non-downloaded items`() {
        val downloaded = listOf(false, true, false, false)

        incompleteTrackIndicesToRemove(downloaded, currentIndex = -1) shouldBeEqualTo
            listOf(0, 2, 3)
    }

    @Test
    fun `an all-downloaded queue removes nothing`() {
        incompleteTrackIndicesToRemove(listOf(true, true, true), currentIndex = 0)
            .shouldBeEqualTo(emptyList())
    }

    @Test
    fun `an all-streaming queue keeps only the playing item`() {
        val downloaded = List(5) { false }

        incompleteTrackIndicesToRemove(downloaded, currentIndex = 0) shouldBeEqualTo
            listOf(1, 2, 3, 4)
    }

    @Test
    fun `an empty queue removes nothing`() {
        incompleteTrackIndicesToRemove(emptyList(), currentIndex = -1)
            .shouldBeEqualTo(emptyList())
    }

    @Test
    fun `an out-of-range current index still cleans the queue`() {
        // A stale index must not accidentally protect a real item or crash.
        val downloaded = listOf(false, false)

        incompleteTrackIndicesToRemove(downloaded, currentIndex = 9) shouldBeEqualTo listOf(0, 1)
    }

    @Test
    fun `returned indices are ascending so caller offset math stays simple`() {
        val downloaded = listOf(false, false, false, false)
        val result = incompleteTrackIndicesToRemove(downloaded, currentIndex = 2)

        result shouldBeEqualTo result.sorted()
        // Applying them in order with a running "removed" offset lands on live positions
        // 0, 0, 1 (index 3 -> 3 - 2 removed) - i.e. never touches the kept item at 2.
        val liveTargets = result.mapIndexed { removed, index -> index - removed }
        liveTargets shouldBeEqualTo listOf(0, 0, 1)
    }
}
