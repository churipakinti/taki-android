/*
 * PlaylistListActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import org.moire.ultrasonic.util.LayoutType

/**
 * The per-playlist context-menu actions, one-for-one with the legacy
 * `R.menu.select_playlist_context` (`select_playlist_context_offline` is the same six minus
 * [INFO]/[DOWNLOAD]/[UPDATE_INFO]/[DELETE] - see [PlaylistListUiState.online]).
 * [DOWNLOAD]'s label ("Download" vs "Remove download") is resolved by the host from the row's
 * own [PlaylistRowDownloadStatus], exactly like the legacy `handleDownloadAction`.
 */
enum class PlaylistContextAction { INFO, PLAY_NOW, PLAY_SHUFFLED, DOWNLOAD, UPDATE_INFO, DELETE }

/**
 * What the host Fragment needs to wire up from
 * [org.moire.ultrasonic.ui.playlistlist.PlaylistListScreen]. Every callback is dispatched to the
 * unchanged `NavController` / `MusicService` / `DownloadUtil` / legacy dialog paths the legacy
 * `PlaylistsFragment` already used - no playback, navigation or mutation logic is re-implemented
 * in Compose.
 */
data class PlaylistListActions(
    /** Tap a row - navigate to Playlist Detail. */
    val onEntryClick: (PlaylistListRow) -> Unit,
    val onContextAction: (PlaylistListRow, PlaylistContextAction) -> Unit,
    val onLayoutTypeSelected: (LayoutType) -> Unit,
    /** The create-playlist row/tile (online only) - opens the legacy name dialog. */
    val onCreatePlaylist: () -> Unit,
    val onRefresh: () -> Unit,
) {
    companion object {
        val Noop = PlaylistListActions(
            onEntryClick = {},
            onContextAction = { _, _ -> },
            onLayoutTypeSelected = {},
            onCreatePlaylist = {},
            onRefresh = {},
        )
    }
}
