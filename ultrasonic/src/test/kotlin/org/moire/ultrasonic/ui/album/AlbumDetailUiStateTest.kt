/*
 * AlbumDetailUiStateTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Derived-state behaviour of [AlbumDetailUiState] - what the screen branches on. */
class AlbumDetailUiStateTest {

    private fun track(id: String) = AlbumDetailRow.Track(id, null, "T $id", null, "3:00", false)

    @Test
    fun `the default state is loading with nothing to show`() {
        val state = AlbumDetailUiState()
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
        assertFalse(state.showEmpty)
        assertFalse(state.infoAvailable)
    }

    @Test
    fun `a loaded album with rows has content and is not empty`() {
        val state = AlbumDetailUiState(
            isLoading = false,
            rows = persistentListOf(track("1"), track("2")),
        )
        assertTrue(state.hasContent)
        assertFalse(state.showEmpty)
    }

    @Test
    fun `a finished load with no rows is the empty state`() {
        val state = AlbumDetailUiState(isLoading = false, rows = persistentListOf())
        assertTrue(state.showEmpty)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a still-loading album is never the empty state`() {
        assertFalse(AlbumDetailUiState(isLoading = true).showEmpty)
    }

    @Test
    fun `the info action turns on only once non-empty notes arrive`() {
        assertFalse(AlbumDetailUiState(notes = null).infoAvailable)
        assertFalse(AlbumDetailUiState(notes = "").infoAvailable)
        assertTrue(AlbumDetailUiState(notes = "A landmark 1955 session.").infoAvailable)
    }

    @Test
    fun `a disc row and a track row are distinct row types`() {
        val rows = persistentListOf<AlbumDetailRow>(AlbumDetailRow.Disc(1), track("1"))
        assertEquals(1, rows.filterIsInstance<AlbumDetailRow.Disc>().size)
        assertEquals(1, rows.filterIsInstance<AlbumDetailRow.Track>().size)
    }

    @Test
    fun `radio availability defaults on and can be turned off for offline`() {
        assertTrue(AlbumDetailUiState().radioAvailable)
        assertFalse(AlbumDetailUiState(radioAvailable = false).radioAvailable)
    }
}
