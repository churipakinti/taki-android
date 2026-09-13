/*
 * AlbumListUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.albumlist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.view.SortOrder

/**
 * The immutable, presentation-ready state of the Compose Album List screen (issue #10 phase
 * 4E2). A projection of what the legacy `AlbumListModel`/`AlbumListFragment` already
 * loads/derives: an id3 `getAlbumList2` or folder-mode `getAlbumList` page (or, when reached
 * "by artist", the artist's full album list), the sort orders the `FilterButtonBar` exposed for
 * the current mode, and the folder-selector header - shown only for folder-mode servers that are
 * *currently sorted alphabetically*, exactly matching `AlbumListModel.showSelectFolderHeader()`
 * (unlike Artist List, this is not a plain online/non-id3 check). Built once in
 * [org.moire.ultrasonic.model.AlbumListViewModel] and never mutated in the UI.
 *
 * Deliberately has no scroll-reset-on-sort-change field: the legacy `AlbumListFragment` has no
 * such mechanism either (`RecyclerView.submitList`'s DiffUtil update never repositions the
 * scroll offset), so none is invented here.
 */
@Immutable
data class AlbumListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val layoutType: LayoutType = LayoutType.COVER,
    val sortOrder: SortOrder = SortOrder.BY_NAME,
    /** Only the sort orders the legacy `FilterButtonBar` for this screen actually exposed -
     *  either `AlbumListFragment.getListOfSortOrders()` (online/offline/id3 gated), or, when
     *  reached "by artist" (an artist's own album list), the fixed `[BY_NAME, BY_YEAR]` capability
     *  override `AlbumListFragment.setupFilterBar()` applies for that mode. */
    val availableSortOrders: ImmutableList<SortOrder> = persistentListOf(),
    /** True only when online, folder-mode (non-id3), and the current order is `BY_NAME` or
     *  `BY_ARTIST` - mirrors `AlbumListModel.showSelectFolderHeader()`'s alphabetical-only gate
     *  exactly. Never true in "by artist" mode (that path never sets the type that gates it). */
    val showFolderHeader: Boolean = false,
    val folders: ImmutableList<MusicFolder> = persistentListOf(),
    val selectedFolderId: String? = null,
    /** Mirrors `Utils.createPopupMenu`'s `!ActiveServerProvider.isOffline()` gate on the
     *  context menu's Download item. */
    val downloadAvailable: Boolean = true,
    val rows: ImmutableList<AlbumListRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One row in the Album List - a 1:1 projection of the legacy `AlbumRowBinder`/`AlbumGridBinder`
 * binding (`item.title` / `item.artist` / cover art), both of which show the artist subtitle in
 * grid *and* list layout (unlike Artist List's grid, which has no subtitle).
 */
@Immutable
data class AlbumListRow(
    val id: String,
    val title: String,
    val artist: String,
    val artworkModel: CoverArtRequest?,
)
