/*
 * HomeScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.AlbumShelfItem
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.FeaturedMixCard
import org.moire.ultrasonic.ui.components.TakiFilterChip
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiSectionHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

/**
 * The canonical Taki V2 Home composition (TAKI_DESIGN_SYSTEM_V2.md section 10): editorial and
 * modular, not a grid of identical cards. First viewport = greeting, then the daily-mix
 * featured card ("music worth playing now"), then quiet quick-access, then artwork-first
 * shelves. 16dp gutter, 24dp section rhythm, chrome recedes, artwork carries the colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
    // Live reserve so the last item clears whatever floating chrome (bottom nav + mini-player)
    // is currently visible; the host feeds NavigationActivity.contentBottomInset here. The
    // static token is only the fallback for previews / tests.
    bottomContentInset: Dp = TakiTheme.dimensions.contentInsetFloatingChrome,
) {
    TakiScaffold(modifier = modifier) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(HOME_LIST_TEST_TAG),
                contentPadding = PaddingValues(
                    top = TakiTheme.spacing.lg,
                    bottom = bottomContentInset,
                ),
            ) {
                item(key = "header") {
                    HomeHeader(greeting = state.greeting, onOverflow = actions.onOverflow)
                }

                if (state.featuredMix != null) {
                    item(key = "featured") { FeaturedMix(mix = state.featuredMix, actions = actions) }
                }

                item(key = "quick_access") { QuickAccessRow(actions = actions) }

                if (state.isLoading && !state.hasContent) {
                    item(key = "skeleton") { HomeSkeleton() }
                }

                if (state.recentlyPlayed.isNotEmpty()) {
                    shelf(
                        key = "recent",
                        titleRes = R.string.main_albums_recent,
                        albums = state.recentlyPlayed,
                        compact = true,
                        onAlbumClick = actions.onAlbumClick,
                    )
                }

                state.shelves.forEach { shelfUi ->
                    if (shelfUi.albums.isNotEmpty()) {
                        shelf(
                            key = "shelf_${shelfUi.kind.name}",
                            titleRes = shelfUi.kind.titleRes,
                            albums = shelfUi.albums,
                            compact = false,
                            onAlbumClick = actions.onAlbumClick,
                        )
                    }
                }

                if (state.isEmpty) {
                    item(key = "empty") { HomeEmpty() }
                }
            }
        }
    }
}

@get:StringRes
private val HomeShelfKind.titleRes: Int
    get() = when (this) {
        HomeShelfKind.LIKED -> R.string.main_albums_starred
        HomeShelfKind.NEWEST -> R.string.main_albums_newest
        HomeShelfKind.DISCOVER -> R.string.home_discover_title
        HomeShelfKind.FREQUENT -> R.string.main_albums_frequent
    }

@get:StringRes
private val HomeGreeting.textRes: Int
    get() = when (this) {
        HomeGreeting.MORNING -> R.string.home_greeting_morning
        HomeGreeting.AFTERNOON -> R.string.home_greeting_afternoon
        HomeGreeting.EVENING -> R.string.home_greeting_evening
    }

@Composable
private fun HomeHeader(greeting: HomeGreeting, onOverflow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TakiTheme.spacing.screenHorizontal,
                end = TakiTheme.spacing.screenHorizontal,
                bottom = TakiTheme.spacing.sm,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(greeting.textRes),
            style = TakiTheme.type.hero,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        TakiIconButton(
            onClick = onOverflow,
            painter = painterResource(R.drawable.ic_more_vert),
            contentDescription = stringResource(R.string.library_hub_title),
        )
    }
}

@Composable
private fun FeaturedMix(mix: FeaturedMixUi, actions: HomeActions) {
    FeaturedMixCard(
        title = stringResource(R.string.home_mix_title),
        subtitle = stringResource(R.string.home_mix_song_count, mix.trackCount),
        artworkModel = mix.artworkModel,
        onPlay = actions.onPlayMix,
        playContentDescription = stringResource(R.string.home_mix_play),
        onClick = actions.onOpenMix,
        onSecondary = actions.onRegenerateMix,
        secondaryPainter = painterResource(R.drawable.media_shuffle),
        secondaryContentDescription = stringResource(R.string.home_mix_regenerate),
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TakiTheme.spacing.screenHorizontal,
                end = TakiTheme.spacing.screenHorizontal,
                top = TakiTheme.spacing.sectionGap,
            ),
    )
}

@Composable
private fun QuickAccessRow(actions: HomeActions) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = TakiTheme.spacing.xs),
        contentPadding = PaddingValues(horizontal = TakiTheme.spacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.sm),
    ) {
        item {
            TakiFilterChip(
                stringResource(R.string.playlist_label),
                selected = false,
                onClick = actions.onOpenPlaylists,
            )
        }
        item {
            TakiFilterChip(
                stringResource(R.string.main_albums_title),
                selected = false,
                onClick = actions.onOpenAlbums,
            )
        }
        item {
            TakiFilterChip(
                stringResource(R.string.main_artists_title),
                selected = false,
                onClick = actions.onOpenArtists,
            )
        }
        item {
            TakiFilterChip(
                stringResource(R.string.main_songs_title),
                selected = false,
                onClick = actions.onOpenSongs,
            )
        }
    }
}

@Composable
private fun HomeEmpty() {
    EmptyState(
        icon = painterResource(R.drawable.ic_library),
        title = stringResource(R.string.select_album_empty),
        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
    )
}

private fun LazyListScope.shelf(
    key: String,
    @StringRes titleRes: Int,
    albums: List<HomeAlbumUi>,
    compact: Boolean,
    onAlbumClick: (HomeAlbumUi) -> Unit,
) {
    item(key = "${key}_header") {
        TakiSectionHeader(
            title = stringResource(titleRes),
            modifier = Modifier.padding(
                start = TakiTheme.spacing.screenHorizontal,
                end = TakiTheme.spacing.screenHorizontal,
                top = TakiTheme.spacing.sectionGap,
                bottom = TakiTheme.spacing.sm,
            ),
        )
    }
    item(key = "${key}_row") {
        val artworkSize = if (compact) {
            TakiTheme.dimensions.artworkShelfCompact
        } else {
            TakiTheme.dimensions.artworkCard
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = TakiTheme.spacing.screenHorizontal),
            horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumShelfItem(
                    title = album.title,
                    subtitle = album.subtitle,
                    artworkModel = album.artworkModel,
                    onClick = { onAlbumClick(album) },
                    artworkSize = artworkSize,
                )
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(modifier = Modifier.padding(top = TakiTheme.spacing.sectionGap)) {
        repeat(SKELETON_SHELVES) {
            SkeletonBlock(
                width = SKELETON_HEADER_WIDTH,
                height = TakiTheme.spacing.lg,
                modifier = Modifier.padding(
                    horizontal = TakiTheme.spacing.screenHorizontal,
                    vertical = TakiTheme.spacing.sm,
                ),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = TakiTheme.spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
                userScrollEnabled = false,
            ) {
                items(SKELETON_ITEMS) {
                    SkeletonBlock(
                        width = TakiTheme.dimensions.artworkCard,
                        height = TakiTheme.dimensions.artworkCard,
                    )
                }
            }
            Spacer(Modifier.height(TakiTheme.spacing.sectionGap))
        }
    }
}

@Composable
private fun SkeletonBlock(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Spacer(
        modifier = modifier
            .width(width)
            .height(height)
            .clip(TakiTheme.shapes.sm)
            .background(TakiTheme.colors.surfaceLow),
    )
}

/** For tests: the outer vertical list. */
const val HOME_LIST_TEST_TAG = "home_list"

private const val SKELETON_SHELVES = 3
private const val SKELETON_ITEMS = 4
private val SKELETON_HEADER_WIDTH = 140.dp // taki-raw-ok: placeholder bar width, not a component

@Preview
@Composable
private fun HomeScreenPreview() {
    TakiTheme {
        HomeScreen(
            state = HomeUiState(
                isLoading = false,
                featuredMix = FeaturedMixUi(trackCount = 25, artworkModel = null),
                recentlyPlayed = persistentListOf(
                    HomeAlbumUi("1", "An Album", "An Artist", null, true, null),
                    HomeAlbumUi("2", "Another", "Someone", null, true, null),
                ),
                shelves = persistentListOf(
                    HomeShelfUi(
                        HomeShelfKind.LIKED,
                        persistentListOf(HomeAlbumUi("3", "Liked One", "Artist", null, true, null)),
                    ),
                ),
            ),
            actions = HomeActions.Noop,
        )
    }
}
