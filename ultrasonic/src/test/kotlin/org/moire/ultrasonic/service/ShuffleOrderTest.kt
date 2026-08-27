/*
 * ShuffleOrderTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [createShuffleListFromAnchor] produces the Media3 `ShuffleOrder` index array used by
 * PlaybackService. It has two modes:
 *
 *  - `anchor = currentMediaItemIndex` (Now Playing shuffle toggle / session restore): keep
 *    `0..anchor` in natural order, shuffle only what comes after. `result[0]` stays `0`.
 *  - `anchor = -1` (fresh Album / collection "Shuffle"): shuffle every position, so `result[0]`
 *    - the first window Media3 plays - is random.
 *
 * All assertions use a seeded [Random] so nothing depends on real randomness.
 */
class ShuffleOrderTest {

    private val seeds = (0L until 50L)

    @Test
    fun `anchor -1 always yields a full permutation`() {
        for (seed in seeds) {
            val order = createShuffleListFromAnchor(anchorIndex = -1, length = 8, random = Random(seed))
            assertEquals(
                "seed=$seed must be a permutation of 0..7",
                (0..7).toSet(),
                order.toSet()
            )
        }
    }

    @Test
    fun `anchor -1 does not pin index 0 - the first window can be a non-zero track`() {
        val firstWindows = seeds.map {
            createShuffleListFromAnchor(anchorIndex = -1, length = 8, random = Random(it))[0]
        }.toSet()

        assertTrue(
            "reshuffleAll must be able to start from a non-zero track, saw only $firstWindows",
            firstWindows.any { it != 0 }
        )
        assertTrue("first window is always a valid index", firstWindows.all { it in 0..7 })
    }

    @Test
    fun `anchor -1 with a fixed seed is deterministic`() {
        val a = createShuffleListFromAnchor(anchorIndex = -1, length = 8, random = Random(12345L))
        val b = createShuffleListFromAnchor(anchorIndex = -1, length = 8, random = Random(12345L))
        assertEquals(a.toList(), b.toList())
    }

    @Test
    fun `a non-negative anchor keeps the prefix in natural order and shuffles only the tail`() {
        for (seed in seeds) {
            val order = createShuffleListFromAnchor(anchorIndex = 3, length = 8, random = Random(seed))

            assertEquals(
                "seed=$seed: positions 0..3 stay pinned (Now Playing toggle semantics)",
                listOf(0, 1, 2, 3),
                order.toList().subList(0, 4)
            )
            assertEquals(
                "seed=$seed: still a full permutation",
                (0..7).toSet(),
                order.toSet()
            )
            assertEquals(
                "seed=$seed: only 4..7 are reordered",
                setOf(4, 5, 6, 7),
                order.toList().subList(4, 8).toSet()
            )
        }
    }

    @Test
    fun `anchor at the last index leaves the order untouched`() {
        val order = createShuffleListFromAnchor(anchorIndex = 4, length = 5, random = Random(1L))
        assertEquals(listOf(0, 1, 2, 3, 4), order.toList())
    }

    @Test
    fun `length of one is handled for both modes`() {
        assertEquals(listOf(0), createShuffleListFromAnchor(-1, 1, Random(1L)).toList())
        assertEquals(listOf(0), createShuffleListFromAnchor(0, 1, Random(1L)).toList())
    }
}
