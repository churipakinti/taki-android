/*
 * UpNextProjectionTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.upnext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pure Up Next projection (issue #10 phase 4K1): what follows the current track in the
 * shuffle-aware play order, with keys that survive duplicates and track transitions, plus the
 * reorder / play-order-position arithmetic the host dispatches to the unchanged runtime.
 */
class UpNextProjectionTest {

    private fun entry(id: String, menu: List<UpNextMenuItem> = emptyList()) =
        QueueEntry(mediaId = id, title = "T-$id", artist = "A-$id", menuItems = menu)

    @Test
    fun `an empty queue has nothing upcoming`() {
        assertTrue(buildUpcoming(emptyList(), -1).isEmpty())
    }

    @Test
    fun `a single current track leaves nothing upcoming`() {
        assertTrue(buildUpcoming(listOf(entry("a")), 0).isEmpty())
    }

    @Test
    fun `the tracks after the current index are upcoming in order with their play-order positions`() {
        val upcoming = buildUpcoming(listOf(entry("a"), entry("b"), entry("c"), entry("d")), 1)
        assertEquals(listOf("T-c", "T-d"), upcoming.map { it.title })
        assertEquals(listOf(2, 3), upcoming.map { it.playOrderIndex })
        assertEquals("A-c", upcoming.first().artist)
    }

    @Test
    fun `with no current track everything is upcoming`() {
        val upcoming = buildUpcoming(listOf(entry("a"), entry("b")), -1)
        assertEquals(listOf(0, 1), upcoming.map { it.playOrderIndex })
    }

    @Test
    fun `the projection follows the given play order, not id order (shuffle-aware)`() {
        // Play order c, a, b: after the current (c) come a then b, whatever the raw order was.
        val upcoming = buildUpcoming(listOf(entry("c"), entry("a"), entry("b")), 0)
        assertEquals(listOf("T-a", "T-b"), upcoming.map { it.title })
    }

    @Test
    fun `duplicate tracks get distinct keys that stay stable when earlier items are played past`() {
        val entries = listOf(entry("x"), entry("y"), entry("x"), entry("x"))
        val atStart = buildUpcoming(entries, 0)
        val afterOne = buildUpcoming(entries, 1)
        assertEquals(atStart.map { it.key }.toSet().size, atStart.size)
        // "y" leaves, but the two later duplicates keep the same keys.
        assertEquals(atStart.drop(1).map { it.key }, afterOne.map { it.key })
    }

    @Test
    fun `advancing the current track drops exactly the row that became current`() {
        val entries = listOf(entry("a"), entry("b"), entry("c"))
        val before = buildUpcoming(entries, 0)
        val after = buildUpcoming(entries, 1)
        assertEquals(before.drop(1).map { it.key }, after.map { it.key })
    }

    @Test
    fun `a removal from the queue shifts later positions down by one`() {
        val before = buildUpcoming(listOf(entry("a"), entry("b"), entry("c"), entry("d")), 0)
        val after = buildUpcoming(listOf(entry("a"), entry("c"), entry("d")), 0)
        assertEquals(listOf(1, 2, 3), before.map { it.playOrderIndex })
        assertEquals(listOf("T-c", "T-d"), after.map { it.title })
        assertEquals(listOf(1, 2), after.map { it.playOrderIndex })
    }

    @Test
    fun `menu items and local-track metadata pass through unchanged`() {
        val menu = listOf(UpNextMenuItem.LYRICS, UpNextMenuItem.FAVORITE)
        val upcoming = buildUpcoming(listOf(entry("a"), entry("b", menu)), 0)
        assertEquals(menu, upcoming.single().menuItems)
    }

    @Test
    fun `moved relocates one item and leaves the rest in order`() {
        assertEquals(listOf("a", "c", "b", "d"), moved(listOf("a", "b", "c", "d"), 1, 2))
        assertEquals(listOf("d", "a", "b", "c"), moved(listOf("a", "b", "c", "d"), 3, 0))
    }

    @Test
    fun `moved is a no-op for an unchanged or out-of-range move`() {
        val list = listOf("a", "b", "c")
        assertSame(list, moved(list, 1, 1))
        assertSame(list, moved(list, -1, 2))
        assertSame(list, moved(list, 0, 3))
    }

    @Test
    fun `an upcoming row maps to the play-order position the runtime expects`() {
        assertEquals(4, playOrderPosition(currentIndex = 2, upcomingIndex = 1))
        assertEquals(0, playOrderPosition(currentIndex = -1, upcomingIndex = 0))
    }

    @Test
    fun `reordering upcoming rows dispatches play-order positions offset by the current index`() {
        // Current is at 2; dragging the first upcoming row (position 3) below the second (4).
        val current = 2
        assertEquals(3 to 4, playOrderPosition(current, 0) to playOrderPosition(current, 1))
    }
}
