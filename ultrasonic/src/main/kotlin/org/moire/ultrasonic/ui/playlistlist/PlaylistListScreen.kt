/*
 * PlaylistListScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiEntryGrid
import org.moire.ultrasonic.ui.components.TakiEntryRow
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType

private const val PLAYLIST_GRID_COLUMNS = 2
private const val CREATE_ROW_KEY = "create_playlist"

/** Lets tests scroll to a row regardless of the current [LayoutType]. */
const val PLAYLIST_LIST_CONTENT_TEST_TAG = "playlist_list_content"

/**
 * The Compose Playlists List screen (issue #10 phase 4G1): playlist rows/cards (name, song
 * count, download status), a grid/list toggle (**no sort menu** - the legacy `FilterButtonBar`
 * never exposed one here), the create-playlist tile, a per-playlist context menu, and the empty
 * state - a 1:1 visual/behavioural port of the legacy `PlaylistsFragment` (`GridView` +
 * `PlaylistAdapter`).
 *
 * Deliberately draws **no back button and no visible title**: `NavigationActivity` already hides
 * the shared Material toolbar for `playlistsFragment` and shows its shared
 * `content_navigation_header` back-only bar instead (unchanged by this phase, and already true
 * before it), exactly as the legacy screen did.
 */
@Composable
fun PlaylistListScreen(
    state: PlaylistListUiState,
    actions: PlaylistListActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs)) {
                if (firstLoad) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
                        color = TakiTheme.colors.accent,
                        trackColor = TakiTheme.colors.surfaceLow,
                    )
                }
            }

            PlaylistListControls(state, actions)

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
                        title = stringResource(R.string.select_playlist_empty),
                        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                    )
                } else {
                    PlaylistListContent(state, actions, bottomContentInset)
                }
            }
        }
    }
}

@Composable
private fun PlaylistListControls(state: PlaylistListUiState, actions: PlaylistListActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = pluralStringResource(R.plurals.n_playlists, state.rows.size, state.rows.size),
            style = TakiTheme.type.caption,
        )
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
private fun PlaylistListContent(
    state: PlaylistListUiState,
    actions: PlaylistListActions,
    bottomContentInset: Dp,
) {
    when (state.layoutType) {
        LayoutType.COVER -> {
            val gridState = rememberLazyGridState()
            TakiEntryGrid(
                items = gridItems(state),
                modifier = Modifier.testTag(PLAYLIST_LIST_CONTENT_TEST_TAG),
                state = gridState,
                columns = PLAYLIST_GRID_COLUMNS,
                contentPadding = PaddingValues(
                    start = TakiTheme.spacing.md,
                    end = TakiTheme.spacing.md,
                    top = TakiTheme.spacing.sm,
                    bottom = TakiTheme.spacing.sm + bottomContentInset,
                ),
                key = { it.key() },
            ) { entry ->
                when (entry) {
                    is PlaylistGridEntry.Create -> CreatePlaylistGridCell(actions.onCreatePlaylist)
                    is PlaylistGridEntry.Row -> PlaylistGridCell(entry.row, state.online, actions)
                }
            }
        }
        LayoutType.LIST -> {
            val listState = rememberLazyListState()
            LazyColumn(
                modifier = Modifier.testTag(PLAYLIST_LIST_CONTENT_TEST_TAG),
                state = listState,
                contentPadding = PaddingValues(bottom = bottomContentInset),
            ) {
                playlistListItems(state, actions)
            }
        }
    }
}

private sealed interface PlaylistGridEntry {
    fun key(): Any

    data class Row(val row: PlaylistListRow) : PlaylistGridEntry {
        override fun key() = row.id
    }

    data object Create : PlaylistGridEntry {
        override fun key() = CREATE_ROW_KEY
    }
}

private fun gridItems(state: PlaylistListUiState): List<PlaylistGridEntry> = buildList {
    state.rows.forEach { add(PlaylistGridEntry.Row(it)) }
    if (state.online) add(PlaylistGridEntry.Create)
}

private fun LazyListScope.playlistListItems(state: PlaylistListUiState, actions: PlaylistListActions) {
    items(state.rows, key = { it.id }) { row -> PlaylistRowItem(row, state.online, actions) }
    if (state.online) {
        item(key = CREATE_ROW_KEY) { CreatePlaylistRow(actions.onCreatePlaylist) }
    }
}

