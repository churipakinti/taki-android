/*
 * ArtistRadioSelectorTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotContain
import org.junit.Test
import org.moire.ultrasonic.domain.Track

class ArtistRadioSelectorTest {

    @Test
    fun `balances seed artist with related artists and filler when available`() {
        val radio = ArtistRadioSelector.select(
            seedArtistId = "seed-artist",
            seedArtistName = "Seed Artist",
            seedArtistTracks = (1..30).map {
                track(
                    "seed-$it",
                    artist = "Seed Artist",
                    artistId = "seed-artist",
                    album = "Seed $it"
                )
            },
            relatedArtistTracks = (1..20).map {
                track("related-$it", artist = "Related $it", artistId = "related-$it")
            },
            fillerTracks = (1..20).map {
                track("filler-$it", artist = "Filler $it", artistId = "filler-$it")
            },
            targetSize = 30,
            minimumSize = 15
        )

        radio.size shouldBeEqualTo 30
        (radio.count { it.artistId == "seed-artist" } in 12..15) shouldBeEqualTo true
        (radio.count { it.id.startsWith("related-") } >= 10) shouldBeEqualTo true
        (radio.count { it.id.startsWith("filler-") } >= 5) shouldBeEqualTo true
    }

    @Test
    fun `falls back to a centered artist session when related artists are unavailable`() {
        val radio = ArtistRadioSelector.select(
            seedArtistId = "seed-artist",
            seedArtistName = "Seed Artist",
            seedArtistTracks = (1..18).map {
                track(
                    "seed-$it",
                    artist = "Seed Artist",
                    artistId = "seed-artist",
                    album = "Seed $it"
                )
            },
            relatedArtistTracks = emptyList(),
            fillerTracks = emptyList(),
            targetSize = 30,
            minimumSize = 15
        )

        radio.size shouldBeEqualTo 18
        radio.all { it.artistId == "seed-artist" } shouldBeEqualTo true
    }

    @Test
    fun `avoids album domination while enough albums are available`() {
        val dominantAlbum = (1..12).map {
            track(
                "dominant-$it",
                artist = "Seed Artist",
                artistId = "seed-artist",
                album = "One Album"
            )
        }
        val otherAlbums = (1..10).map {
            track(
                "album-$it",
                artist = "Seed Artist",
                artistId = "seed-artist",
                album = "Album $it"
            )
        }

        val radio = ArtistRadioSelector.select(
            seedArtistId = "seed-artist",
            seedArtistName = "Seed Artist",
            seedArtistTracks = dominantAlbum + otherAlbums,
            relatedArtistTracks = emptyList(),
            fillerTracks = emptyList(),
            targetSize = 14,
            minimumSize = 10
        )

        (radio.count { it.album == "One Album" } <= 4) shouldBeEqualTo true
    }

    @Test
    fun `avoids three consecutive songs by the same artist when possible`() {
        val radio = ArtistRadioSelector.select(
            seedArtistId = "seed-artist",
            seedArtistName = "Seed Artist",
            seedArtistTracks = (1..10).map {
                track(
                    "seed-$it",
                    artist = "Seed Artist",
                    artistId = "seed-artist",
                    album = "Seed $it"
                )
            },
            relatedArtistTracks = (1..10).map {
                track(
                    "related-$it",
                    artist = "Related",
                    artistId = "related",
                    album = "Related $it"
                )
            },
            fillerTracks = emptyList(),
            targetSize = 16,
            minimumSize = 12
        )

        radio.windowed(3).map { window -> window.map { it.artistId } } shouldNotContain
            listOf("seed-artist", "seed-artist", "seed-artist")
    }

    private fun track(id: String, artist: String, artistId: String, album: String = id): Track =
        Track(
            id = id,
            title = id,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = album
        )
}
