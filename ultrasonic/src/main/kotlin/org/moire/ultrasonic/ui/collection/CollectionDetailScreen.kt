/*
 * CollectionDetailScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.toImmutableList
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.moire.ultrasonic.util.LayoutType

const val COLLECTION_DETAIL_LIST_TEST_TAG = "collection_detail_list"
const val COLLECTION_DETAIL_TOP_BAR_TEST_TAG = "collection_detail_top_bar"

private const val GRID_COLUMNS = 2

/**
 * Collection Detail (issue #10 phase 4B) - the box-set screen, migrated to Compose as a
 * straight presentation swap. It is navigation-only, exactly as the legacy screen was.
 *
 * The header is deliberately compact and structural - it belongs to the same family as Home /
 * Library / Search / Album Detail, not the old Ultrasonic toolbar: a lightweight top row
 * (back + layout toggle + discover) on the dark canvas, a small stacked-cover identity mark
 * (~120dp, not a hero), the grouping title once, one quiet "N discs" line, then the member
 * grid/list. No Play, Shuffle, download or context menu - the legacy screen had none.
 *
 * [layout] is the list/grid toggle state, owned by `CollectionDetailFragment` (the navigation
 * boundary). [bottomContentInset] is the live floating-chrome reserve, applied only to the
 * scrolling area so the fixed header does not gain dead space.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    state: CollectionDetailUiState,
    actions: CollectionDetailActions,
    layout: LayoutType,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = TakiTheme.dimensions.contentInsetFloatingChrome,
) {
    // "Loading" with members already on screen is a pull-to-refresh; the very first resolve
    // uses the 2dp strip instead, so the two never overlap.
    val isRefreshing = state.isLoading && state.hasContent
    val firstLoad = state.isLoading && !state.hasContent

    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CollectionTopBar(actions = actions, layout = layout, discovering = state.isDiscovering)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TakiTheme.spacing.xxs),
            ) {
                if (firstLoad) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
                        color = TakiTheme.colors.accent,
                        trackColor = TakiTheme.colors.surfaceLow,
                    )
                }
            }

            // The header is fixed - it does not scroll away with the disc list, matching the
            // legacy `collection_detail_header` include.
            CollectionHeader(state = state, showCount = !firstLoad)

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
                if (layout == LayoutType.LIST) {
                    CollectionDiscList(state, actions, bottomContentInset)
                } else {
                    CollectionDiscGrid(state, actions, bottomContentInset)
                }
            }
        }
    }
}

/**
 * The lightweight top row: a back affordance, then the layout toggle and the discover action,
 * on the bare Taki canvas - no Material toolbar, no elevation, no fill, no title (the grouping
 * name lives once in the header below). Every glyph keeps a 48dp target (`TakiIconButton`).
 */
@Composable
private fun CollectionTopBar(
    actions: CollectionDetailActions,
    layout: LayoutType,
    discovering: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(COLLECTION_DETAIL_TOP_BAR_TEST_TAG)
            .padding(horizontal = TakiTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TakiIconButton(
            onClick = actions.onBack,
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = stringResource(R.string.common_navigate_back),
            tint = TakiTheme.colors.ivory,
        )
        Spacer(Modifier.weight(1f))
        TakiIconButton(
            onClick = actions.onToggleLayout,
            painter = painterResource(
                if (layout == LayoutType.LIST) {
                    R.drawable.ic_baseline_view_grid
                } else {
                    R.drawable.ic_baseline_view_list
                },
            ),
            contentDescription = stringResource(R.string.collection_toggle_layout),
        )
        TakiIconButton(
            onClick = actions.onDiscoverMore,
            painter = painterResource(R.drawable.ic_menu_refresh),
            contentDescription = stringResource(R.string.collection_discover_more),
            enabled = !discovering,
        )
    }
}

@Composable
private fun CollectionHeader(state: CollectionDetailUiState, showCount: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TakiTheme.spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(TakiTheme.spacing.lg))
        CollectionIdentityMark(state.members)
        Spacer(Modifier.height(TakiTheme.spacing.md))
        Text(
            text = state.title,
            style = TakiTheme.type.hero,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().semantics { heading() },
        )
        if (showCount) {
            Spacer(Modifier.height(TakiTheme.spacing.xs))
            Text(
                text = pluralStringResource(R.plurals.n_discs, state.discCount, state.discCount),
                style = TakiTheme.type.caption,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(TakiTheme.spacing.lg))
    }
}

/**
 * The box-set identity mark: up to three member covers fanned **horizontally** so the cluster
 * reads wider than tall - "a collection of releases", not one album hero. The front (first)
 * cover is centred and fully visible; the other two peek out left and right by one overlap
 * step. Zero elevation, no glow, no container. A one-member (or still-loading) collection shows
 * a single centred cover, no fan; a two-member one shows just two overlapped covers.
 */
