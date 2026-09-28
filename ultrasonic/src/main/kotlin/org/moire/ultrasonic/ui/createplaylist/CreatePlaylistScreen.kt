/*
 * CreatePlaylistScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.createplaylist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
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
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiSearchField
import org.moire.ultrasonic.ui.components.TakiSortMenu
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.view.SortOrder

/** Lets tests scroll to a row. */
const val CREATE_PLAYLIST_CONTENT_TEST_TAG = "create_playlist_content"

private const val LOAD_MORE_THRESHOLD_ROWS = 7

/**
 * The Compose Create Playlist screen (issue #10 phase 4M2) - a 1:1 visual/behavioural port of
 * the legacy `create_playlist_editor.xml` + `PlaylistTrackPickerBinder`: a search field, a sort
 * filter (All Songs / Random / By Artist / By Genre), the track picker list, and a fixed bottom
 * bar (selected count + Save). Draws no header of its own - this destination relies on the shared
 * Material toolbar exactly like the legacy screen did (its title is set via `setTitle`, not a
 * Compose-drawn row), so no `NavigationActivity` chrome flag is needed for it.
 */
@Composable
fun CreatePlaylistScreen(
    state: CreatePlaylistUiState,
    actions: CreatePlaylistActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    TakiScaffold(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = bottomContentInset),
        ) {
            TakiSearchField(
                query = state.searchQuery,
                onQueryChange = actions.onSearchQueryChange,
                onSubmit = actions.onSearchSubmit,
                onClear = actions.onSearchClear,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.sm),
                hint = stringResource(R.string.playlist_search_songs),
            )

            TakiSortMenu(
                options = SORT_OPTIONS,
                selected = state.sortOrder,
                onSelect = actions.onSortOrderSelected,
                label = { stringResource(sortOrderLabelRes(it)) },
                modifier = Modifier.padding(horizontal = TakiTheme.spacing.md),
            )

            // Reserved strip so a load never shifts the content below it (same pattern as the
            // shared Track List screen).
            Box(modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs)) {
                if (state.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
                        color = TakiTheme.colors.accent,
                        trackColor = TakiTheme.colors.surfaceLow,
                    )
                }
            }

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (state.showEmpty) {
                    EmptyState(
                        icon = painterResource(R.drawable.ic_empty),
                        title = stringResource(R.string.playlist_no_songs_found),
                        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                    )
                } else {
                    CreatePlaylistContent(state, actions)
                }
            }

            CreatePlaylistBottomBar(state, actions)
        }
    }
}

@Composable
private fun CreatePlaylistContent(state: CreatePlaylistUiState, actions: CreatePlaylistActions) {
    val listState = rememberLazyListState()
    LoadMoreOnScrollNearEnd(listState, state.rows.size, actions.onLoadMore)
    LazyColumn(
        modifier = Modifier.testTag(CREATE_PLAYLIST_CONTENT_TEST_TAG),
        state = listState,
        contentPadding = PaddingValues(bottom = TakiTheme.spacing.md),
    ) {
        items(state.rows, key = { it.id }) { row ->
            CreatePlaylistTrackItem(row, onClick = { actions.onTrackToggle(row) })
        }
    }
}

@Composable
private fun CreatePlaylistTrackItem(row: CreatePlaylistTrackRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = TakiTheme.spacing.md, vertical = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TakiArtwork(
            model = row.artworkModel,
            contentDescription = null,
            size = TakiTheme.dimensions.artworkThumb,
            shape = TakiTheme.shapes.sm,
            placeholder = painterResource(R.drawable.unknown_album),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = TakiTheme.spacing.md),
        ) {
            Text(
                text = row.title,
                style = TakiTheme.type.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.subtitle.isNotEmpty()) {
                Text(
                    text = row.subtitle,
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Checkbox(
            checked = row.selected,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = TakiTheme.colors.accent),
        )
    }
}

@Composable
private fun CreatePlaylistBottomBar(state: CreatePlaylistUiState, actions: CreatePlaylistActions) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.rowMd)
            .background(TakiTheme.colors.surfaceHigh)
            .padding(horizontal = TakiTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(
                R.plurals.playlist_selected_songs,
                state.selectedCount,
                state.selectedCount,
            ),
            style = TakiTheme.type.body,
            color = TakiTheme.colors.gray,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(TakiTheme.dimensions.iconMd),
                color = TakiTheme.colors.accent,
            )
        } else {
            Button(onClick = actions.onSave, enabled = state.saveEnabled) {
                Text(stringResource(R.string.common_save))
            }
        }
    }
}

/** Fires [onLoadMore] whenever the last visible item comes within [LOAD_MORE_THRESHOLD_ROWS] of
 *  the end - the Compose equivalent of the legacy `EndlessScrollListener`. */
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

private val SORT_OPTIONS = listOf(
    SortOrder.ALL_SONGS,
    SortOrder.RANDOM,
    SortOrder.BY_ARTIST,
    SortOrder.BY_GENRE,
)

private fun sortOrderLabelRes(order: SortOrder): Int = when (order) {
    SortOrder.ALL_SONGS -> R.string.main_songs_all
    SortOrder.RANDOM -> R.string.main_albums_random
    SortOrder.BY_ARTIST -> R.string.main_albums_alphaByArtist
    SortOrder.BY_GENRE -> R.string.main_albums_byGenre
    else -> R.string.main_songs_all
}
