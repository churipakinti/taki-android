/*
 * CreatePlaylistViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.createplaylist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.CreatePlaylistViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner

/**
 * [CreatePlaylistViewModel] projection, ported 1:1 from the legacy `CreatePlaylistFragment`:
 * paged All Songs/Random/By Artist/By Genre loading, free-text search (no pagination), the
 * insertion-ordered selection set, and the create-only save call. Every server call is replaced
 * with a controllable fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CreatePlaylistViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var activeServerProvider: ActiveServerProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()

        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = true

        activeServerProvider = mock {
            on { getActiveServer(any()) } doReturn ServerSetting().apply { musicFolderId = null }
        }
        startKoin {
            modules(module { single { activeServerProvider } })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    private fun track(id: String, title: String = "Track $id", artist: String = "Artist", album: String = "Album") =
        Track(id = id, title = title, artist = artist, album = album)

    private fun vm(
        allSongs: suspend (Int, Int, String?) -> List<Track> = { _, offset, _ ->
            if (offset == 0) listOf(track("1"), track("2")) else emptyList()
        },
    ) = CreatePlaylistViewModel(app).apply {
        allSongsLoader = allSongs
        load()
    }

    @Test
    fun `the default state is loading and loads all songs immediately`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1"), track("2")) }
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("1", "2"), state.rows.map { it.id })
        assertEquals(SortOrder.ALL_SONGS, state.sortOrder)
    }

    @Test
    fun `a track row carries title, subtitle, and selected state`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1", title = "So What", artist = "Miles Davis", album = "Kind of Blue")) }
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals("So What", row.title)
        assertEquals("Miles Davis · Kind of Blue", row.subtitle)
        assertFalse(row.selected)
    }

    @Test
    fun `toggling a track selects it and updates the row and count`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        advanceUntilIdle()

        model.toggleTrack("1")

        val state = model.uiState.value
        assertEquals(1, state.selectedCount)
        assertTrue(state.rows.single().selected)
        assertEquals(listOf("1"), model.selectedTracksSnapshot()?.map { it.id })
    }

    @Test
    fun `toggling a track twice deselects it`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        advanceUntilIdle()

        model.toggleTrack("1")
        model.toggleTrack("1")

        assertEquals(0, model.uiState.value.selectedCount)
        assertNull(model.selectedTracksSnapshot())
    }

    @Test
    fun `re-selecting a track moves it to the end of the selection order`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1"), track("2"), track("3")) }
        advanceUntilIdle()

        model.toggleTrack("1")
        model.toggleTrack("2")
        model.toggleTrack("3")
        // Deselect and reselect "1" - it must move to the end, exactly like the legacy
        // LinkedHashMap remove()+put() reinsertion.
        model.toggleTrack("1")
        model.toggleTrack("1")

        assertEquals(listOf("2", "3", "1"), model.selectedTracksSnapshot()?.map { it.id })
    }

    @Test
    fun `load more appends with the next offset and stops when the page is short`() = runTest {
        var calls = 0
        val model = vm { count, offset, _ ->
            calls++
            when (offset) {
                0 -> List(count) { i -> track("p1-$i") }
                count -> listOf(track("p2-last")) // short page: exhausts canLoadMore
                else -> emptyList()
            }
        }
        advanceUntilIdle()
        val firstPageSize = model.uiState.value.rows.size

        model.loadMore()
        advanceUntilIdle()
        assertEquals(firstPageSize + 1, model.uiState.value.rows.size)

        model.loadMore()
        advanceUntilIdle()
        // canLoadMoreAllSongs is now false (short page) - no third call, no growth.
        assertEquals(firstPageSize + 1, model.uiState.value.rows.size)
        assertEquals(2, calls)
    }

    @Test
    fun `selection survives switching between sort modes`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1"), track("2")) }
        advanceUntilIdle()
        model.toggleTrack("1")

        model.onSortOrderSelected(SortOrder.RANDOM)
        advanceUntilIdle()

        assertEquals(1, model.uiState.value.selectedCount)
    }

    @Test
    fun `by-artist and by-genre only stage the sort order until a selection is made`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        advanceUntilIdle()

        model.onSortOrderSelected(SortOrder.BY_ARTIST)

        // Still showing the previous (All Songs) rows - no load happened yet.
        assertEquals(SortOrder.BY_ARTIST, model.uiState.value.sortOrder)
        assertEquals(listOf("1"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `selecting an artist loads that artist's songs`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        model.artistSongsLoader = { artistId, _, _, offset, _ ->
            if (offset == 0 && artistId == "ar1") listOf(track("a1"), track("a2")) else emptyList()
        }
        advanceUntilIdle()

        model.selectArtist("ar1", "Miles Davis")
        advanceUntilIdle()

        assertEquals(listOf("a1", "a2"), model.uiState.value.rows.map { it.id })
        assertEquals(SortOrder.BY_ARTIST, model.uiState.value.sortOrder)
    }

    @Test
    fun `selecting a genre loads that genre's songs`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        model.genreSongsLoader = { genre, _, offset ->
            if (offset == 0 && genre == "Jazz") listOf(track("g1")) else emptyList()
        }
        advanceUntilIdle()

        model.selectGenre("Jazz")
        advanceUntilIdle()

        assertEquals(listOf("g1"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `submitting a search replaces the list and disables load-more`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1"), track("2")) }
        advanceUntilIdle()
        model.searchLoader = { query -> if (query == "blue") listOf(track("s1")) else emptyList() }

        model.onQueryChange("blue")
        model.onSearchSubmit()
        advanceUntilIdle()

        assertEquals(listOf("s1"), model.uiState.value.rows.map { it.id })

        // loadMore is a no-op while showing search results.
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf("s1"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `a blank search submit reverts to the current sort mode's list`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        advanceUntilIdle()
        model.onQueryChange("")

        model.onSearchSubmit()
        advanceUntilIdle()

        assertEquals(listOf("1"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `loadArtists and loadGenres are plain pass-throughs`() = runTest {
        val model = vm()
        model.artistsLoader = { listOf(Artist(id = "a1", name = "Radiohead")) }
        model.genresLoader = { listOf(Genre(index = "g1", name = "Rock")) }

        assertEquals("Radiohead", model.loadArtists().single().name)
        assertEquals("Rock", model.loadGenres().single().name)
    }

    @Test
    fun `save calls the create seam and toggles isSaving around it`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        advanceUntilIdle()
        var savedName: String? = null
        var savedTracks: List<Track>? = null
        model.playlistCreator = { name, tracks ->
            assertTrue(model.uiState.value.isSaving)
            savedName = name
            savedTracks = tracks
        }

        model.save("My Mix", listOf(track("1")))

        assertEquals("My Mix", savedName)
        assertEquals(listOf("1"), savedTracks?.map { it.id })
        assertFalse(model.uiState.value.isSaving)
    }

    @Test
    fun `a failed save resets isSaving but the selection is untouched for a retry`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        advanceUntilIdle()
        model.toggleTrack("1")
        model.playlistCreator = { _, _ -> throw IOException("network dropped") }

        try {
            model.save("My Mix", model.selectedTracksSnapshot()!!)
        } catch (expected: IOException) {
            // expected - the Fragment's toastingExceptionHandler reports it
        }

        assertFalse(model.uiState.value.isSaving)
        assertEquals(1, model.uiState.value.selectedCount)
        assertEquals(listOf("1"), model.selectedTracksSnapshot()?.map { it.id })
    }

    @Test
    fun `selectedTracksSnapshot is null when nothing is selected`() = runTest {
        val model = vm { _, _, _ -> listOf(track("1")) }
        advanceUntilIdle()

        assertNull(model.selectedTracksSnapshot())
    }
}
