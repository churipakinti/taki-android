/*
 * LibraryHubNavigationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.library.LibraryHubAction

/** The Library hub's targets are exactly the legacy popup's `when` (issue #10 phase 5A6). */
class LibraryHubNavigationTest {

    @Test
    fun `Switch collection opens the server selector`() {
        val destination = libraryHubDestination(LibraryHubAction.SWITCH)
        assertEquals(R.id.serverSelectorFragment, destination.destinationId)
        assertNull(destination.serverId)
    }

    @Test
    fun `Add collection opens Edit Server in create mode`() {
        val destination = libraryHubDestination(LibraryHubAction.ADD)
        assertEquals(R.id.editServerFragment, destination.destinationId)
        assertEquals(-1, destination.serverId)
    }

    @Test
    fun `Settings and About open their own screens`() {
        assertEquals(R.id.settingsFragment, libraryHubDestination(LibraryHubAction.SETTINGS).destinationId)
        assertEquals(R.id.aboutFragment, libraryHubDestination(LibraryHubAction.ABOUT).destinationId)
        assertNull(libraryHubDestination(LibraryHubAction.SETTINGS).serverId)
        assertNull(libraryHubDestination(LibraryHubAction.ABOUT).serverId)
    }

    @Test
    fun `every action has a destination`() {
        LibraryHubAction.values().forEach { libraryHubDestination(it) }
    }
}
