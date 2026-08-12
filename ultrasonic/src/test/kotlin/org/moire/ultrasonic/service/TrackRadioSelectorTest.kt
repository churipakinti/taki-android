/*
 * TrackRadioSelectorTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotContain
import org.junit.Test
import org.moire.ultrasonic.domain.Track

class TrackRadioSelectorTest {

    @Test
    fun `keeps seed first and excludes duplicate seed candidates`() {
        val seed = track("seed", artist = "Seed Artist")

        val radio = TrackRadioSelector.select(
            seed = seed,
            candidateGroups = listOf(listOf(seed, track("a"), track("b"))),
            targetSize = 10,
            minimumSize = 3
        )

        radio.first() shouldBeEqualTo seed
        radio.map { it.id } shouldBeEqualTo listOf("seed", "a", "b")
    }

    @Test
    fun `deduplicates candidates across fallback groups`() {
        val duplicate = track("dup", artist = "A")

        val radio = TrackRadioSelector.select(
            seed = track("seed"),
            candidateGroups = listOf(
                listOf(duplicate, track("one")),
                listOf(duplicate, track("two"))
            ),
            targetSize = 10,
            minimumSize = 4
        )

        radio.map { it.id } shouldBeEqualTo listOf("seed", "dup", "one", "two")
    }

    @Test
    fun `does not let one artist dominate while enough variety exists`() {
        val sameArtist = (1..8).map { track("same-$it", artist = "A") }
        val otherArtists = (1..8).map { track("other-$it", artist = "B$it") }

        val radio = TrackRadioSelector.select(
            seed = track("seed", artist = "A"),
            candidateGroups = listOf(sameArtist, otherArtists),
            targetSize = 10,
            minimumSize = 5
        )

        (radio.count { it.artist == "A" } <= 4) shouldBeEqualTo true
    }

    @Test
    fun `avoids three consecutive songs by the same artist when possible`() {
        val radio = TrackRadioSelector.select(
            seed = track("seed", artist = "A"),
            candidateGroups = listOf(
                listOf(
                    track("a1", artist = "A"),
                    track("a2", artist = "A"),
                    track("b1", artist = "B"),
                    track("a3", artist = "A")
                )
            ),
            targetSize = 10,
            minimumSize = 4
        )

        radio.windowed(3).map { window -> window.map { it.artist } } shouldNotContain
            listOf("A", "A", "A")
    }

    private fun track(id: String, artist: String = id, artistId: String? = null): Track =
        Track(id = id, title = id, artist = artist, artistId = artistId)
}
