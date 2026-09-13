/*
 * ArtistListActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artistlist

import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.view.SortOrder

/** The context-menu actions the legacy `context_menu_artist` menu exposes (issue #10 phase
 *  4E1): Play Now/Next/Last, Start Radio (a silent no-op for a folder-mode `Index` row - the
 *  legacy `ContextMenuUtil.handleContextMenu`'s `if (!isArtist) return false` never showed a
 *  toast either, so this preserves that exactly rather than hiding the item for indexes) and
 *  Download (hidden when offline, see [ArtistListUiState] via the host). */
enum class ArtistContextAction { PLAY_NOW, PLAY_NEXT, PLAY_LAST, START_RADIO, DOWNLOAD }

/**
 * What the host Fragment needs to wire up from [org.moire.ultrasonic.ui.artistlist.ArtistListScreen].
 * Every callback is dispatched to the unchanged `MediaPlayerManager` / `ArtistRadioQueueBuilder`
 * / `DownloadUtil` / `ContextMenuUtil` / `NavController` paths the legacy `ArtistListFragment`
 * already used - no playback or navigation logic is re-implemented in Compose.
 */
data class ArtistListActions(
    val onEntryClick: (ArtistListRow) -> Unit,
    val onContextAction: (ArtistListRow, ArtistContextAction) -> Unit,
    val onSortOrderSelected: (SortOrder) -> Unit,
    val onLayoutTypeSelected: (LayoutType) -> Unit,
    val onFolderSelected: (String?) -> Unit,
    val onRefresh: () -> Unit,
) {
    companion object {
        val Noop = ArtistListActions(
            onEntryClick = {},
            onContextAction = { _, _ -> },
            onSortOrderSelected = {},
            onLayoutTypeSelected = {},
            onFolderSelected = {},
            onRefresh = {},
        )
    }
}
