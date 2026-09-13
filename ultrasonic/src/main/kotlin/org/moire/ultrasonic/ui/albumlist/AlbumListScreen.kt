/*
 * AlbumListScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.albumlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.AlbumShelfItem
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiEntryGrid
import org.moire.ultrasonic.ui.components.TakiEntryRow
import org.moire.ultrasonic.ui.components.TakiFolderSelectorHeader
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiSortMenu
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.view.SortOrder

private const val ALBUM_GRID_COLUMNS = 3

/** Same value as the legacy `EndlessScrollListener.VISIBLE_TRESHOLD`, multiplied by the column
 *  count for the grid layout exactly as `EndlessScrollListener`'s `GridLayoutManager`
 *  constructor did (`treshold *= layoutManager.spanCount`). */
private const val LOAD_MORE_THRESHOLD_ROWS = 7

/** Applied to whichever of the grid/list containers is on screen - lets tests scroll to a row
 *  regardless of the current [LayoutType]. */
const val ALBUM_LIST_CONTENT_TEST_TAG = "album_list_content"

/**
 * The Compose Album List screen (issue #10 phase 4E2): id3/folder-mode album pages (or an
 * artist's full album list when reached "by artist"), a sort-order picker, grid/list toggle,
 * infinite scroll, the folder-selector header (folder-mode servers, alphabetical order only),
 * and a per-row context menu - a 1:1 visual/behavioural port of the legacy `AlbumListFragment`
 * (`FilterButtonBar` + `AlbumRowDelegate`/`AlbumGridDelegate`). This migration is also what
 * retires `list_layout_filterable.xml` - Artist List (phase 4E1) already stopped using it, and
 * this was its last consumer.
 *
 * Deliberately draws **no back button and no visible title**: `NavigationActivity` already hides
 * the shared Material toolbar for `albumListFragment` and shows its shared
 * `content_navigation_header` back-only bar instead (unchanged by this phase, and already true
 * before it), exactly as the legacy screen did.
 */
@Composable
fun AlbumListScreen(
    state: AlbumListUiState,
    actions: AlbumListActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Reserved strip so the first load never shifts the controls row below it.
            Box(
                modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
            ) {
                if (firstLoad) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
                        color = TakiTheme.colors.accent,
                        trackColor = TakiTheme.colors.surfaceLow,
                    )
                }
            }

            AlbumListControls(state, actions)

            if (state.showFolderHeader) {
                TakiFolderSelectorHeader(
                    folders = state.folders,
                    selectedFolderId = state.selectedFolderId,
                    onFolderSelected = actions.onFolderSelected,
                )
            }

            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = actions.onRefresh,
                state = pullState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                        containerColor = TakiTheme.colors.surface,
                        color = TakiTheme.colors.gray,
                    )
                },
            ) {
                if (state.showEmpty) {
                    EmptyState(
                        icon = painterResource(R.drawable.ic_empty),
                        title = stringResource(R.string.select_album_empty),
                        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                    )
                } else {
                    AlbumListContent(state, actions, bottomContentInset)
                }
            }
        }
    }
}

@Composable
private fun AlbumListControls(state: AlbumListUiState, actions: AlbumListActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TakiSortMenu(
            options = state.availableSortOrders,
            selected = state.sortOrder,
            onSelect = actions.onSortOrderSelected,
            label = { stringResource(sortOrderLabelRes(it)) },
        )
        Spacer(Modifier.weight(1f))
        val isGrid = state.layoutType == LayoutType.COVER
        TakiIconButton(
            onClick = {
                actions.onLayoutTypeSelected(if (isGrid) LayoutType.LIST else LayoutType.COVER)
            },
            painter = painterResource(
                if (isGrid) R.drawable.ic_baseline_view_list else R.drawable.ic_baseline_view_grid,
            ),
            contentDescription = stringResource(
                if (isGrid) R.string.list_view else R.string.grid_view,
            ),
        )
    }
}

@Composable
private fun AlbumListContent(
    state: AlbumListUiState,
    actions: AlbumListActions,
    bottomContentInset: Dp,
) {
    when (state.layoutType) {
        LayoutType.COVER -> {
            val gridState = rememberLazyGridState()
            LoadMoreOnScrollNearEnd(gridState, state.rows.size, actions.onLoadMore)
            TakiEntryGrid(
                items = state.rows,
                modifier = Modifier.testTag(ALBUM_LIST_CONTENT_TEST_TAG),
                state = gridState,
                columns = ALBUM_GRID_COLUMNS,
                contentPadding = PaddingValues(
                    start = TakiTheme.spacing.md,
                    end = TakiTheme.spacing.md,
                    top = TakiTheme.spacing.sm,
                    bottom = TakiTheme.spacing.sm + bottomContentInset,
                ),
                key = { it.id },
            ) { row -> AlbumGridCell(row, actions, state.downloadAvailable) }
        }
        LayoutType.LIST -> {
            val listState = rememberLazyListState()
            LoadMoreOnScrollNearEnd(listState, state.rows.size, actions.onLoadMore)
            LazyColumn(
                modifier = Modifier.testTag(ALBUM_LIST_CONTENT_TEST_TAG),
                state = listState,
                contentPadding = PaddingValues(bottom = bottomContentInset),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    AlbumRowItem(row, actions, state.downloadAvailable)
                }
            }
        }
    }
}

