/*
 * SearchActions.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.search

/**
 * Every side-effecting thing the Search screen can ask for. Supplied by the host Fragment,
 * which owns the `NavController` and the `MediaPlayerManager` command boundary (migration
 * plan section 3.2 / 8). Query/recent/show-more callbacks go straight to `SearchViewModel`;
 * result taps navigate or issue the play command. Composables touch neither directly.
 */
class SearchActions(
    val onQueryChange: (String) -> Unit,
    val onSubmit: () -> Unit,
    val onClearQuery: () -> Unit,
    val onRecentSearchTap: (String) -> Unit,
    val onRemoveRecentSearch: (String) -> Unit,
    val onClearAllRecentSearches: () -> Unit,
    val onShowMoreArtists: () -> Unit,
    val onShowMoreAlbums: () -> Unit,
    val onShowMoreSongs: () -> Unit,
    val onArtistClick: (SearchArtistUi) -> Unit,
    val onAlbumClick: (SearchAlbumUi) -> Unit,
    val onSongClick: (SearchSongUi) -> Unit,
) {
    companion object {
        /** Inert set, for previews and screenshot tests. */
        val Noop = SearchActions(
            onQueryChange = {},
            onSubmit = {},
            onClearQuery = {},
            onRecentSearchTap = {},
            onRemoveRecentSearch = {},
            onClearAllRecentSearches = {},
            onShowMoreArtists = {},
            onShowMoreAlbums = {},
            onShowMoreSongs = {},
            onArtistClick = {},
            onAlbumClick = {},
            onSongClick = {},
        )
    }
}
