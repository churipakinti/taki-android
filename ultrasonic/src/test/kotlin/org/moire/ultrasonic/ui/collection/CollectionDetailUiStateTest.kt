/*
 * CollectionDetailUiStateTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collection

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Derived-state behaviour of [CollectionDetailUiState] - what the screen branches on. */
class CollectionDetailUiStateTest {

    private fun member(id: String) =
        CollectionMember(id = id, parent = null, discNumber = null, title = "Disc $id", trackCount = null, artworkModel = null)

    @Test
    fun `the default state is loading with nothing to show`() {
        val state = CollectionDetailUiState()
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
        assertFalse(state.showEmpty)
        assertEquals(0, state.discCount)
    }

    @Test
    fun `a resolved collection with members has content and is not empty`() {
        val state = CollectionDetailUiState(
            isLoading = false,
            members = persistentListOf(member("1"), member("2"), member("3")),
        )
        assertTrue(state.hasContent)
        assertFalse(state.showEmpty)
        assertEquals(3, state.discCount)
    }

    @Test
    fun `a finished resolve with no members is the empty state`() {
        val state = CollectionDetailUiState(isLoading = false, members = persistentListOf())
        assertTrue(state.showEmpty)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a still-loading collection is never the empty state`() {
        assertFalse(CollectionDetailUiState(isLoading = true).showEmpty)
    }

    @Test
    fun `discovering is independent of loading and content`() {
        val state = CollectionDetailUiState(
            isLoading = false,
            isDiscovering = true,
            members = persistentListOf(member("1")),
        )
        assertTrue(state.isDiscovering)
        assertTrue(state.hasContent)
        assertFalse(state.showEmpty)
    }
}
