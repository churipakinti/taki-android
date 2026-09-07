/*
 * AlbumDetailActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

/**
 * The navigation + playback callbacks the Compose Album Detail screen fires. Every one is
 * implemented in `TrackCollectionFragment` (the host / navigation + playback boundary fixed
 * in issue #8); the screen itself owns no `NavController`, no `MediaPlayerManager`, no
 * `RxBus`. Mirrors `HomeActions` / `LibraryActions` / `SearchActions`.
 */
class AlbumDetailActions(
    /** Play the whole album from the top (`InsertionMode.CLEAR`, autoplay). */
    val onPlay: () -> Unit,
    /** Play the whole album shuffled. */
    val onShuffle: () -> Unit,
    /** Optimistic star/unstar of the album through the shared `RatingManager` pipeline
     *  (issue #15). Called with the new intended state. */
    val onToggleStar: (Boolean) -> Unit,
    /** Download every track of the album. */
    val onDownload: () -> Unit,
    /** Open the album-notes bottom sheet (only offered once notes resolved). */
    val onShowInfo: () -> Unit,
    /** Navigate to the album artist's detail screen (issue #16). */
    val onArtistClick: () -> Unit,
    /** A secondary overflow action (issue #16 parity). */
    val onOverflowItem: (AlbumOverflowItem) -> Unit,
    /** Play from this track, keeping the rest of the album queued after it. */
    val onTrackClick: (trackId: String) -> Unit,
    /** A per-track long-press action (legacy `R.menu.context_menu_track_collection` parity).
     *  Routed to the unchanged `ContextMenuUtil` / add-to-playlist paths in the host. */
    val onTrackContextAction: (trackId: String, action: TrackContextAction) -> Unit,
    /** Resolves which conditional context items a track should show, at menu-open time
     *  (a point read of the download state + offline flag). */
    val trackContextMenuState: (trackId: String) -> TrackContextMenuState,
    /** Play a single disc from its header (multi-disc albums). */
    val onDiscPlay: (discNumber: Int) -> Unit,
    /** Download a single disc from its header. */
    val onDiscDownload: (discNumber: Int) -> Unit,
    /** Pull-to-refresh: re-fetch the album from the server. */
    val onRefresh: () -> Unit,
) {
    companion object {
        val Noop = AlbumDetailActions(
            onPlay = {},
            onShuffle = {},
            onToggleStar = {},
            onDownload = {},
            onShowInfo = {},
            onArtistClick = {},
            onOverflowItem = {},
            onTrackClick = {},
            onTrackContextAction = { _, _ -> },
            trackContextMenuState = { TrackContextMenuState() },
            onDiscPlay = {},
            onDiscDownload = {},
            onRefresh = {},
        )
    }
}
