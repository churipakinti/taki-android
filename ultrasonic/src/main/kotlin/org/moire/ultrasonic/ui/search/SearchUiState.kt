/*
 * SearchUiState.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.search

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.moire.ultrasonic.imageloader.CoverArtRequest

/**
 * Everything the Compose Search screen renders, as one immutable snapshot built by
 * `SearchViewModel`. Behaviour parity with the old `SearchFragment`:
 *
 *  - empty query -> recent searches (or the "search artists, albums, and songs" prompt when
 *    there are none);
 *  - a live/submitted search -> the trimmed Artists / Albums / Songs groups, each with a
 *    "Show more" affordance when the server returned more than the shown slice;
 *  - a search in flight never blanks results that are already on screen ([isSearching] is a
 *    flag, not a screen state);
 *  - a completed search with nothing to show -> the "no matches" state.
 *
 * Video songs are already filtered out upstream (music-only surface).
 */
@Immutable
data class SearchUiState(
    val query: String = "",
    /** A search request is in flight. Prior results stay visible while it runs. */
    val isSearching: Boolean = false,
    /** At least one search has completed for the current non-empty query. */
    val submitted: Boolean = false,
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val artists: ImmutableList<SearchArtistUi> = persistentListOf(),
    val albums: ImmutableList<SearchAlbumUi> = persistentListOf(),
    val songs: ImmutableList<SearchSongUi> = persistentListOf(),
    val artistsHaveMore: Boolean = false,
    val albumsHaveMore: Boolean = false,
    val songsHaveMore: Boolean = false,
) {
    val hasResults: Boolean
        get() = artists.isNotEmpty() || albums.isNotEmpty() || songs.isNotEmpty()

    /** Empty query -> the recent-searches / prompt body. */
    val showRecentSearches: Boolean
        get() = query.isBlank()

    /** Empty query and no history -> the restrained landing prompt. */
    val showPrompt: Boolean
        get() = query.isBlank() && recentSearches.isEmpty()

    /** A finished search for a real query that matched nothing. */
    val showNoResults: Boolean
        get() = query.isNotBlank() && submitted && !isSearching && !hasResults
}

@Immutable
data class SearchArtistUi(
    val id: String,
    val name: String,
    /** A folder-style [org.moire.ultrasonic.domain.Index] (opens its tracks) vs an id3 artist
     *  (opens its album list) - drives which existing destination the row navigates to. */
    val isIndex: Boolean,
)

@Immutable
data class SearchAlbumUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkModel: CoverArtRequest?,
)

@Immutable
data class SearchSongUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val artworkModel: CoverArtRequest?,
)
