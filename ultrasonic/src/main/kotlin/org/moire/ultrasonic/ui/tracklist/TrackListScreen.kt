/*
 * TrackListScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.tracklist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import org.moire.ultrasonic.ui.album.TrackContextAction
import org.moire.ultrasonic.ui.album.TrackContextMenuState
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiFilterChip
import org.moire.ultrasonic.ui.components.TakiLibraryTrackRow
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiSortMenu
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.view.SortOrder

/** Same value as the legacy `EndlessScrollListener.VISIBLE_TRESHOLD` (no grid multiplier - this
 *  screen is list-only, matching `TrackCollectionFragment`'s `ViewCapabilities(supportsGrid =
 *  false)`). */
private const val LOAD_MORE_THRESHOLD_ROWS = 7

/** Lets tests scroll to a row. */
const val TRACK_LIST_CONTENT_TEST_TAG = "track_list_content"

/**
 * The shared Compose Track List screen (issue #10 phase 4F1): backs both the "Songs" destination
 * (`libraryRoot` - a filterable All Songs/Random/By Artist/By Genre/Liked browser with a "Play
 * all" action, exactly like the legacy `FilterButtonBar`'s `songs_action_row`) and the dedicated
 * Liked Songs destination (`getStarred` - a flat liked-only list, no controls row at all). A 1:1
 * visual/behavioural port of the legacy `list_layout_track_filterable`/`list_layout_track` +
 * `LibraryTrackBinder`.
 *
 * Deliberately draws **no back button and no visible title**: `NavigationActivity` already hides
 * the shared Material toolbar for both destinations (`isLibraryTrackCollection`, unchanged by
 * this phase - both were already gated on `libraryRoot`/`getStarred` before it) and shows its
 * shared `content_navigation_header` back-only bar instead, exactly as the legacy screens did.
 */
@Composable
fun TrackListScreen(
    state: TrackListUiState,
    actions: TrackListActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Reserved strip so the first load never shifts the controls row below it.
            Box(modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs)) {
                if (firstLoad) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
                        color = TakiTheme.colors.accent,
                        trackColor = TakiTheme.colors.surfaceLow,
                    )
                }
            }

            if (state.showControls) {
                TrackListControls(state, actions)
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
                    TrackListContent(state, actions, bottomContentInset)
                }
            }
        }
    }
}

@Composable
private fun TrackListControls(state: TrackListUiState, actions: TrackListActions) {
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
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(TakiTheme.spacing.sm))
        TakiFilterChip(
            label = stringResource(R.string.songs_play_all_label),
            selected = false,
            accentWhenSelected = false,
            onClick = actions.onPlayAll,
        )
    }
}

@Composable
private fun TrackListContent(
    state: TrackListUiState,
    actions: TrackListActions,
    bottomContentInset: Dp,
) {
    val listState = rememberLazyListState()
    LoadMoreOnScrollNearEnd(listState, state.rows.size, actions.onLoadMore)
    LazyColumn(
        modifier = Modifier.testTag(TRACK_LIST_CONTENT_TEST_TAG),
        state = listState,
        contentPadding = PaddingValues(bottom = bottomContentInset),
    ) {
        items(state.rows, key = { it.id }) { row ->
            TrackListItem(row, state.showHeart, actions)
        }
    }
}

@Composable
private fun TrackListItem(row: TrackListRow, showHeart: Boolean, actions: TrackListActions) {
    var menuExpanded by remember { mutableStateOf(false) }
    var menuState by remember { mutableStateOf(TrackContextMenuState()) }
    Box {
        TakiLibraryTrackRow(
            title = row.title,
            subtitle = row.subtitle,
            artworkModel = row.artworkModel,
            onClick = { actions.onTrackClick(row) },
            onOpenMenu = {
                menuState = actions.trackContextMenuState(row)
                menuExpanded = true
            },
            showHeart = showHeart,
            liked = row.liked,
            onHeartClick = { actions.onHeartToggle(row) },
        )
        TrackListContextMenu(
            expanded = menuExpanded,
            menuState = menuState,
            onDismiss = { menuExpanded = false },
            onAction = { action ->
                menuExpanded = false
                actions.onContextAction(row, action)
            },
        )
    }
}

/**
 * The per-track long-press/menu-button menu, one-for-one with the legacy
 * `R.menu.context_menu_track_collection`. A near-duplicate of Compose Album Detail's private
 * `AlbumDetailScreen.TrackContextMenu` (not reused directly - that composable is private, and
 * duplicating ~25 lines here is lower-risk than widening Album Detail's public surface for a
 * dependency it doesn't otherwise need). [TrackContextAction]/[TrackContextMenuState] themselves
 * *are* shared (see [TrackListActions]'s kdoc) - only this rendering is repeated.
 */
@Composable
private fun TrackListContextMenu(
    expanded: Boolean,
    menuState: TrackContextMenuState,
    onDismiss: () -> Unit,
    onAction: (TrackContextAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        TrackListContextMenuItem(R.string.common_play_now) { onAction(TrackContextAction.PLAY_NOW) }
        TrackListContextMenuItem(R.string.common_play_next) { onAction(TrackContextAction.PLAY_NEXT) }
        TrackListContextMenuItem(R.string.common_play_last) { onAction(TrackContextAction.PLAY_LAST) }
        TrackListContextMenuItem(R.string.common_play_from_here) {
            onAction(TrackContextAction.PLAY_FROM_HERE)
        }
        TrackListContextMenuItem(R.string.song_start_radio) { onAction(TrackContextAction.START_RADIO) }
        if (menuState.canAddToPlaylist) {
            TrackListContextMenuItem(R.string.playlist_add_to_title) {
                onAction(TrackContextAction.ADD_TO_PLAYLIST)
            }
        }
        if (menuState.canDownload) {
            TrackListContextMenuItem(R.string.common_download) { onAction(TrackContextAction.DOWNLOAD) }
        }
        if (menuState.canDelete) {
            TrackListContextMenuItem(R.string.common_delete) { onAction(TrackContextAction.DELETE) }
        }
    }
}

@Composable
private fun TrackListContextMenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

/** Fires [onLoadMore] whenever the last visible item comes within
 *  [LOAD_MORE_THRESHOLD_ROWS] of the end, the Compose equivalent of the legacy
 *  `EndlessScrollListener`. The ViewModel is responsible for ignoring redundant/concurrent/
 *  out-of-mode calls (mirrors `EndlessScrollListener`'s own `loading` guard). */
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

/** Mirrors `FilterButtonBar.getStringForSortOrder`'s `primaryAction == PLAY_ALL` branch: this
 *  screen's `STARRED` always means "Liked Songs", never "Liked Albums". */
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
    SortOrder.STARRED -> R.string.main_songs_starred
    SortOrder.BY_YEAR -> R.string.main_albums_by_year
}
