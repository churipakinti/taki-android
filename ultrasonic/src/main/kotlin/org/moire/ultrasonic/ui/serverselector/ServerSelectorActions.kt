/*
 * ServerSelectorActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.serverselector

/** Callbacks [ServerSelectorScreen] needs from its host. */
data class ServerSelectorActions(
    val onBack: () -> Unit,
    val onServerClick: (ServerSelectorRow) -> Unit,
    val onAddServer: () -> Unit,
    val onEditServer: (ServerSelectorRow) -> Unit,
    val onDeleteRequested: (ServerSelectorRow) -> Unit,
    val onDeleteConfirm: () -> Unit,
    val onDeleteCancel: () -> Unit,
) {
    companion object {
        val Noop = ServerSelectorActions(
            onBack = {},
            onServerClick = {},
            onAddServer = {},
            onEditServer = {},
            onDeleteRequested = {},
            onDeleteConfirm = {},
            onDeleteCancel = {},
        )
    }
}
