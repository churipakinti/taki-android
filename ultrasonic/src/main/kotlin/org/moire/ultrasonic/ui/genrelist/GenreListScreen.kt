/*
 * GenreListScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.genrelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiEntryGrid
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.theme.TakiTheme

private const val GENRE_GRID_COLUMNS = 2

/** The legacy `genre_cover_container`'s `android:rotation="10"` - a small decorative tilt on
 *  the representative cover, matching the "leaning photo" card look exactly. */
private const val GENRE_COVER_ROTATION_DEGREES = 10f

/** Lets tests find the scrollable content regardless of loading/empty state. */
const val GENRE_LIST_CONTENT_TEST_TAG = "genre_list_content"

/**
 * The Compose Genres List screen (issue #10 phase 4G2): a fixed 2-column grid of genre cards
 * (name + representative track cover), pull-to-refresh, and the empty state - a 1:1 visual/
 * behavioural port of the legacy `SelectGenreFragment` (`RecyclerView` + `GridLayoutManager(2)`
 * + `GenreAdapter`). No sort menu, no list/grid toggle, no context menu, no create action - the
 * legacy screen never had any of these.
 *
 * Deliberately draws **no back button and no visible title**: `NavigationActivity` already hides
 * the shared Material toolbar for `selectGenreFragment` and shows its shared
 * `content_navigation_header` back-only bar instead (unchanged by this phase, and already true
 * before it), exactly as the legacy screen did.
 */
@Composable
fun GenreListScreen(
    state: GenreListUiState,
    actions: GenreListActions,
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
                        title = stringResource(R.string.select_genre_empty),
                        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                    )
                } else {
                    val gridState = rememberLazyGridState()
                    TakiEntryGrid(
                        items = state.rows,
                        modifier = Modifier.testTag(GENRE_LIST_CONTENT_TEST_TAG),
                        state = gridState,
                        columns = GENRE_GRID_COLUMNS,
                        contentPadding = PaddingValues(
                            start = TakiTheme.spacing.md,
                            end = TakiTheme.spacing.md,
                            top = TakiTheme.spacing.sm,
                            bottom = TakiTheme.spacing.sm + bottomContentInset,
                        ),
                        key = { it.name },
                    ) { row -> GenreGridCell(row, actions, state.coverGeneration) }
                }
            }
        }
    }
}

/** `GenreAdapter.onBindViewHolder`'s bind-driven cover fetch, translated to Compose: firing once
 *  when this cell first enters composition (i.e. is scrolled into view), not eagerly for every
 *  genre up front - [GenreListActions.onCoverNeeded] itself de-dupes repeat calls, matching the
 *  legacy Fragment's own `requestedCovers` set. Also re-firing when [coverGeneration] changes -
 *  a refresh clears the ViewModel's resolved-cover map, and a still-composed cell (one that
 *  never left the screen) needs a new request too, exactly like `notifyDataSetChanged()` forcing
 *  every bound legacy row to rebind. */
@Composable
private fun GenreGridCell(row: GenreListRow, actions: GenreListActions, coverGeneration: Int) {
    LaunchedEffect(row.name, coverGeneration) { actions.onCoverNeeded(row.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.genreCardHeight)
            .clip(TakiTheme.shapes.xs)
            .background(TakiTheme.colors.surfaceHigh)
            .clickable(role = Role.Button) { actions.onGenreClick(row) }
            .padding(horizontal = TakiTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.name,
            style = TakiTheme.type.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = TakiTheme.spacing.sm),
        )
        TakiArtwork(
            model = row.artworkModel,
            contentDescription = null,
            size = TakiTheme.dimensions.genreCoverSize,
            shape = TakiTheme.shapes.xs,
            // The legacy `genre_placeholder` is a <layer-list> (a neutral rectangle + this same
            // centred icon), which `painterResource` cannot load (vector/raster only) - TakiArtwork
            // already draws that neutral background + centred icon composition itself, so its own
            // icon-only vector placeholder reproduces the same final look.
            placeholder = painterResource(R.drawable.genre_placeholder_icon),
            modifier = Modifier.graphicsLayer { rotationZ = GENRE_COVER_ROTATION_DEGREES },
        )
    }
}