@Composable
private fun PlaylistRowItem(row: PlaylistListRow, online: Boolean, actions: PlaylistListActions) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        TakiEntryRow(
            title = row.name,
            artworkModel = row.artworkModel,
            subtitle = pluralStringResource(R.plurals.n_songs, row.songCount, row.songCount),
            caption = downloadStatusLabel(row.downloadStatus),
            placeholder = painterResource(R.drawable.ic_menu_playlists),
            onClick = { actions.onEntryClick(row) },
            onLongClick = { menuExpanded = true },
            trailing = { PlaylistRowTrailing(row.downloadStatus) { menuExpanded = true } },
        )
        PlaylistContextMenu(
            expanded = menuExpanded,
            online = online,
            downloadStatus = row.downloadStatus,
            onDismiss = { menuExpanded = false },
            onAction = { action ->
                menuExpanded = false
                actions.onContextAction(row, action)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistGridCell(row: PlaylistListRow, online: Boolean, actions: PlaylistListActions) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cardWidth = TakiTheme.dimensions.artworkCard
    Column(
        modifier = Modifier
            .width(cardWidth)
            .combinedClickable(
                role = Role.Button,
                onClick = { actions.onEntryClick(row) },
                onLongClick = { menuExpanded = true },
            ),
    ) {
        TakiArtwork(
            model = row.artworkModel,
            contentDescription = null,
            size = cardWidth,
            shape = TakiTheme.shapes.sm,
            placeholder = painterResource(R.drawable.ic_menu_playlists),
        )
        Row(
            modifier = Modifier.width(cardWidth).padding(top = TakiTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.name,
                    style = TakiTheme.type.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_songs, row.songCount, row.songCount),
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                downloadStatusLabel(row.downloadStatus)?.let {
                    Text(text = it, style = TakiTheme.type.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            PlaylistRowTrailing(row.downloadStatus) { menuExpanded = true }
        }
        Box {
            PlaylistContextMenu(
                expanded = menuExpanded,
                online = online,
                downloadStatus = row.downloadStatus,
                onDismiss = { menuExpanded = false },
                onAction = { action ->
                    menuExpanded = false
                    actions.onContextAction(row, action)
                },
            )
        }
    }
}

@Composable
private fun PlaylistRowTrailing(status: PlaylistRowDownloadStatus, onOpenMenu: () -> Unit) {
    val busy = status == PlaylistRowDownloadStatus.CHECKING ||
        status == PlaylistRowDownloadStatus.DOWNLOADING ||
        status == PlaylistRowDownloadStatus.REMOVING
    if (busy) {
        CircularProgressIndicator(
            modifier = Modifier.size(TakiTheme.dimensions.iconSm),
            color = TakiTheme.colors.gray,
        )
    } else {
        TakiIconButton(
            onClick = onOpenMenu,
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.playlist_menu_description),
            iconSize = TakiTheme.dimensions.iconSm,
        )
    }
}

@Composable
private fun PlaylistContextMenu(
    expanded: Boolean,
    online: Boolean,
    downloadStatus: PlaylistRowDownloadStatus,
    onDismiss: () -> Unit,
    onAction: (PlaylistContextAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (online) {
            ContextMenuItem(R.string.common_info) { onAction(PlaylistContextAction.INFO) }
        }
        ContextMenuItem(R.string.common_play_now) { onAction(PlaylistContextAction.PLAY_NOW) }
        ContextMenuItem(R.string.common_play_shuffled) { onAction(PlaylistContextAction.PLAY_SHUFFLED) }
        if (online) {
            ContextMenuItem(
                if (downloadStatus == PlaylistRowDownloadStatus.DOWNLOADED) {
                    R.string.playlist_remove_download_title
                } else {
                    R.string.common_download
                },
            ) { onAction(PlaylistContextAction.DOWNLOAD) }
            ContextMenuItem(R.string.playlist_update_info) { onAction(PlaylistContextAction.UPDATE_INFO) }
            ContextMenuItem(R.string.common_delete) { onAction(PlaylistContextAction.DELETE) }
        }
    }
}

@Composable
private fun ContextMenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}

/** The legacy `bindDownloadStatus`'s status line: hidden for the two states that communicate
 *  nothing actionable (never downloaded, still checking). */
@Composable
private fun downloadStatusLabel(status: PlaylistRowDownloadStatus): String? = when (status) {
    PlaylistRowDownloadStatus.NOT_DOWNLOADED, PlaylistRowDownloadStatus.CHECKING -> null
    PlaylistRowDownloadStatus.DOWNLOADING -> stringResource(R.string.playlist_status_downloading)
    PlaylistRowDownloadStatus.DOWNLOADED -> stringResource(R.string.playlist_status_downloaded)
    PlaylistRowDownloadStatus.PARTIAL -> stringResource(R.string.playlist_status_partial)
    PlaylistRowDownloadStatus.FAILED -> stringResource(R.string.playlist_status_failed)
    PlaylistRowDownloadStatus.EMPTY -> stringResource(R.string.playlist_status_empty)
    PlaylistRowDownloadStatus.REMOVING -> stringResource(R.string.playlist_status_removing)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreatePlaylistRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = null)
            .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_add_white),
            contentDescription = null,
            tint = TakiTheme.colors.gray,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
        Text(
            text = stringResource(R.string.playlist_create),
            style = TakiTheme.type.titleSmall,
            modifier = Modifier.padding(start = TakiTheme.spacing.sm),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreatePlaylistGridCell(onClick: () -> Unit) {
    val cardWidth = TakiTheme.dimensions.artworkCard
    Column(
        modifier = Modifier
            .width(cardWidth)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = null),
    ) {
        Box(
            modifier = Modifier.size(cardWidth),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_white),
                contentDescription = null,
                tint = TakiTheme.colors.gray,
                modifier = Modifier.size(TakiTheme.dimensions.iconLg),
            )
        }
        Text(
            text = stringResource(R.string.playlist_create),
            style = TakiTheme.type.titleSmall,
            modifier = Modifier.padding(top = TakiTheme.spacing.sm),
        )
    }
}
