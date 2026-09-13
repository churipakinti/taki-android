/*
 * ArtistListUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artistlist

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.view.SortOrder

/**
 * The immutable, presentation-ready state of the Compose Artist List screen (issue #10 phase
 * 4E1). A projection of what `ArtistListModel` already loads/derives for the legacy
 * `ArtistListFragment` - id3 artists or folder-mode indexes, the album-derived sort orders,
 * and the folder-selector header. Built once in
 * [org.moire.ultrasonic.model.ArtistListViewModel] and never mutated in the UI.
 */
@Immutable
data class ArtistListUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val layoutType: LayoutType = LayoutType.COVER,
    val sortOrder: SortOrder = SortOrder.BY_NAME,
    /** Only the sort orders the legacy filter bar ever actually exposed
     *  (`ArtistListFragment.getListOfSortOrders`), online/offline gated. `RANDOM`, `STARRED`,
     *  `BY_GENRE`, `HIGHEST` and `BY_YEAR` are real, ported, tested derivations
     *  ([org.moire.ultrasonic.model.ArtistListViewModel] supports them) that the legacy screen
     *  never actually let the user pick either - kept for parity/testability, not newly
     *  exposed here (see the phase 4E1 report). */
    val availableSortOrders: ImmutableList<SortOrder> = persistentListOf(),
    /** True only when online on a non-id3 (folder-mode) server, mirroring
     *  `ArtistListModel.showSelectFolderHeader() = true` gated by
     *  `EntryListFragment.showFolderHeader()`'s online/non-id3 check. */
    val showFolderHeader: Boolean = false,
    val folders: ImmutableList<MusicFolder> = persistentListOf(),
    val selectedFolderId: String? = null,
    /** Mirrors `Utils.createPopupMenu`'s `!ActiveServerProvider.isOffline()` gate on the
     *  context menu's Download item. */
    val downloadAvailable: Boolean = true,
    /** Incremented only when the sort order actually changes (not on initial load, a
     *  pull-to-refresh, or a restored/re-applied order) - the screen resets scroll to top when
     *  this changes, mirroring the legacy `resetScrollOnNextUpdate` flag exactly. */
    val scrollResetToken: Int = 0,
    val rows: ImmutableList<ArtistListRow> = persistentListOf(),
) {
    val hasContent: Boolean get() = rows.isNotEmpty()

    /** Nothing to show and not still loading - render the empty state. */
    val showEmpty: Boolean get() = !isLoading && rows.isEmpty()
}

/**
 * One row in the Artist List - either an id3 `Artist` (taps navigate to Artist Detail) or a
 * folder-mode `Index` (taps navigate to folder browsing) - the two are visually identical in
 * the legacy `ArtistRowBinder`/`ArtistGridBinder` row shape; only [isIndex] changes what a tap
 * does, resolved by the host Fragment.
 */
@Immutable
data class ArtistListRow(
    val id: String,
    val name: String,
    val artworkModel: CoverArtRequest?,
    val isIndex: Boolean,
)
