/*
 * ArtistListScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artistlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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

private const val ARTIST_GRID_COLUMNS = 3

/** Applied to whichever of the grid/list containers is on screen - lets tests scroll to a row
 *  regardless of the current [LayoutType]. */
const val ARTIST_LIST_CONTENT_TEST_TAG = "artist_list_content"

/**
 * The Compose Artist List screen (issue #10 phase 4E1): id3 artists or folder-mode indexes, a
 * sort-order picker, grid/list toggle, the folder-selector header for non-id3 servers, and a
 * per-row context menu - a 1:1 visual/behavioural port of the legacy `ArtistListFragment`
 * (`list_layout_filterable` + `FilterButtonBar` + `ArtistRowBinder`/`ArtistGridBinder`).
 *
 * Deliberately draws **no back button and no visible title**: `NavigationActivity` already
 * hides the shared Material toolbar for `artistListFragment` and shows its shared
 * `content_navigation_header` back-only bar instead (unchanged by this phase - see the phase
 * 4E1 report), exactly as the legacy screen did.
 */
@Composable
fun ArtistListScreen(
    state: ArtistListUiState,
    actions: ArtistListActions,
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

            ArtistListControls(state, actions)

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
                        title = stringResource(R.string.search_no_match),
                        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                    )
                } else {
                    ArtistListContent(state, actions, bottomContentInset)
                }
            }
        }
    }
}

@Composable
private fun ArtistListControls(state: ArtistListUiState, actions: ArtistListActions) {
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
private fun ArtistListContent(
    state: ArtistListUiState,
    actions: ArtistListActions,
    bottomContentInset: Dp,
) {
    when (state.layoutType) {
        LayoutType.COVER -> {
            val gridState = rememberLazyGridState()
            LaunchedEffect(state.scrollResetToken) { gridState.scrollToItem(0) }
            TakiEntryGrid(
                items = state.rows,
                modifier = Modifier.testTag(ARTIST_LIST_CONTENT_TEST_TAG),
                state = gridState,
                columns = ARTIST_GRID_COLUMNS,
                contentPadding = PaddingValues(
                    start = TakiTheme.spacing.md,
                    end = TakiTheme.spacing.md,
                    top = TakiTheme.spacing.sm,
                    bottom = TakiTheme.spacing.sm + bottomContentInset,
                ),
                key = { it.id },
            ) { row -> ArtistGridCell(row, actions, state.downloadAvailable) }
        }
        LayoutType.LIST -> {
            val listState = rememberLazyListState()
            LaunchedEffect(state.scrollResetToken) { listState.scrollToItem(0) }
            LazyColumn(
                modifier = Modifier.testTag(ARTIST_LIST_CONTENT_TEST_TAG),
                state = listState,
                contentPadding = PaddingValues(bottom = bottomContentInset),
            ) {
                items(state.rows, key = { it.id }) { row ->
                    ArtistRowItem(row, actions, state.downloadAvailable)
                }
            }
        }
    }
}

@Composable
private fun ArtistGridCell(
    row: ArtistListRow,
    actions: ArtistListActions,
    downloadAvailable: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        AlbumShelfItem(
            title = row.name,
            subtitle = "",
            artworkModel = row.artworkModel,
            onClick = { actions.onEntryClick(row) },
            onLongClick = { menuExpanded = true },
        )
        ArtistContextMenu(
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
private fun ArtistRowItem(
    row: ArtistListRow,
    actions: ArtistListActions,
    downloadAvailable: Boolean,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        TakiEntryRow(
            title = row.name,
            artworkModel = row.artworkModel,
            placeholder = painterResource(R.drawable.artist_placeholder_icon),
            onClick = { actions.onEntryClick(row) },
            onLongClick = { menuExpanded = true },
        )
        ArtistContextMenu(
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
private fun ArtistContextMenu(
    expanded: Boolean,
    downloadAvailable: Boolean,
    onDismiss: () -> Unit,
    onAction: (ArtistContextAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_play_now)) },
            onClick = { onAction(ArtistContextAction.PLAY_NOW) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_play_next)) },
            onClick = { onAction(ArtistContextAction.PLAY_NEXT) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.common_play_last)) },
            onClick = { onAction(ArtistContextAction.PLAY_LAST) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.song_start_radio)) },
            onClick = { onAction(ArtistContextAction.START_RADIO) },
        )
        if (downloadAvailable) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.common_download)) },
                onClick = { onAction(ArtistContextAction.DOWNLOAD) },
            )
        }
    }
}

/** Mirrors `FilterButtonBar.getStringForSortOrder`, with the same `BY_NAME` ->
 *  `main_artists_alphaByName` override `ArtistListFragment.viewCapabilities` applies. */
private fun sortOrderLabelRes(order: SortOrder): Int = when (order) {
    SortOrder.BY_NAME -> R.string.main_artists_alphaByName
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
