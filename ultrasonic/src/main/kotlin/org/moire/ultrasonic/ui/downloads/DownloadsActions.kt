/*
 * DownloadsActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.downloads

/**
 * Callbacks [DownloadsScreen] needs from its host. [onRemoveClick] is the legacy row's trash
 * button: it removes that album's downloaded tracks immediately - the legacy screen never had a
 * confirmation step, and none is added here (parity first).
 */
data class DownloadsActions(
    val onAlbumClick: (DownloadedAlbumRow) -> Unit,
    val onRemoveClick: (DownloadedAlbumRow) -> Unit,
    val onRefresh: () -> Unit,
) {
    companion object {
        val Noop = DownloadsActions(
            onAlbumClick = {},
            onRemoveClick = {},
            onRefresh = {},
        )
    }
}
