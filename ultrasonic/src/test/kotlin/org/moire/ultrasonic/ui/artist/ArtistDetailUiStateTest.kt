/*
 * ArtistDetailUiStateTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artist

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Derived-state behaviour of [ArtistDetailUiState] - what the screen branches on. */
class ArtistDetailUiStateTest {

    private fun album(id: String) = ArtistAlbumUi(id, null, "Album $id", "Artist", null)
    private fun track(id: String) = ArtistTrackUi(id, "1", "Track $id", "Album", "3:00", false)

    @Test
    fun `the default state is loading with nothing to show`() {
        val state = ArtistDetailUiState()
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
        assertFalse(state.showEmpty)
        assertFalse(state.showAbout)
    }

    @Test
    fun `albums or tracks count as content`() {
        assertTrue(ArtistDetailUiState(isLoading = false, albums = persistentListOf(album("1"))).hasContent)
        assertTrue(
            ArtistDetailUiState(isLoading = false, popularTracks = persistentListOf(track("1"))).hasContent,
        )
    }

    @Test
    fun `a finished load with no albums and no tracks is the empty state`() {
        val state = ArtistDetailUiState(isLoading = false)
        assertTrue(state.showEmpty)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a still-loading artist is never the empty state`() {
        assertFalse(ArtistDetailUiState(isLoading = true).showEmpty)
    }

    @Test
    fun `the about section shows only for a non-empty biography`() {
        assertFalse(ArtistDetailUiState(biography = null).showAbout)
        assertFalse(ArtistDetailUiState(biography = "").showAbout)
        assertTrue(ArtistDetailUiState(biography = "A band.").showAbout)
    }

    @Test
    fun `the biography toggle appears only past the collapse length`() {
        assertFalse(ArtistDetailUiState(biography = "short").biographyCollapsible)
        assertTrue(
            ArtistDetailUiState(biography = "x".repeat(ARTIST_BIOGRAPHY_COLLAPSE_LENGTH + 1))
                .biographyCollapsible,
        )
    }
}
