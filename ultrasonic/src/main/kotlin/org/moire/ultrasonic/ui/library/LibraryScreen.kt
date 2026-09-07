/*
 * LibraryScreen.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.library

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import org.moire.ultrasonic.R
import org.moire.ultrasonic.ui.components.LibraryBrowseRow
import org.moire.ultrasonic.ui.components.LibraryPrimaryCard
import org.moire.ultrasonic.ui.components.TakiIconButton
import org.moire.ultrasonic.ui.components.TakiScaffold
import org.moire.ultrasonic.ui.components.TakiSectionHeader
import org.moire.ultrasonic.ui.theme.TakiTheme

const val LIBRARY_LIST_TEST_TAG = "library_list"

/**
 * Library (TAKI_DESIGN_SYSTEM_V2.md section 11 / the visual north star): two clearly
 * different levels. **"Your music"** is a 2x2 grid of compact personal-destination
 * [LibraryPrimaryCard]s; **"Browse your collection"** is a quieter list of 56dp transparent
 * [LibraryBrowseRow]s. No hero card, no atmosphere, no artwork - deliberately calmer than
 * Home. One [rememberLazyListState] so scroll position survives navigation.
 *
 * [bottomContentInset] is the live floating-chrome reserve from `NavigationActivity` (same
 * flow Home uses) so the last row clears the bottom nav / mini-player while the rest of the
 * content scrolls behind them.
 */
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    actions: LibraryActions,
    modifier: Modifier = Modifier,
    bottomContentInset: Dp = TakiTheme.dimensions.contentInsetFloatingChrome,
) {
    TakiScaffold(modifier = modifier) {
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(LIBRARY_LIST_TEST_TAG),
            contentPadding = PaddingValues(
                top = TakiTheme.spacing.lg,
                bottom = bottomContentInset,
            ),
        ) {
            item(key = "header") {
                LibraryHeader(onOverflow = actions.onOverflow)
            }

            sectionHeader("your_music", R.string.library_your_music)
            item(key = "primary_grid") { YourMusicGrid(actions) }

            sectionHeader("collection", R.string.library_collection)
            browseRow(
                "albums", R.string.main_albums_title,
                R.drawable.ic_menu_browse, actions.onAlbums,
            )
            browseRow(
                "artists", R.string.main_artists_title,
                R.drawable.ic_artist, actions.onArtists,
            )
            browseRow(
                "songs", R.string.main_songs_title,
                R.drawable.ic_library, actions.onSongs,
            )
            browseRow(
                "genres", R.string.main_genres_title,
                R.drawable.genre_placeholder_icon, actions.onGenres,
            )
            // Box Sets: only once a box set has been resolved from already-cached metadata.
            if (state.boxSetsAvailable) {
                browseRow(
                    "box_sets", R.string.library_box_sets,
                    R.drawable.ic_layers, actions.onBoxSets,
                )
            }
        }
    }
}

/** The 2x2 "Your music" card grid. Reading order = the required stable order. */
@Composable
private fun YourMusicGrid(actions: LibraryActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = TakiTheme.spacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md)) {
            LibraryPrimaryCard(
                label = stringResource(R.string.library_liked_songs),
                leadingPainter = painterResource(R.drawable.rating_heart_full),
                onClick = actions.onLikedSongs,
                modifier = Modifier.weight(1f),
                iconTint = TakiTheme.colors.liked,
            )
            LibraryPrimaryCard(
                label = stringResource(R.string.library_liked_albums),
                leadingPainter = painterResource(R.drawable.rating_heart_full),
                onClick = actions.onLikedAlbums,
                modifier = Modifier.weight(1f),
                iconTint = TakiTheme.colors.liked,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TakiTheme.spacing.md)) {
            LibraryPrimaryCard(
                label = stringResource(R.string.playlist_label),
                leadingPainter = painterResource(R.drawable.ic_menu_playlists),
                onClick = actions.onPlaylists,
                modifier = Modifier.weight(1f),
            )
            LibraryPrimaryCard(
                label = stringResource(R.string.menu_downloads),
                leadingPainter = painterResource(R.drawable.ic_menu_download),
                onClick = actions.onDownloads,
                modifier = Modifier.weight(1f),
            )
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

private fun LazyListScope.browseRow(
    key: String,
    @StringRes labelRes: Int,
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
) {
    item(key = key) {
        LibraryBrowseRow(
            label = stringResource(labelRes),
            leadingPainter = painterResource(iconRes),
            onClick = onClick,
        )
    }
}

@Composable
private fun LibraryHeader(onOverflow: () -> Unit) {
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
            text = stringResource(R.string.library_hub_bottom_label),
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

@Preview
@Composable
private fun LibraryScreenPreview() {
    TakiTheme {
        LibraryScreen(
            state = LibraryUiState(boxSetsAvailable = true),
            actions = LibraryActions.Noop,
        )
    }
}
