/*
 * FolderBrowserScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.folderbrowser

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.album.TrackContextAction
import org.moire.ultrasonic.ui.album.TrackContextMenuState
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiEntryRow
import org.moire.ultrasonic.ui.components.TakiLibraryTrackRow
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

/** Lets tests scroll to a row. */
const val FOLDER_BROWSER_CONTENT_TEST_TAG = "folder_browser_content"

/**
 * The Compose folder/non-ID3 browser (issue #10 phase 4M1) - the last surviving legacy
 * `TrackCollectionFragment` mode. A 1:1 visual/behavioural port of the legacy
 * `list_layout_track` + `AlbumRowDelegate`/`TrackViewBinder` mixed rendering: sub-directory rows
 * and bare track rows in the server's own order, no grouping between them.
 *
 * Always draws its own [TakiScreenHeader] (back + the directory's title): this destination never
 * used the shared Material toolbar or the shared `content_navigation_header` back bar either (it
 * fell through to the plain, title-only `HeaderViewBinder`), so there is no shared chrome to defer
 * to, unlike Track List's headerTitle-null case.
 */
@Composable
fun FolderBrowserScreen(
    state: FolderBrowserUiState,
    actions: FolderBrowserActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    val isRefreshing = state.isLoading && state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TakiScreenHeader(onBack = actions.onBack, title = state.title)

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
                    FolderBrowserContent(state, actions, bottomContentInset)
                }
            }
        }
    }
}

@Composable
private fun FolderBrowserContent(
    state: FolderBrowserUiState,
    actions: FolderBrowserActions,
    bottomContentInset: Dp,
) {
    LazyColumn(
        modifier = Modifier.testTag(FOLDER_BROWSER_CONTENT_TEST_TAG),
        contentPadding = PaddingValues(bottom = bottomContentInset),
    ) {
        items(state.rows, key = { it.id() }) { row ->
            when (row) {
                is FolderBrowserRow.Directory -> TakiEntryRow(
                    title = row.title,
                    subtitle = row.artist,
                    artworkModel = row.artworkModel,
                    onClick = { actions.onDirectoryClick(row) },
                )

                is FolderBrowserRow.Track -> FolderBrowserTrackItem(row, state.online, actions)
            }
        }
    }
}

private fun FolderBrowserRow.id(): String = when (this) {
    is FolderBrowserRow.Directory -> id
    is FolderBrowserRow.Track -> id
}

@Composable
private fun FolderBrowserTrackItem(
    row: FolderBrowserRow.Track,
    showHeart: Boolean,
    actions: FolderBrowserActions,
) {
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
        FolderBrowserTrackContextMenu(
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
 * `R.menu.context_menu_track_collection`. A near-duplicate of Track List's own private
 * `TrackListContextMenu` (not reused directly - same reasoning as that composable's own kdoc:
 * duplicating ~25 lines here is lower-risk than widening either screen's public surface for a
 * dependency it doesn't otherwise need). [TrackContextAction]/[TrackContextMenuState] themselves
 * *are* shared - only this rendering is repeated.
 */
@Composable
private fun FolderBrowserTrackContextMenu(
    expanded: Boolean,
    menuState: TrackContextMenuState,
    onDismiss: () -> Unit,
    onAction: (TrackContextAction) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        FolderBrowserMenuItem(R.string.common_play_now) { onAction(TrackContextAction.PLAY_NOW) }
        FolderBrowserMenuItem(R.string.common_play_next) { onAction(TrackContextAction.PLAY_NEXT) }
        FolderBrowserMenuItem(R.string.common_play_last) { onAction(TrackContextAction.PLAY_LAST) }
        FolderBrowserMenuItem(R.string.common_play_from_here) {
            onAction(TrackContextAction.PLAY_FROM_HERE)
        }
        FolderBrowserMenuItem(R.string.song_start_radio) { onAction(TrackContextAction.START_RADIO) }
        if (menuState.canAddToPlaylist) {
            FolderBrowserMenuItem(R.string.playlist_add_to_title) {
                onAction(TrackContextAction.ADD_TO_PLAYLIST)
            }
        }
        if (menuState.canDownload) {
            FolderBrowserMenuItem(R.string.common_download) { onAction(TrackContextAction.DOWNLOAD) }
        }
        if (menuState.canDelete) {
            FolderBrowserMenuItem(R.string.common_delete) { onAction(TrackContextAction.DELETE) }
        }
    }
}

@Composable
private fun FolderBrowserMenuItem(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(labelRes)) }, onClick = onClick)
}
