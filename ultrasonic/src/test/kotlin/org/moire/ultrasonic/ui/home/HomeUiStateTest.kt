/*
 * HomeUiStateTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure behaviour of [HomeUiState] and [greetingForHour] - folds in what the old
 * `HomeFragment.updateEmptyState()` and `setGreeting()` did.
 */
class HomeUiStateTest {

    private fun album(id: String) = HomeAlbumUi(id, "T$id", "A$id", null, true, null)

    @Test
    fun `greeting boundaries match the old constants`() {
        assertEquals(HomeGreeting.MORNING, greetingForHour(0))
        assertEquals(HomeGreeting.MORNING, greetingForHour(11))
        assertEquals(HomeGreeting.AFTERNOON, greetingForHour(12))
        assertEquals(HomeGreeting.AFTERNOON, greetingForHour(17))
        assertEquals(HomeGreeting.EVENING, greetingForHour(18))
        assertEquals(HomeGreeting.EVENING, greetingForHour(23))
    }

    @Test
    fun `a loaded state with nothing anywhere is empty`() {
        val state = HomeUiState(isLoading = false)
        assertTrue(state.isEmpty)
        assertFalse(state.hasContent)
    }

    @Test
    fun `still loading is never reported as empty`() {
        assertFalse(HomeUiState(isLoading = true).isEmpty)
    }

    @Test
    fun `a single non-empty shelf counts as content`() {
        val state = HomeUiState(
            isLoading = false,
            shelves = persistentListOf(
                HomeShelfUi(HomeShelfKind.LIKED, persistentListOf()),
                HomeShelfUi(HomeShelfKind.NEWEST, persistentListOf(album("1"))),
            ),
        )
        assertFalse(state.isEmpty)
        assertTrue(state.hasContent)
    }

    @Test
    fun `the featured mix alone counts as content`() {
        val state = HomeUiState(isLoading = false, featuredMix = FeaturedMixUi(3, null))
        assertFalse(state.isEmpty)
        assertTrue(state.hasContent)
    }

    @Test
    fun `recently played alone counts as content`() {
        val state = HomeUiState(isLoading = false, recentlyPlayed = persistentListOf(album("1")))
        assertFalse(state.isEmpty)
    }
}
