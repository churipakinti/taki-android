/*
 * FolderBrowserActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.folderbrowser

import org.moire.ultrasonic.ui.album.TrackContextAction
import org.moire.ultrasonic.ui.album.TrackContextMenuState

/**
 * What the host Fragment needs to wire up from [FolderBrowserScreen]. Reuses
 * [TrackContextAction]/[TrackContextMenuState] from Compose Album Detail/Track List as-is - this
 * mode dispatches the exact same `R.menu.context_menu_track_collection` eight actions through the
 * same `ContextMenuUtil`/`DownloadUtil` paths (issue #10 phase 4M1). Every callback reaches the
 * unchanged `MediaPlayerManager`/`ContextMenuUtil`/`RxBus` rating pipeline the legacy
 * `TrackCollectionFragment` already used - no playback or queue logic is re-implemented here.
 */
data class FolderBrowserActions(
    val onDirectoryClick: (FolderBrowserRow.Directory) -> Unit,
    val onTrackClick: (FolderBrowserRow.Track) -> Unit,
    val onContextAction: (FolderBrowserRow.Track, TrackContextAction) -> Unit,
    val trackContextMenuState: (FolderBrowserRow.Track) -> TrackContextMenuState,
    /** Only invoked when the row's server is online (see [FolderBrowserArgs.online]). */
    val onHeartToggle: (FolderBrowserRow.Track) -> Unit,
    val onRefresh: () -> Unit,
    val onBack: () -> Unit,
) {
    companion object {
        val Noop = FolderBrowserActions(
            onDirectoryClick = {},
            onTrackClick = {},
            onContextAction = { _, _ -> },
            trackContextMenuState = { TrackContextMenuState() },
            onHeartToggle = {},
            onRefresh = {},
            onBack = {},
        )
    }
}
