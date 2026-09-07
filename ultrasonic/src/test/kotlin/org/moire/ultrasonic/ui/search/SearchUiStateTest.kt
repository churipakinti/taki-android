/*
 * SearchUiStateTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.search

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure derived behaviour of [SearchUiState] - the body the Search screen shows. */
class SearchUiStateTest {

    private fun artist(id: String) = SearchArtistUi(id, "Artist $id", isIndex = false)

    @Test
    fun `default is the recent-searches body with the prompt`() {
        val state = SearchUiState()
        assertTrue(state.showRecentSearches)
        assertTrue(state.showPrompt)
        assertFalse(state.hasResults)
        assertFalse(state.showNoResults)
    }

    @Test
    fun `recent searches present hides the prompt but keeps the recent body`() {
        val state = SearchUiState(recentSearches = persistentListOf("bach"))
        assertTrue(state.showRecentSearches)
        assertFalse(state.showPrompt)
    }

    @Test
    fun `a non-blank query leaves the recent body`() {
        assertFalse(SearchUiState(query = "ba").showRecentSearches)
    }

    @Test
    fun `results present means hasResults and not no-results`() {
        val state = SearchUiState(
            query = "bach",
            submitted = true,
            artists = persistentListOf(artist("1")),
        )
        assertTrue(state.hasResults)
        assertFalse(state.showNoResults)
    }

    @Test
    fun `a finished empty search shows no-results`() {
        val state = SearchUiState(query = "zzz", submitted = true, isSearching = false)
        assertTrue(state.showNoResults)
    }

    @Test
    fun `a search still running never shows no-results`() {
        val state = SearchUiState(query = "zzz", submitted = true, isSearching = true)
        assertFalse(state.showNoResults)
    }

    @Test
    fun `an unsubmitted non-blank query does not show no-results yet`() {
        assertFalse(SearchUiState(query = "z", submitted = false).showNoResults)
    }
}