@Composable
private fun CollectionIdentityMark(members: List<CollectionMember>) {
    val cover = TakiTheme.dimensions.collectionHeroCover
    val step = TakiTheme.dimensions.collectionHeroOverlapStep
    val covers = members.take(3)

    when (covers.size) {
        0, 1 -> IdentityCover(covers.firstOrNull(), cover)

        2 -> Box(modifier = Modifier.size(width = cover + step, height = cover)) {
            IdentityCover(covers[1], cover, Modifier.align(Alignment.CenterStart))
            IdentityCover(covers[0], cover, Modifier.align(Alignment.CenterEnd))
        }

        else -> Box(modifier = Modifier.size(width = cover + step * 2, height = cover)) {
            IdentityCover(covers[2], cover, Modifier.align(Alignment.CenterStart))
            IdentityCover(covers[1], cover, Modifier.align(Alignment.CenterEnd))
            IdentityCover(covers[0], cover, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun IdentityCover(member: CollectionMember?, size: Dp, modifier: Modifier = Modifier) {
    TakiArtwork(
        model = member?.artworkModel,
        contentDescription = null,
        size = size,
        shape = TakiTheme.shapes.md,
        modifier = modifier,
    )
}

@Composable
private fun CollectionDiscGrid(
    state: CollectionDetailUiState,
    actions: CollectionDetailActions,
    bottomContentInset: Dp,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        state = rememberLazyGridState(),
        modifier = Modifier.fillMaxSize().testTag(COLLECTION_DETAIL_LIST_TEST_TAG),
        contentPadding = PaddingValues(
            start = TakiTheme.spacing.screenHorizontal,
            end = TakiTheme.spacing.screenHorizontal,
            top = TakiTheme.spacing.sm,
            bottom = bottomContentInset,
        ),
        horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
        verticalArrangement = Arrangement.spacedBy(TakiTheme.spacing.lg),
    ) {
        if (state.showEmpty) {
            item(span = { GridItemSpan(maxLineSpan) }) { CollectionEmptyState() }
        } else {
            items(state.members, key = { it.id }) { member ->
                CollectionDiscGridCard(member = member, onClick = { actions.onOpenMember(member) })
            }
        }
    }
}

@Composable
private fun CollectionDiscList(
    state: CollectionDetailUiState,
    actions: CollectionDetailActions,
    bottomContentInset: Dp,
) {
    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.fillMaxSize().testTag(COLLECTION_DETAIL_LIST_TEST_TAG),
        contentPadding = PaddingValues(
            top = TakiTheme.spacing.sm,
            bottom = bottomContentInset,
        ),
    ) {
        if (state.showEmpty) {
            item(key = "empty") { CollectionEmptyState() }
        } else {
            items(state.members, key = { it.id }) { member ->
                CollectionDiscListRow(member = member, onClick = { actions.onOpenMember(member) })
            }
        }
    }
}

@Composable
private fun CollectionDiscGridCard(member: CollectionMember, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            TakiArtwork(
                model = member.artworkModel,
                contentDescription = member.title,
                size = maxWidth,
                shape = TakiTheme.shapes.sm,
            )
        }
        Spacer(Modifier.height(TakiTheme.spacing.sm))
        member.discNumber?.let { disc ->
            Text(
                text = stringResource(R.string.album_disc_header, disc),
                style = TakiTheme.type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(TakiTheme.spacing.textTight))
        }
        // One line only: uneven wrapped titles break the grid rhythm across a large
        // collection; the full title is on the member's Album Detail.
        Text(
            text = member.title,
            style = TakiTheme.type.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        member.trackCount?.let { count ->
            Spacer(Modifier.height(TakiTheme.spacing.textTight))
            Text(
                text = stringResource(R.string.collection_disc_track_count, count),
                style = TakiTheme.type.caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CollectionDiscListRow(member: CollectionMember, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.rowLg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = TakiTheme.spacing.sm, end = TakiTheme.spacing.screenHorizontal)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(TakiTheme.dimensions.trackNumberColumn),
            contentAlignment = Alignment.Center,
        ) {
            member.discNumber?.let { disc ->
                Text(
                    text = disc.toString(),
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.width(TakiTheme.spacing.sm))
        TakiArtwork(
            model = member.artworkModel,
            contentDescription = null,
            size = TakiTheme.dimensions.artworkThumb,
            shape = TakiTheme.shapes.sm,
        )
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.title,
                style = TakiTheme.type.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            member.trackCount?.let { count ->
                Spacer(Modifier.height(TakiTheme.spacing.textTight))
                Text(
                    text = stringResource(R.string.collection_disc_track_count, count),
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CollectionEmptyState() {
    EmptyState(
        icon = painterResource(R.drawable.ic_empty),
        title = stringResource(R.string.search_no_match),
        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
    )
}

private fun previewState(grouping: String, vararg titles: String) = CollectionDetailUiState(
    isLoading = false,
    grouping = grouping,
    title = grouping,
    members = titles.mapIndexed { index, memberTitle ->
        CollectionMember(
            id = (index + 1).toString(),
            parent = null,
            discNumber = index + 1,
            title = memberTitle,
            trackCount = null,
            artworkModel = null,
        )
    }.toImmutableList(),
)

@Preview
@Composable
private fun CollectionDetailScreenPreview() {
    TakiTheme {
        CollectionDetailScreen(
            state = previewState(
                "Bach 333",
                "Cantatas BWV 1-3",
                "Cantatas BWV 4-6",
                "Cantatas BWV 7-9",
                "Cantatas BWV 10-12",
            ),
            actions = CollectionDetailActions.Noop,
            layout = LayoutType.COVER,
        )
    }
}

@Preview
@Composable
private fun CollectionDetailListPreview() {
    TakiTheme {
        CollectionDetailScreen(
            state = previewState("Mercury Living Presence", "Volume 1", "Volume 2"),
            actions = CollectionDetailActions.Noop,
            layout = LayoutType.LIST,
        )
    }
}
