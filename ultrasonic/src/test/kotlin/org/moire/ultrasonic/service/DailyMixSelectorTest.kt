/*
 * DailyMixSelectorTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.moire.ultrasonic.domain.Track

class DailyMixSelectorTest {
    @Test
    fun `builds target mix near 50 30 20 composition`() {
        val mix = DailyMixSelector.select(
            familiarTracks = tracks("familiar", 20),
            rediscoveryTracks = tracks("rediscovery", 20),
            explorationTracks = tracks("exploration", 20),
            fallbackTracks = tracks("fallback", 20),
            targetSize = 30,
            minimumSize = 15
        )

        assertEquals(30, mix.size)
        assertEquals(15, mix.count { it.id.startsWith("familiar") })
        assertEquals(9, mix.count { it.id.startsWith("rediscovery") })
        assertEquals(6, mix.count { it.id.startsWith("exploration") })
        assertFalse(mix.any { it.id.startsWith("fallback") })
    }

    @Test
    fun `fills from other buckets and fallback when a category is small`() {
        val mix = DailyMixSelector.select(
            familiarTracks = tracks("familiar", 3),
            rediscoveryTracks = tracks("rediscovery", 4),
            explorationTracks = tracks("exploration", 3),
            fallbackTracks = tracks("fallback", 40),
            targetSize = 30,
            minimumSize = 15
        )

        assertEquals(30, mix.size)
        assertTrue(mix.count { it.id.startsWith("fallback") } >= 15)
    }

    @Test
    fun `deduplicates tracks across buckets and filters videos`() {
        val duplicate = track("same-id", 1)
        val video = track("video", 2).apply { isVideo = true }

        val mix = DailyMixSelector.select(
            familiarTracks = listOf(duplicate, video) + tracks("familiar", 10),
            rediscoveryTracks = listOf(duplicate) + tracks("rediscovery", 10),
            explorationTracks = tracks("exploration", 10),
            fallbackTracks = emptyList(),
            targetSize = 20,
            minimumSize = 15
        )

        assertEquals(1, mix.count { it.id == duplicate.id })
        assertFalse(mix.any { it.id == video.id })
    }

    @Test
    fun `avoids three consecutive songs from one artist when alternatives exist`() {
        val dominant = (1..20).map { index ->
            Track(
                id = "dominant-$index",
                artist = "Dominant Artist",
                artistId = "dominant"
            )
        }
        val alternatives = tracks("alt", 20)

        val mix = DailyMixSelector.select(
            familiarTracks = dominant,
            rediscoveryTracks = alternatives,
            explorationTracks = emptyList(),
            fallbackTracks = emptyList(),
            targetSize = 20,
            minimumSize = 15
        )

        assertFalse(
            mix.windowed(3).any { window ->
                window.map { it.artistId }.distinct().size == 1
            }
        )
    }

    @Test
    fun `different seeds regenerate a different order within the same rules`() {
        val first = DailyMixSelector.select(
            familiarTracks = tracks("familiar", 40),
            rediscoveryTracks = tracks("rediscovery", 40),
            explorationTracks = tracks("exploration", 40),
            fallbackTracks = tracks("fallback", 40),
            targetSize = 30,
            minimumSize = 15,
            seed = 1L
        )
        val second = DailyMixSelector.select(
            familiarTracks = tracks("familiar", 40),
            rediscoveryTracks = tracks("rediscovery", 40),
            explorationTracks = tracks("exploration", 40),
            fallbackTracks = tracks("fallback", 40),
            targetSize = 30,
            minimumSize = 15,
            seed = 2L
        )

        assertEquals(30, first.size)
        assertEquals(30, second.size)
        assertEquals(15, second.count { it.id.startsWith("familiar") })
        assertEquals(9, second.count { it.id.startsWith("rediscovery") })
        assertEquals(6, second.count { it.id.startsWith("exploration") })
        assertFalse(first.map { it.id } == second.map { it.id })
    }

    @Test
    fun `deprioritizes tracks played within the last 48 hours`() {
        val now = 1_700_000_000_000L
        val recentlyPlayed = track("familiar-recent", 0).apply {
            lastPlayed = Date(now - 60_000L)
        }
        val untouched = tracks("familiar", 15)

        val mix = DailyMixSelector.select(
            familiarTracks = untouched + recentlyPlayed,
            rediscoveryTracks = tracks("rediscovery", 20),
            explorationTracks = tracks("exploration", 20),
            fallbackTracks = emptyList(),
            targetSize = 30,
            minimumSize = 15,
            now = now
        )

        assertFalse(mix.any { it.id == recentlyPlayed.id })
    }

    @Test
    fun `still includes a recently played track when nothing else is left`() {
        val now = 1_700_000_000_000L
        val recentlyPlayed = track("only-track", 0).apply {
            lastPlayed = Date(now - 60_000L)
        }

        val mix = DailyMixSelector.select(
            familiarTracks = listOf(recentlyPlayed),
            rediscoveryTracks = emptyList(),
            explorationTracks = emptyList(),
            fallbackTracks = emptyList(),
            targetSize = 30,
            minimumSize = 1,
            now = now
        )

        assertEquals(listOf(recentlyPlayed.id), mix.map { it.id })
    }

    private fun tracks(prefix: String, count: Int): List<Track> =
        (1..count).map { track("$prefix-$it", it) }

    private fun track(id: String, index: Int): Track = Track(
        id = id,
        title = id,
        album = "Album $id",
        albumId = "album-$id",
        artist = "Artist $id",
        artistId = "artist-$id"
    )
}
