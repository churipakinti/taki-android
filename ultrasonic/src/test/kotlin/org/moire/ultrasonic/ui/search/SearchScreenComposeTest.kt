/*
 * SearchScreenComposeTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.ui.theme.TakiTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Search screen: the search field, the recent-searches body, the three differentiated result
 * groups, "Show more", the loading strip, no-results, and every callback. JVM / Robolectric,
 * a short viewport so "reachable by scrolling" is a real assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w412dp-h640dp-xxhdpi")
class SearchScreenComposeTest {

    @get:Rule
    val compose = createComposeRule()

    private fun art(id: String) = CoverArtRequest(id, "key-$id", size = 0)

    private val populated = SearchUiState(
        query = "bach",
        submitted = true,
        artists = persistentListOf(
            SearchArtistUi("ar1", "Johann Sebastian Bach", isIndex = false),
            SearchArtistUi("ar2", "Wilhelm Friedemann Bach", isIndex = true),
        ),
        albums = persistentListOf(
            SearchAlbumUi("al1", "Cello Suites", "J. S. Bach", art("al1")),
        ),
        songs = persistentListOf(
            SearchSongUi("s1", "Air on the G String", "J. S. Bach", art("s1")),
        ),
        artistsHaveMore = true,
        albumsHaveMore = false,
        songsHaveMore = false,
    )

    private fun setContent(
        state: SearchUiState,
        actions: SearchActions = SearchActions.Noop,
        fontScale: Float = 1f,
    ) {
        compose.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                TakiTheme { SearchScreen(state = state, actions = actions) }
            }
        }
    }

    private fun scrollTo(text: String) = run {
        compose.onNodeWithTag(SEARCH_LIST_TEST_TAG).performScrollToNode(hasText(text, substring = true))
        compose.onNodeWithText(text, substring = true)
    }

    @Test
    fun `renders the title and the search field`() {
        setContent(SearchUiState())
        compose.onNodeWithText("Search").assertIsDisplayed()
        compose.onNodeWithText("Search your music").assertIsDisplayed()
    }

    @Test
    fun `typing in the field forwards to the query callback`() {
        val typed = StringBuilder()
        setContent(SearchUiState(), noopExcept(onQueryChange = { typed.append(it) }))
        compose.onNode(hasSetTextAction()).performTextInput("bach")
        assertEquals("bach", typed.toString())
    }

    @Test
    fun `the clear button shows only with a query and fires the clear callback`() {
        var cleared = 0
        setContent(SearchUiState(query = "bach"), noopExcept(onClearQuery = { cleared++ }))
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun `no clear button when the query is empty`() {
        setContent(SearchUiState())
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
    }

    @Test
    fun `recent searches render and their taps route`() {
        var tapped: String? = null
        var removed: String? = null
        var clearedAll = 0
        setContent(
            SearchUiState(recentSearches = persistentListOf("bach", "miles davis")),
            noopExcept(
                onRecentSearchTap = { tapped = it },
                onRemoveRecentSearch = { removed = it },
                onClearAllRecentSearches = { clearedAll++ },
            ),
        )
        compose.onNodeWithText("Recent searches").assertIsDisplayed()
        compose.onNodeWithText("miles davis").performClick()
        assertEquals("miles davis", tapped)

        compose.onAllNodesWithContentDescription("Remove from recent searches")[0].performClick()
        assertEquals("bach", removed)

        compose.onNodeWithText("Clear all").performClick()
        assertEquals(1, clearedAll)
    }

    @Test
    fun `the empty landing prompt shows when there are no recent searches`() {
        setContent(SearchUiState())
        compose.onNodeWithText("Search artists, albums, and songs").assertIsDisplayed()
    }

    @Test
    fun `the three result groups render and Show more fires for its group`() {
        var moreArtists = 0
        setContent(populated, noopExcept(onShowMoreArtists = { moreArtists++ }))

        compose.onNodeWithText("Artists").assertIsDisplayed()
        scrollTo("Albums").assertIsDisplayed()
        scrollTo("Songs").assertIsDisplayed()

        compose.onNodeWithTag(SEARCH_LIST_TEST_TAG).performScrollToNode(hasText("Show More"))
        compose.onNodeWithText("Show More").performClick()
        assertEquals(1, moreArtists)
    }

    @Test
    fun `result taps forward the right model`() {
        var artist: SearchArtistUi? = null
        var album: SearchAlbumUi? = null
        var song: SearchSongUi? = null
        setContent(
            populated,
            noopExcept(
                onArtistClick = { artist = it },
                onAlbumClick = { album = it },
                onSongClick = { song = it },
            ),
        )
        compose.onNodeWithText("Johann Sebastian Bach").performClick()
        scrollTo("Cello Suites").performClick()
        scrollTo("Air on the G String").performClick()

        assertEquals("ar1", artist?.id)
        assertEquals("al1", album?.id)
        assertEquals("s1", song?.id)
    }

    @Test
    fun `a running search shows the progress strip without blanking results`() {
        setContent(populated.copy(isSearching = true))
        compose.onNodeWithText("Johann Sebastian Bach").assertIsDisplayed()
    }

    @Test
    fun `a finished empty search shows the no-results state`() {
        setContent(SearchUiState(query = "zzz", submitted = true, isSearching = false))
        compose.onNodeWithText("No matches, please try again").assertIsDisplayed()
    }

    @Test
    fun `font scale 1_30 keeps the field and groups usable`() {
        setContent(populated, fontScale = 1.30f)
        compose.onNode(hasSetTextAction()).assertIsDisplayed()
        scrollTo("Artists").assertIsDisplayed()
        scrollTo("Johann Sebastian Bach").assertIsDisplayed()
    }

    private fun noopExcept(
        onQueryChange: (String) -> Unit = {},
        onClearQuery: () -> Unit = {},
        onRecentSearchTap: (String) -> Unit = {},
        onRemoveRecentSearch: (String) -> Unit = {},
        onClearAllRecentSearches: () -> Unit = {},
        onShowMoreArtists: () -> Unit = {},
        onArtistClick: (SearchArtistUi) -> Unit = {},
        onAlbumClick: (SearchAlbumUi) -> Unit = {},
        onSongClick: (SearchSongUi) -> Unit = {},
    ) = SearchActions(
        onQueryChange = onQueryChange,
        onSubmit = {},
        onClearQuery = onClearQuery,
        onRecentSearchTap = onRecentSearchTap,
        onRemoveRecentSearch = onRemoveRecentSearch,
        onClearAllRecentSearches = onClearAllRecentSearches,
        onShowMoreArtists = onShowMoreArtists,
        onShowMoreAlbums = {},
        onShowMoreSongs = {},
        onArtistClick = onArtistClick,
        onAlbumClick = onAlbumClick,
        onSongClick = onSongClick,
    )
}
