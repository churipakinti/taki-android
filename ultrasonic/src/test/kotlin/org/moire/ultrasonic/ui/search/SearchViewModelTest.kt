/*
 * SearchViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.search

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.SearchViewModel
import org.moire.ultrasonic.util.RecentSearches
import org.moire.ultrasonic.util.Settings
import org.robolectric.RobolectricTestRunner

/**
 * [SearchViewModel] semantics, ported 1:1 from `SearchFragment`: 300ms debounce, min 2 chars,
 * no-repeat-of-last-live-query, per-request cancellation + latest-wins, `DEFAULT_*` slice with
 * per-group "Show more", video filtering, recent-search read/write, initial (voice) query,
 * and failure -> empty results. The server call is replaced with a controllable fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
        RecentSearches(app).clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        RecentSearches(app).clear()
    }

    private fun vm(
        service: suspend (SearchCriteria) -> SearchResult? = { result() },
    ) = SearchViewModel(app).apply { searchService = service }

    private fun result(
        artists: Int = 1,
        albums: Int = 1,
        songs: Int = 1,
        videos: Int = 0,
    ) = SearchResult(
        artists = List(artists) { Artist(id = "ar$it", serverId = 0, name = "Artist $it") },
        albums = List(albums) { Album(id = "al$it", title = "Album $it", artist = "By $it") },
        songs = List(songs) { Track(id = "s$it", title = "Song $it", artist = "By $it") } +
            List(videos) { Track(id = "v$it", title = "Video $it", isVideo = true) },
    )

    @Test
    fun `default state is the empty recent body`() {
        val state = vm().uiState.value
        assertEquals("", state.query)
        assertFalse(state.isSearching)
        assertFalse(state.submitted)
        assertFalse(state.hasResults)
    }

    @Test
    fun `a one-character query never searches`() = runTest {
        var calls = 0
        val model = vm { calls++; result() }
        model.onQueryChange("b")
        advanceUntilIdle()
        assertEquals(0, calls)
    }

    @Test
    fun `a two-character query searches once after the debounce`() = runTest {
        var calls = 0
        val model = vm { calls++; result() }
        model.onQueryChange("ba")
        advanceTimeBy(299)
        assertEquals(0, calls)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertTrue(model.uiState.value.submitted)
    }

    @Test
    fun `rapid typing collapses to a single search for the final query`() = runTest {
        val queried = mutableListOf<String>()
        val model = vm { queried += it.query; result() }
        model.onQueryChange("ba")
        advanceTimeBy(100)
        model.onQueryChange("bac")
        advanceTimeBy(100)
        model.onQueryChange("bach")
        advanceUntilIdle()
        assertEquals(listOf("bach"), queried)
    }

    @Test
    fun `submit searches immediately and records a recent search`() = runTest {
        var calls = 0
        val model = vm { calls++; result() }
        model.onQueryChange("bach")
        model.onSubmit()
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(listOf("bach"), RecentSearches(app).get())
        assertTrue(model.uiState.value.recentSearches.contains("bach"))
    }

    @Test
    fun `a slow older response cannot overwrite a newer one`() = runTest {
        val model = vm { criteria ->
            if (criteria.query == "old") { delay(1_000); result(artists = 9) } else result(artists = 1)
        }
        model.onQueryChange("old")
        advanceTimeBy(300)
        // "old" request is now in its 1s delay; a newer query supersedes it.
        model.onQueryChange("new")
        advanceUntilIdle()
        assertEquals(1, model.uiState.value.artists.size)
    }

    @Test
    fun `results are sliced to DEFAULT and Show more expands one group`() = runTest {
        val model = vm { result(artists = Settings.DEFAULT_ARTISTS + 4) }
        model.onQueryChange("bach")
        advanceUntilIdle()

        assertEquals(Settings.DEFAULT_ARTISTS, model.uiState.value.artists.size)
        assertTrue(model.uiState.value.artistsHaveMore)

        model.onShowMoreArtists()
        advanceUntilIdle()
        assertEquals(Settings.DEFAULT_ARTISTS + 4, model.uiState.value.artists.size)
        assertFalse(model.uiState.value.artistsHaveMore)
    }

    @Test
    fun `video songs are filtered out`() = runTest {
        val model = vm { result(songs = 2, videos = 3) }
        model.onQueryChange("bach")
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.songs.size)
        assertTrue(model.uiState.value.songs.none { it.id.startsWith("v") })
    }

    @Test
    fun `clearing the query drops results and returns to the recent body`() = runTest {
        val model = vm { result() }
        model.onQueryChange("bach")
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasResults)

        model.onClearQuery()
        advanceUntilIdle()
        val state = model.uiState.value
        assertEquals("", state.query)
        assertFalse(state.hasResults)
        assertFalse(state.submitted)
        assertTrue(state.showRecentSearches)
    }

    @Test
    fun `tapping a recent search moves it to the top and searches`() = runTest {
        RecentSearches(app).save("old one")
        RecentSearches(app).save("bach")
        var calls = 0
        val model = vm { calls++; result() }

        model.onRecentSearchTap("old one")
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals("old one", model.uiState.value.recentSearches.first())
        assertEquals("old one", model.uiState.value.query)
    }

    @Test
    fun `remove one and clear all update the recent list`() = runTest {
        RecentSearches(app).save("a")
        RecentSearches(app).save("b")
        val model = vm()

        model.onRemoveRecentSearch("a")
        assertEquals(listOf("b"), model.uiState.value.recentSearches)

        model.onClearAllRecentSearches()
        assertTrue(model.uiState.value.recentSearches.isEmpty())
    }

    @Test
    fun `an initial voice query searches but is not re-saved as recent by the ViewModel`() = runTest {
        var calls = 0
        val model = vm { calls++; result() }
        model.setInitialQuery("moonlight sonata")
        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals("moonlight sonata", model.uiState.value.query)
        // The Activity persists the voice query; the ViewModel must not add a duplicate.
        assertFalse(RecentSearches(app).get().contains("moonlight sonata"))
    }

    @Test
    fun `a failing search ends as a finished empty result`() = runTest {
        val model = vm { throw IOException("server down") }
        model.onQueryChange("bach")
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isSearching)
        assertTrue(state.submitted)
        assertFalse(state.hasResults)
        assertTrue(state.showNoResults)
    }

    @Test
    fun `trackFor returns the full track behind a shown song row`() = runTest {
        val model = vm { result(songs = 1) }
        model.onQueryChange("bach")
        advanceUntilIdle()

        val id = model.uiState.value.songs.first().id
        assertEquals("Song 0", model.trackFor(id)?.title)
        assertNull(model.trackFor("nope"))
    }
}
