/*
 * CollectionListScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collectionlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiEntryGrid
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiScreenHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

private const val COLLECTION_GRID_COLUMNS = 2

/** Same 82%-of-container card size as the legacy `view_stacked_artwork.xml`'s three
 *  `MaterialCardView`s. */
private const val STACK_CARD_FRACTION = 0.82f

/** Lets tests find the scrollable content regardless of loading/empty state. */
const val COLLECTION_LIST_CONTENT_TEST_TAG = "collection_list_content"

/**
 * Box Sets list (post-issue-#10 residual migration, phase 5A1): a fixed 2-column grid of
 * stacked-cover collection cards, pull-to-refresh, and the empty state - a 1:1 visual/
 * behavioural port of the legacy `CollectionListFragment` (`RecyclerView` + `GridLayoutManager
 * (2)` + `CollectionRowAdapter`). Draws its own [TakiScreenHeader] (back + "Box Sets"), exactly
 * as the legacy Fragment's Compose-only header already did.
 */
@Composable
fun CollectionListScreen(
    state: CollectionListUiState,
    actions: CollectionListActions,
    bottomContentInset: Dp,
    modifier: Modifier = Modifier,
) {
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TakiScreenHeader(onBack = actions.onBack, title = stringResource(R.string.library_box_sets))

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
                        title = stringResource(R.string.collection_empty),
                        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
                    )
                } else {
                    val gridState = rememberLazyGridState()
                    TakiEntryGrid(
                        items = state.rows,
                        modifier = Modifier.testTag(COLLECTION_LIST_CONTENT_TEST_TAG),
                        state = gridState,
                        columns = COLLECTION_GRID_COLUMNS,
                        contentPadding = PaddingValues(
                            start = TakiTheme.spacing.md,
                            end = TakiTheme.spacing.md,
                            top = TakiTheme.spacing.sm,
                            bottom = TakiTheme.spacing.sm + bottomContentInset,
                        ),
                        key = { it.id },
                    ) { row -> CollectionGridCell(row, onClick = { actions.onCollectionClick(row) }) }
                }
            }
        }
    }
}

@Composable
private fun CollectionGridCell(row: CollectionListRow, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        CollectionStackedArtwork(row = row, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
            text = row.title,
            style = TakiTheme.type.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = TakiTheme.spacing.sm),
        )
        Text(
            text = pluralStringResource(R.plurals.n_discs, row.albumCount, row.albumCount),
            style = TakiTheme.type.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The diagonal 3-cover stack: back (top-start), middle (centre), front (bottom-end), each 82%
 * of the square container - a straight geometric port of the legacy `view_stacked_artwork.xml`'s
 * percent-bias `ConstraintLayout` (bias 0/0.5/1 on both axes). Only the layers the Collection
 * actually has are drawn ([CollectionListRow.albumCount]), exactly like the legacy
 * `StackedArtworkBinder`'s `back.isVisible = albumCount > 2` / `middle.isVisible = albumCount >
 * 1` - a single-cover Collection shows one clean cover, never 3 copies of the same image.
 */
@Composable
private fun CollectionStackedArtwork(row: CollectionListRow, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier) {
        val cardSize = maxWidth * STACK_CARD_FRACTION
        if (row.albumCount > 2) {
            TakiArtwork(
                model = row.covers.getOrNull(2),
                contentDescription = null,
                size = cardSize,
                shape = TakiTheme.shapes.sm,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
        if (row.albumCount > 1) {
            TakiArtwork(
                model = row.covers.getOrNull(1),
                contentDescription = null,
                size = cardSize,
                shape = TakiTheme.shapes.sm,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        TakiArtwork(
            model = row.covers.getOrNull(0),
            contentDescription = null,
            size = cardSize,
            shape = TakiTheme.shapes.sm,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}
