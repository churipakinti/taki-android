/*
 * LibraryHubDestination.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.fragment

import android.os.Bundle
import androidx.navigation.NavController
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.library.LibraryHubAction

/** Where a [LibraryHubAction] navigates: a nav-graph destination id and, for "Add", a server id. */
internal data class LibraryHubDestination(val destinationId: Int, val serverId: Int? = null)

/** The exact targets of the legacy Library hub popup (the `NavigationActivity.showLibraryHub`
 *  `when` it replaces): Add opens Edit Server in create mode (`serverId = -1`). */
internal fun libraryHubDestination(action: LibraryHubAction): LibraryHubDestination = when (action) {
    LibraryHubAction.SWITCH -> LibraryHubDestination(R.id.serverSelectorFragment)
    LibraryHubAction.ADD -> LibraryHubDestination(R.id.editServerFragment, serverId = NEW_SERVER_ID)
    LibraryHubAction.SETTINGS -> LibraryHubDestination(R.id.settingsFragment)
    LibraryHubAction.ABOUT -> LibraryHubDestination(R.id.aboutFragment)
}

private const val NEW_SERVER_ID = -1

internal fun NavController.navigateLibraryHub(action: LibraryHubAction) {
    val destination = libraryHubDestination(action)
    val args = destination.serverId?.let { Bundle().apply { putInt("serverId", it) } }
    navigate(destination.destinationId, args)
}
