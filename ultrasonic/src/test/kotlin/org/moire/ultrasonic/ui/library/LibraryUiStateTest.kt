/*
 * LibraryUiStateTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LibraryUiState] is deliberately tiny - only Box Sets visibility is real state. */
class LibraryUiStateTest {

    @Test
    fun `default hides Box Sets`() {
        assertFalse(LibraryUiState().boxSetsAvailable)
    }

    @Test
    fun `Box Sets can be marked available`() {
        assertTrue(LibraryUiState(boxSetsAvailable = true).boxSetsAvailable)
    }

    @Test
    fun `copy toggles only Box Sets`() {
        val hidden = LibraryUiState()
        assertTrue(hidden.copy(boxSetsAvailable = true).boxSetsAvailable)
        assertFalse(hidden.copy(boxSetsAvailable = true).copy(boxSetsAvailable = false).boxSetsAvailable)
    }
}
