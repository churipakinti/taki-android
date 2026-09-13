/*
 * TrackListActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.tracklist

import org.moire.ultrasonic.ui.album.TrackContextAction
import org.moire.ultrasonic.ui.album.TrackContextMenuState
import org.moire.ultrasonic.view.SortOrder

/**
 * What the host Fragment needs to wire up from [org.moire.ultrasonic.ui.tracklist.TrackListScreen].
 * Reuses [TrackContextAction]/[TrackContextMenuState] from Compose Album Detail (issue #10 phase
 * 4A) as-is: both destinations dispatch the exact same `R.menu.context_menu_track_collection`
 * eight actions through the same `ContextMenuUtil`/`DownloadUtil` paths, so a parallel enum
 * would only duplicate it. Every callback reaches the unchanged `MediaPlayerManager` /
 * `ContextMenuUtil` / `RxBus` rating pipeline the legacy `TrackCollectionFragment` already used
 * - no playback or queue logic is re-implemented in Compose.
 */
data class TrackListActions(
    val onTrackClick: (TrackListRow) -> Unit,
    val onContextAction: (TrackListRow, TrackContextAction) -> Unit,
    val trackContextMenuState: (TrackListRow) -> TrackContextMenuState,
    /** Only invoked when [TrackListUiState.showHeart] is true. */
    val onHeartToggle: (TrackListRow) -> Unit,
    /** Only invoked when [TrackListUiState.showControls] is true. */
    val onSortOrderSelected: (SortOrder) -> Unit,
    /** The `FilterButtonBar` primary action - "Songs" destination only. */
    val onPlayAll: () -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
    /** Only invoked when [TrackListUiState.headerTitle] is non-null (Genre tracks/Daily Mix,
     *  issue #10 phase 4F2) - the Compose-drawn header's back arrow, exactly like the legacy
     *  `bindLightweightHeader`'s `onBack = { findNavController().navigateUp() }`. */
    val onBack: () -> Unit = {},
) {
    companion object {
        val Noop = TrackListActions(
            onTrackClick = {},
            onContextAction = { _, _ -> },
            trackContextMenuState = { TrackContextMenuState() },
            onHeartToggle = {},
            onSortOrderSelected = {},
            onPlayAll = {},
            onRefresh = {},
            onLoadMore = {},
            onBack = {},
        )
    }
}
