/*
 * AlbumListActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.albumlist

import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.view.SortOrder

/** The context-menu actions the legacy Album List popup menu exposes (issue #10 phase 4E2):
 *  Play Now/Next/Last and Download (hidden when offline, see [AlbumListUiState] via the host).
 *  Unlike Artist List's menu, there is no Start Radio item - `AlbumRowDelegate` explicitly hides
 *  it (`popup.menu.findItem(R.id.menu_start_radio)?.isVisible = false`) for every album row. */
enum class AlbumContextAction { PLAY_NOW, PLAY_NEXT, PLAY_LAST, DOWNLOAD }

/**
 * What the host Fragment needs to wire up from [org.moire.ultrasonic.ui.albumlist.AlbumListScreen].
 * Every callback is dispatched to the unchanged `MediaPlayerManager` / `ContextMenuUtil` /
 * `NavController` paths the legacy `AlbumListFragment` already used - no playback or navigation
 * logic is re-implemented in Compose. [onLoadMore] is the Compose equivalent of the legacy
 * `EndlessScrollListener` - fired whenever the visible scroll position nears the end of the
 * loaded rows; the ViewModel is responsible for ignoring it while a load is already in flight,
 * once there is nothing more to load, or in "by artist" mode (which never pages).
 */
data class AlbumListActions(
    val onEntryClick: (AlbumListRow) -> Unit,
    val onContextAction: (AlbumListRow, AlbumContextAction) -> Unit,
    val onSortOrderSelected: (SortOrder) -> Unit,
    val onLayoutTypeSelected: (LayoutType) -> Unit,
    val onFolderSelected: (String?) -> Unit,
    val onRefresh: () -> Unit,
    val onLoadMore: () -> Unit,
) {
    companion object {
        val Noop = AlbumListActions(
            onEntryClick = {},
            onContextAction = { _, _ -> },
            onSortOrderSelected = {},
            onLayoutTypeSelected = {},
            onFolderSelected = {},
            onRefresh = {},
            onLoadMore = {},
        )
    }
}
