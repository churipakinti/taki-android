/*
 * PlaylistDetailActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import org.moire.ultrasonic.ui.album.TrackContextAction
import org.moire.ultrasonic.ui.album.TrackContextMenuState

/**
 * The navigation + playback callbacks the Compose Playlist Detail screen fires. Every one is
 * implemented in `TrackCollectionFragment` (the host / navigation + playback boundary fixed in
 * issue #8); the screen itself owns no `NavController`, no `MediaPlayerManager`, no `RxBus`.
 * Reuses [TrackContextAction]/[TrackContextMenuState] from Album Detail/Track List as-is (issue
 * #10 phase 4A/4F1) plus one new shared case, [TrackContextAction.REMOVE_FROM_PLAYLIST] - a
 * parallel enum would only duplicate the other eight actions this screen shares with every other
 * track-context menu in the app.
 */
class PlaylistDetailActions(
    /** Play the whole playlist from the top (`InsertionMode.CLEAR`, autoplay). */
    val onPlay: () -> Unit,
    /** Play the whole playlist shuffled. */
    val onShuffle: () -> Unit,
    /** Download every track of the playlist (the header's overflow menu, reusing the legacy
     *  `showPlaylistHeaderMenu` dialog). */
    val onShowHeaderMenu: () -> Unit,
    /** Play from this track, keeping the rest of the playlist queued after it. */
    val onTrackClick: (trackId: String) -> Unit,
    /** A per-track long-press action (legacy `R.menu.context_menu_track_collection_playlist`
     *  parity - the base track menu plus "Remove from playlist"). Routed to the unchanged
     *  `ContextMenuUtil` / add-to-playlist / remove-from-playlist paths in the host. */
    val onTrackContextAction: (trackId: String, action: TrackContextAction) -> Unit,
    /** Resolves which conditional context items a track should show, at menu-open time (a point
     *  read of the download state + offline flag, exactly like the legacy
     *  `Utils.createPopupMenu`). */
    val trackContextMenuState: (trackId: String) -> TrackContextMenuState,
    /** Pull-to-refresh: re-fetch the playlist from the server. Always a real network call -
     *  see [PlaylistDetailUiState]'s kdoc on why there is no cache to bypass. */
    val onRefresh: () -> Unit,
) {
    companion object {
        val Noop = PlaylistDetailActions(
            onPlay = {},
            onShuffle = {},
            onShowHeaderMenu = {},
            onTrackClick = {},
            onTrackContextAction = { _, _ -> },
            trackContextMenuState = { TrackContextMenuState() },
            onRefresh = {},
        )
    }
}
