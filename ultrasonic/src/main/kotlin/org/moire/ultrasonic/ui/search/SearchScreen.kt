/*
 * SearchScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.search

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.EmptyState
import org.moire.ultrasonic.ui.components.TakiArtwork
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiSearchField
import org.moire.ultrasonic.ui.components.TakiSectionHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

const val SEARCH_LIST_TEST_TAG = "search_list"

/**
 * Search (TAKI_DESIGN_SYSTEM_V2.md section 11 / the north star): the most immediate of the
 * three primary destinations. A fixed header + search field, then a context-dependent body -
 * recent searches when the query is empty, three differentiated result groups while
 * searching, a slim progress line during a refresh (results stay put), and a calm "no
 * matches" state. No hero card, no atmosphere.
 *
 * [bottomContentInset] is the live floating-chrome reserve from `NavigationActivity` (the
 * same flow Home / Library use). While the IME is up the Activity hides the mini-player and
 * bottom nav, so that reserve shrinks and results simply scroll under the keyboard, exactly
 * as the legacy screen behaved.
 */
@Composable
fun SearchScreen(
    state: SearchUiState,
    actions: SearchActions,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = TakiTheme.dimensions.contentInsetFloatingChrome,
) {
    TakiScaffold(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.search_title),
                style = TakiTheme.type.hero,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(
                        start = TakiTheme.spacing.screenHorizontal,
                        end = TakiTheme.spacing.screenHorizontal,
                        top = TakiTheme.spacing.lg,
                        bottom = TakiTheme.spacing.sm,
                    )
                    .semantics { heading() },
            )
            TakiSearchField(
                query = state.query,
                onQueryChange = actions.onQueryChange,
                onSubmit = actions.onSubmit,
                onClear = actions.onClearQuery,
                modifier = Modifier.padding(horizontal = TakiTheme.spacing.screenHorizontal),
            )
            // Reserved 2dp strip so a refresh never shifts the list.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TakiTheme.spacing.xxs),
            ) {
                if (state.isSearching) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(TakiTheme.spacing.xxs),
                        color = TakiTheme.colors.accent,
                        trackColor = TakiTheme.colors.surfaceLow,
                    )
                }
            }

            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag(SEARCH_LIST_TEST_TAG),
                contentPadding = PaddingValues(
                    top = TakiTheme.spacing.sm,
                    bottom = bottomContentInset,
                ),
            ) {
                when {
                    state.showRecentSearches -> recentSearches(state, actions)
                    state.showNoResults -> item(key = "no_results") { NoResults() }
                    else -> resultGroups(state, actions)
                }
            }
        }
    }
}

private fun LazyListScope.recentSearches(state: SearchUiState, actions: SearchActions) {
    if (state.recentSearches.isEmpty()) {
        item(key = "prompt") {
            EmptyState(
                icon = painterResource(R.drawable.ic_menu_search),
                title = stringResource(R.string.search_prompt),
                modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
            )
        }
        return
    }
    item(key = "recent_header") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = TakiTheme.spacing.screenHorizontal,
                    end = TakiTheme.spacing.screenHorizontal,
                    top = TakiTheme.spacing.md,
                    bottom = TakiTheme.spacing.sm,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.search_recent),
                style = TakiTheme.type.sectionHeader,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            Text(
                text = stringResource(R.string.search_clear_all),
                style = TakiTheme.type.caption,
                color = TakiTheme.colors.accent,
                modifier = Modifier
                    .defaultMinSize(minHeight = TakiTheme.dimensions.touchTargetMin)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .clickable(role = Role.Button, onClick = actions.onClearAllRecentSearches)
                    .padding(start = TakiTheme.spacing.md),
            )
        }
    }
    items(state.recentSearches, key = { "recent_$it" }) { query ->
        RecentSearchRow(
            query = query,
            onTap = { actions.onRecentSearchTap(query) },
            onRemove = { actions.onRemoveRecentSearch(query) },
        )
    }
}