/** Fires [onLoadMore] whenever the last visible item comes within
 *  [LOAD_MORE_THRESHOLD_ROWS] * [ALBUM_GRID_COLUMNS] rows of the end, the Compose equivalent of
 *  the legacy `EndlessScrollListener`. The ViewModel (not this composable) is responsible for
 *  ignoring redundant/concurrent/out-of-mode calls, exactly like `EndlessScrollListener`'s own
 *  `loading` guard. */
@Composable
private fun LoadMoreOnScrollNearEnd(state: LazyGridState, totalItems: Int, onLoadMore: () -> Unit) {
    LaunchedEffect(state, totalItems) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                val threshold = LOAD_MORE_THRESHOLD_ROWS * ALBUM_GRID_COLUMNS
                if (totalItems > 0 && lastVisible >= 0 && lastVisible + threshold >= totalItems) {
                    onLoadMore()
                }
            }
    }
}

@Composable
private fun LoadMoreOnScrollNearEnd(state: LazyListState, totalItems: Int, onLoadMore: () -> Unit) {
    LaunchedEffect(state, totalItems) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .collect { lastVisible ->
                if (totalItems > 0 && lastVisible >= 0 && lastVisible + LOAD_MORE_THRESHOLD_ROWS >= totalItems) {
                    onLoadMore()
                }
            }
    }
}

@Composable
private fun AlbumGridCell(
    row: AlbumListRow,
    actions: AlbumListActions,
    downloadAvailable: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        AlbumShelfItem(
            title = row.title,
            subtitle = row.artist,
            artworkModel = row.artworkModel,
            onClick = { actions.onEntryClick(row) },
            onLongClick = { menuExpanded = true },
        )
        AlbumContextMenu(
            expanded = menuExpanded,
            downloadAvailable = downloadAvailable,
            onDismiss = { menuExpanded = false },
            onAction = { action ->
                menuExpanded = false
                actions.onContextAction(row, action)
            },
        )
    }
}

@Composable
private fun AlbumRowItem(
    row: AlbumListRow,
    actions: AlbumListActions,
    downloadAvailable: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        TakiEntryRow(
            title = row.title,
            subtitle = row.artist,
            artworkModel = row.artworkModel,
            placeholder = painterResource(R.drawable.unknown_album),
            onClick = { actions.onEntryClick(row) },
            onLongClick = { menuExpanded = true },
        )
        AlbumContextMenu(
            expanded = menuExpanded,
            downloadAvailable = downloadAvailable,
            onDismiss = { menuExpanded = false },
            onAction = { action ->
                menuExpanded = false
                actions.onContextAction(row, action)
            },
        )
    }
}

@Composable
private fun AlbumContextMenu(
    expanded: Boolean,
    downloadAvailable: Boolean,
    onDismiss: () -> Unit,
    onAction: (AlbumContextAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_play_now)) },
            onClick = { onAction(AlbumContextAction.PLAY_NOW) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_play_next)) },
            onClick = { onAction(AlbumContextAction.PLAY_NEXT) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_play_last)) },
            onClick = { onAction(AlbumContextAction.PLAY_LAST) },
        )
        if (downloadAvailable) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_download)) },
                onClick = { onAction(AlbumContextAction.DOWNLOAD) },
            )
        }
    }
}

/** Mirrors `FilterButtonBar.getStringForSortOrder`. */
private fun sortOrderLabelRes(order: SortOrder): Int = when (order) {
    SortOrder.BY_NAME -> R.string.main_albums_alphaByName
    SortOrder.ALL_SONGS -> R.string.main_songs_all
    SortOrder.RANDOM -> R.string.main_albums_random
    SortOrder.NEWEST -> R.string.main_albums_newest
    SortOrder.HIGHEST -> R.string.main_albums_highest
    SortOrder.FREQUENT -> R.string.main_albums_frequent
    SortOrder.RECENT -> R.string.main_albums_recent
    SortOrder.BY_ARTIST -> R.string.main_albums_alphaByArtist
    SortOrder.BY_GENRE -> R.string.main_albums_byGenre
    SortOrder.STARRED -> R.string.main_albums_starred
    SortOrder.BY_YEAR -> R.string.main_albums_by_year
}
