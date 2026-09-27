/*
 * FolderBrowserUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.folderbrowser

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * The immutable, presentation-ready state of the Compose folder/non-ID3 browser (issue #10 phase
 * 4M1) - the last surviving legacy `TrackCollectionFragment` mode (`isAlbum=false`, no
 * `playlistId`, not a library-track-rows mode). Reached only from Compose Artist List's "Index"
 * row tap (`ArtistListFragment.onEntryClick`, `row.isIndex`) in folder-tagged (non-ID3) servers -
 * every deeper directory tap from here routes straight to the already-Compose Album Detail
 * (`isAlbum=true`, `shouldUseComposeAlbumDetail`), which has handled further sub-folder nesting
 * since phase 4H1. This screen therefore only ever needs to render one level.
 *
 * A 1:1 projection of the legacy `getLiveData`'s `getMusicDirectory` branch +
 * `AlbumRowDelegate`/`TrackViewBinder` mixed rendering, not another Album List: the directory's
 * children are not necessarily ID3 albums.
 */
@Immutable
data class FolderBrowserUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    /** The current directory's display name (`navArgs.name`), shown in this screen's own
     *  [org.moire.ultrasonic.ui.components.TakiScreenHeader] - this destination never used the
     *  shared toolbar (`NavigationActivity.hidesSupportActionBar`'s `isLibraryTrackCollection`/
     *  `isAlbumDetail` don't cover it, so it fell through to the plain `HeaderViewBinder` title
     *  in the legacy screen; same content, Compose-drawn here instead). */
    val title: String = "",
    /** Whether a per-track heart is offered (legacy `showRating = true` for this mode, hidden
     *  offline exactly like `TrackViewHolder.setSong`'s `!showRating || isOffline`). */
    val online: Boolean = true,
    val rows: ImmutableList<FolderBrowserRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One row - either a sub-directory (rendered as the legacy `AlbumRowDelegate` did: title, artist,
 * cover art, tap-only - the legacy folder row's long-press menu always resolved to an empty track
 * list and did nothing, exactly like `AlbumDetailRow.Folder`'s own precedent, so no context menu
 * is offered here either) or a bare track (the legacy `TrackViewBinder`/`list_item_track` row:
 * title, "artist · album" subtitle, cover art, a heart when online, and the full track context
 * menu).
 */
@Immutable
sealed interface FolderBrowserRow {

    @Immutable
    data class Directory(
        val id: String,
        val title: String,
        val artist: String?,
        val artworkModel: CoverArtRequest?,
        /** The legacy `onItemClick`'s `item.parent` - passed through unchanged as the next
         *  screen's `parentId` nav arg, exactly like the legacy directory tap. */
        val parent: String?,
    ) : FolderBrowserRow

    @Immutable
    data class Track(
        val id: String,
        val title: String,
        val subtitle: String,
        val artworkModel: CoverArtRequest?,
        val liked: Boolean = false,
    ) : FolderBrowserRow
}

/** What the host `TrackCollectionFragment` needs to open the folder browser - the unchanged
 *  `trackCollectionFragment` nav arguments for this mode, interpreted the same way the legacy
 *  `getLiveData` did. */
@Immutable
data class FolderBrowserArgs(
    val id: String,
    val name: String?,
    val refresh: Boolean = false,
    /** Whether a per-track heart is offered (legacy `showRating = true` for this mode, hidden
     *  offline exactly like `TrackViewHolder.setSong`'s `!showRating || isOffline`). */
    val online: Boolean = true,
)
