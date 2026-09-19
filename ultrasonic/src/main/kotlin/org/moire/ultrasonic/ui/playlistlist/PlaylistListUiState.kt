/*
 * PlaylistListUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.util.LayoutType

/**
 * The immutable, presentation-ready state of the Compose Playlists List screen (issue #10
 * phase 4G1). A projection of what the legacy `PlaylistsFragment` already loads/derives:
 * `MusicService.getPlaylists`, a per-playlist async track-count/download-status resolve
 * (`resolveDownloadStates`), and the grid/list toggle the legacy `FilterButtonBar` exposed -
 * **no sort orders** (`ViewCapabilities(supportedSortOrders = emptyList())`), unlike Album/Artist
 * List. Built in [org.moire.ultrasonic.model.PlaylistListViewModel]; the download-status fields
 * are the one part that keeps mutating reactively after the initial load (RxBus-driven), exactly
 * like the legacy screen's own two-stage `CHECKING` -> resolved flow.
 */
@Immutable
data class PlaylistListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val layoutType: LayoutType = LayoutType.LIST,
    /** `!ActiveServerProvider.isOffline()` - gates the create-playlist row/tile (legacy
     *  `PlaylistAdapter.getCount()`'s `+1` only when online) and four of the six context-menu
     *  items (`select_playlist_context` vs `select_playlist_context_offline` - Info/Download/
     *  Update Information/Delete are online-only; Play Now/Play Shuffled remain either way). */
    val online: Boolean = true,
    val rows: ImmutableList<PlaylistListRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One playlist row/card. [songCount] mirrors the legacy `bindTrackCount`'s own fallback exactly:
 * the real resolved track count once `resolveDownloadStates` finishes for this playlist, or the
 * server's own (occasionally stale) `Playlist.songCount` metadata field until then.
 */
@Immutable
data class PlaylistListRow(
    val id: String,
    val name: String,
    val songCount: Int,
    /** The representative cover (first resolved track with non-blank art), or null before
     *  resolution finishes or when no track has one - see the legacy `bindPlaylistCover`'s own
     *  kdoc on why one representative cover was chosen over a collage. */
    val artworkModel: CoverArtRequest?,
    val downloadStatus: PlaylistRowDownloadStatus = PlaylistRowDownloadStatus.CHECKING,
)

/** 1:1 with the legacy `PlaylistsFragment.PlaylistDownloadStatus`. */
enum class PlaylistRowDownloadStatus {
    CHECKING,
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    PARTIAL,
    FAILED,
    EMPTY,
    REMOVING,
}
