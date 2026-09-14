/*
 * PlaylistDetailUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * The immutable, presentation-ready state of the Compose Playlist Detail screen (issue #10
 * phase 4F3). A specialized sibling of [org.moire.ultrasonic.ui.album.AlbumDetailUiState] -
 * `TrackCollectionFragment`'s legacy `navArgs.playlistId != null` mode already reused the exact
 * same hero binder (`AlbumDetailHeaderBinder`) as Album Detail, so this mirrors its shape
 * closely, minus what a playlist never had: disc grouping, notes, the server-favourite star, the
 * info sheet, and a clickable artist line (`AlbumDetailHeaderBinder`'s `onArtistClick`/
 * `onInfoAction`/`onToggleStar` were all `null` for playlists - see the phase 4F3 report's audit
 * section). [rows] carries no disc markers - a playlist is always a flat, ordered list, unlike a
 * multi-disc album.
 *
 * Unlike Album Detail, there is **no load-once caching**: `CachedMusicService.getPlaylist`
 * passes straight through to the network on every call (no `refresh` parameter even exists on
 * the legacy `TrackCollectionModel.getPlaylist`), so [org.moire.ultrasonic.model.PlaylistDetailViewModel.load]
 * always re-fetches, exactly like the legacy screen did on every `onViewCreated`.
 */
@Immutable
data class PlaylistDetailUiState(
    val isLoading: Boolean = true,
    /** The track list came back empty or the load failed - the screen shows the same
     *  "No matches" state the legacy `emptyView` shows for a playlist with nothing playable. */
    val loadFailed: Boolean = false,
    val playlistId: String? = null,
    val title: String = "",
    /** Single track artist, or the "Various Artists" label - always shown, exactly like Album
     *  Detail's own derivation (a playlist's tracks almost never share one artist). */
    val artist: String = "",
    val year: String? = null,
    val songCount: Int = 0,
    /** Pre-formatted total running time (`Util.formatTotalDuration`), or null when unknown. */
    val totalDuration: String? = null,
    /** Coil model for the hero cover (the first track that actually has one - see
     *  `PlaylistsFragment.bindPlaylistCover`'s own comment on why a single representative cover
     *  beats a collage), or null for the neutral placeholder. */
    val artworkModel: CoverArtRequest? = null,
    /** Whether "Start radio" is offered in the per-track context menu (hidden offline, like the
     *  legacy `song_menu_start_radio` visibility). */
    val radioAvailable: Boolean = true,
    val rows: ImmutableList<PlaylistDetailRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One track row - a playlist is always flat (no [org.moire.ultrasonic.ui.album.AlbumDetailRow.Disc]
 * equivalent), in the server's playlist order.
 */
@Immutable
data class PlaylistDetailRow(
    val id: String,
    /** Display track number (position in the playlist), or null to hide the number column
     *  (matches `Settings.SHOULD_SHOW_TRACK_NUMBER`, same as Album Detail). */
    val number: String?,
    val title: String,
    /** Always shown for playlists (`TrackCollectionFragment`'s `showArtist = { _ -> true }` for
     *  `!navArgs.isAlbum`), unlike Album Detail where it is conditional on multiple artists. */
    val artist: String?,
    val duration: String?,
    val isVideo: Boolean,
)

/**
 * What the host needs to open the Playlist Detail load. Interpreted from the unchanged
 * `trackCollectionFragment` nav arguments by `TrackCollectionFragment`.
 */
@Immutable
data class PlaylistDetailArgs(
    val playlistId: String,
    val playlistName: String,
    /** `!ActiveServerProvider.isOffline()` at open time - whether "Start radio" and the
     *  playlist header's download/rename/delete menu are offered (computed by the host so the
     *  ViewModel stays free of the server provider). */
    val radioAvailable: Boolean = true,
)
