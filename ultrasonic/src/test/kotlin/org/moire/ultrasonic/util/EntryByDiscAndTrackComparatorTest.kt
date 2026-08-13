/*
 * EntryByDiscAndTrackComparatorTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.util

import java.util.Collections
import org.amshove.kluent.shouldBeEqualTo
import org.junit.Test
import org.moire.ultrasonic.domain.Track

/**
 * Locks down the disc/track ordering (and per-disc grouping) that Album Detail relies on both
 * for the main track list and for the disc header's Play button, which reuses a disc's slice of
 * this same sorted list as-is instead of re-sorting. See TAKI_ALBUM_DETAIL_FIX_PLAN.md problem 2.
 */
class EntryByDiscAndTrackComparatorTest {

    @Test
    fun `sorts by disc number then track number`() {
        val tracks = mutableListOf(
            track(id = "d2t1", disc = 2, trackNo = 1),
            track(id = "d1t2", disc = 1, trackNo = 2),
            track(id = "d1t1", disc = 1, trackNo = 1),
            track(id = "d2t2", disc = 2, trackNo = 2)
        )

        Collections.sort(tracks, EntryByDiscAndTrackComparator())

        tracks.map { it.id } shouldBeEqualTo listOf("d1t1", "d1t2", "d2t1", "d2t2")
    }

    @Test
    fun `falls back to path when track numbers are missing`() {
        val tracks = mutableListOf(
            track(id = "b", disc = 1, trackNo = null, path = "b.flac"),
            track(id = "a", disc = 1, trackNo = null, path = "a.flac")
        )

        Collections.sort(tracks, EntryByDiscAndTrackComparator())

        tracks.map { it.id } shouldBeEqualTo listOf("a", "b")
    }

    @Test
    fun `missing disc number is treated as disc 1`() {
        val tracks = mutableListOf(
            track(id = "explicit-disc-1", disc = 1, trackNo = 2),
            track(id = "no-disc", disc = null, trackNo = 1)
        )

        Collections.sort(tracks, EntryByDiscAndTrackComparator())

        tracks.map { it.id } shouldBeEqualTo listOf("no-disc", "explicit-disc-1")
    }

    @Test
    fun `grouping a sorted album by disc keeps each group in track order`() {
        val album = mutableListOf(
            track(id = "d2t2", disc = 2, trackNo = 2),
            track(id = "d1t2", disc = 1, trackNo = 2),
            track(id = "d2t1", disc = 2, trackNo = 1),
            track(id = "d1t1", disc = 1, trackNo = 1)
        )
        Collections.sort(album, EntryByDiscAndTrackComparator())

        val tracksByDisc = album.groupBy { it.discNumber ?: 1 }

        tracksByDisc[1]!!.map { it.id } shouldBeEqualTo listOf("d1t1", "d1t2")
        tracksByDisc[2]!!.map { it.id } shouldBeEqualTo listOf("d2t1", "d2t2")
    }

    private fun track(
        id: String,
        disc: Int?,
        trackNo: Int?,
        path: String? = null
    ): Track = Track(id = id, title = id, discNumber = disc, track = trackNo, path = path)
}