private fun LazyListScope.resultGroups(state: SearchUiState, actions: SearchActions) {
    if (state.artists.isNotEmpty()) {
        sectionHeader("artists", R.string.search_artists)
        items(state.artists, key = { "artist_${it.id}" }) { artist ->
            ArtistResultRow(artist = artist, onClick = { actions.onArtistClick(artist) })
        }
        if (state.artistsHaveMore) {
            item(key = "more_artists") { ShowMoreRow(actions.onShowMoreArtists) }
        }
    }
    if (state.albums.isNotEmpty()) {
        sectionHeader("albums", R.string.search_albums)
        items(state.albums, key = { "album_${it.id}" }) { album ->
            ArtworkResultRow(
                title = album.title,
                subtitle = album.subtitle,
                artworkModel = album.artworkModel,
                onClick = { actions.onAlbumClick(album) },
            )
        }
        if (state.albumsHaveMore) {
            item(key = "more_albums") { ShowMoreRow(actions.onShowMoreAlbums) }
        }
    }
    if (state.songs.isNotEmpty()) {
        sectionHeader("songs", R.string.search_songs)
        items(state.songs, key = { "song_${it.id}" }) { song ->
            ArtworkResultRow(
                title = song.title,
                subtitle = song.subtitle,
                artworkModel = song.artworkModel,
                onClick = { actions.onSongClick(song) },
            )
        }
        if (state.songsHaveMore) {
            item(key = "more_songs") { ShowMoreRow(actions.onShowMoreSongs) }
        }
    }
}

private fun LazyListScope.sectionHeader(key: String, @StringRes titleRes: Int) {
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
}

@Composable
private fun RecentSearchRow(query: String, onTap: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.rowSm)
            .clickable(role = Role.Button, onClick = onTap)
            .padding(start = TakiTheme.spacing.screenHorizontal)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_history),
            contentDescription = null,
            tint = TakiTheme.colors.gray,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Text(
            text = query,
            style = TakiTheme.type.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TakiIconButton(
            onClick = onRemove,
            painter = painterResource(R.drawable.ic_menu_close),
            contentDescription = stringResource(R.string.search_remove_recent),
            iconSize = TakiTheme.dimensions.iconSm,
        )
    }
}

@Composable
private fun ArtistResultRow(artist: SearchArtistUi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.rowSm)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TakiTheme.spacing.screenHorizontal)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_artist),
            contentDescription = null,
            tint = TakiTheme.colors.gray,
            modifier = Modifier.size(TakiTheme.dimensions.iconMd),
        )
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Text(
            text = artist.name,
            style = TakiTheme.type.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ArtworkResultRow(
    title: String,
    subtitle: String,
    artworkModel: Any?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.rowLg)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TakiTheme.spacing.screenHorizontal)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TakiArtwork(
            model = artworkModel,
            contentDescription = null,
            size = TakiTheme.dimensions.artworkMini,
            shape = TakiTheme.shapes.sm,
        )
        Spacer(Modifier.width(TakiTheme.spacing.md))
        Column {
            Text(
                text = title,
                style = TakiTheme.type.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = TakiTheme.type.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ShowMoreRow(onClick: () -> Unit) {
    Text(
        text = stringResource(R.string.search_more),
        style = TakiTheme.type.caption,
        color = TakiTheme.colors.gray,
        modifier = Modifier
            .fillMaxWidth()
            .height(TakiTheme.dimensions.touchTargetMin)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = TakiTheme.spacing.screenHorizontal),
    )
}

@Composable
private fun NoResults() {
    EmptyState(
        icon = painterResource(R.drawable.ic_menu_search),
        title = stringResource(R.string.search_no_match),
        modifier = Modifier.padding(top = TakiTheme.spacing.xxl),
    )
}

@Preview
@Composable
private fun SearchScreenPreview() {
    TakiTheme {
        SearchScreen(
            state = SearchUiState(recentSearches = persistentListOf("bach", "miles davis")),
            actions = SearchActions.Noop,
        )
    }
}
