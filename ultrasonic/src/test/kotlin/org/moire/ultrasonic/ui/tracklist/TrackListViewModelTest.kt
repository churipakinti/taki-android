/*
 * TrackListViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.tracklist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
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
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.TrackListViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner

/**
 * [TrackListViewModel] projection, ported from `TrackCollectionModel`/`TrackCollectionFragment`
 * (issue #10 phase 4F1): the "Songs" (`libraryRoot`) filterable browser's five sub-modes (All
 * Songs/Random/By Artist/By Genre/Liked, each with its own paging bookkeeping ported verbatim)
 * and the dedicated Liked Songs (`getStarred`) destination's flat, unpaged, no-controls list.
 * Deliberately has no load-once-across-back-navigation tests: the class exists specifically
 * because the legacy screen never had that guard - every [org.moire.ultrasonic.model.TrackListViewModel.load]
 * always re-fetches.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TrackListViewModelTest {

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

    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String? = null,
        album: String? = null,
        artistId: String? = null,
        starred: Boolean = false,
    ) = Track(id = id, title = title, artist = artist, album = album, artistId = artistId, starred = starred)

    private fun vm(
        libraryRoot: Boolean = true,
        getStarred: Boolean = false,
        allSongs: suspend (Int, Int, String?) -> List<Track> = { _, _, _ -> emptyList() },
        starred: suspend () -> List<Track> = { emptyList() },
        random: suspend (Int) -> List<Track> = { emptyList() },
        artistSongs: suspend (String, String, Int, Int, String?) -> List<Track> = { _, _, _, _, _ -> emptyList() },
        genreSongs: suspend (String, Int, Int) -> List<Track> = { _, _, _ -> emptyList() },
        artists: suspend () -> List<ArtistOrIndex> = { emptyList() },
        genres: suspend () -> List<Genre> = { emptyList() },
    ) = TrackListViewModel(app).apply {
        allSongsLoader = allSongs
        starredLoader = starred
        randomLoader = random
        artistSongsLoader = artistSongs
        genreSongsLoader = genreSongs
        artistsLoader = artists
        genresLoader = genres
        initialize(libraryRoot, getStarred)
    }

    // --- Initial state / initialize() -------------------------------------------------------

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `the Songs destination shows controls and starts on ALL_SONGS`() {
        val state = vm(libraryRoot = true, getStarred = false).uiState.value
        assertTrue(state.showControls)
        assertFalse(state.showHeart)
        assertEquals(SortOrder.ALL_SONGS, state.sortOrder)
        assertEquals(
            listOf(SortOrder.ALL_SONGS, SortOrder.RANDOM, SortOrder.BY_ARTIST, SortOrder.BY_GENRE, SortOrder.STARRED),
            state.availableSortOrders,
        )
    }

    @Test
    fun `the Liked Songs destination has no controls and starts on STARRED`() {
        val state = vm(libraryRoot = false, getStarred = true).uiState.value
        assertFalse(state.showControls)
        assertTrue(state.showHeart)
        assertEquals(SortOrder.STARRED, state.sortOrder)
        assertTrue(state.availableSortOrders.isEmpty())
    }

    @Test
    fun `offline hides STARRED from the Songs sort menu`() {
        Settings.activeServer = -1
        val state = vm(libraryRoot = true).uiState.value
        assertFalse(SortOrder.STARRED in state.availableSortOrders)
    }

    // --- All Songs ---------------------------------------------------------------------------

    @Test
    fun `All Songs loads rows with title and an artist times album subtitle`() = runTest {
        val model = vm(
            allSongs = { _, _, _ -> listOf(track("t1", title = "OK Computer", artist = "Radiohead", album = "OK Computer")) },
        )
        model.load()
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals("OK Computer", row.title)
        assertEquals("Radiohead · OK Computer", row.subtitle)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test
    fun `All Songs always re-fetches - there is no load-once guard`() = runTest {
        var calls = 0
        val model = vm(allSongs = { _, _, _ -> calls++; listOf(track("t1")) })
        model.load()
        advanceUntilIdle()
        model.load()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `All Songs requests Settings-MAX_SONGS per page`() = runTest {
        var requestedCount = -1
        val model = vm(allSongs = { count, _, _ -> requestedCount = count; emptyList() })
        model.load()
        advanceUntilIdle()
        assertEquals(Settings.MAX_SONGS, requestedCount)
    }

    @Test
    fun `All Songs loadMore appends at an accumulating offset and de-dupes`() = runTest {
        val offsets = mutableListOf<Int>()
        val model = vm(
            allSongs = { count, offset, _ -> offsets += offset; List(count) { track("t-$offset-$it") } },
        )
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, Settings.MAX_SONGS), offsets)
        assertEquals(Settings.MAX_SONGS * 2, model.uiState.value.rows.size)
    }

    @Test
    fun `All Songs loadMore stops once a page comes back short`() = runTest {
        var calls = 0
        val model = vm(
            allSongs = { count, offset, _ ->
                calls++
                if (offset == 0) List(count) { track("t$it") } else listOf(track("short"))
            },
        )
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        val callsAfterShortPage = calls
        model.loadMore()
        advanceUntilIdle()

        assertEquals(callsAfterShortPage, calls)
    }

    @Test
    fun `a failed load ends without content, not stuck loading`() = runTest {
        val model = vm(allSongs = { _, _, _ -> throw java.io.IOException("down") })
        model.load()
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.loadFailed)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = vm(allSongs = { _, _, _ -> throw CancellationException("scope died") })
        model.load()
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    // --- Liked Songs ---------------------------------------------------------------------------

    @Test
    fun `Liked Songs loads via the starred loader, unpaged`() = runTest {
        var allSongsCalls = 0
        val model = vm(
            libraryRoot = false,
            getStarred = true,
            allSongs = { _, _, _ -> allSongsCalls++; emptyList() },
            starred = { listOf(track("t1", starred = true), track("t2", starred = true)) },
        )
        model.load()
        advanceUntilIdle()

        assertEquals(0, allSongsCalls)
        assertEquals(2, model.uiState.value.rows.size)
        assertTrue(model.uiState.value.rows.all { it.liked })
    }

    @Test
    fun `Liked Songs loadMore is a no-op - getStarred never paged`() = runTest {
        var starredCalls = 0
        val model = vm(libraryRoot = false, getStarred = true, starred = { starredCalls++; listOf(track("t1")) })
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(1, starredCalls)
    }

    @Test
    fun `unliking a track in Liked Songs removes it immediately (optimistic)`() = runTest {
        val model = vm(
            libraryRoot = false,
            getStarred = true,
            starred = { listOf(track("t1", starred = true), track("t2", starred = true)) },
        )
        model.load()
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.rows.size)

        val newStarred = model.toggleHeartOptimistic("t1")

        assertEquals(false, newStarred)
        assertEquals(listOf("t2"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `liking a track that is already visible does not remove it`() = runTest {
        val model = vm(
            libraryRoot = false,
            getStarred = true,
            starred = { listOf(track("t1", starred = false)) },
        )
        model.load()
        advanceUntilIdle()

        val newStarred = model.toggleHeartOptimistic("t1")

        assertEquals(true, newStarred)
        assertEquals(1, model.uiState.value.rows.size)
        assertTrue(model.uiState.value.rows.single().liked)
    }

    @Test
    fun `toggling a heart on the Songs (All Songs) screen never removes the row`() = runTest {
        // showHeart is false here, but the underlying toggle can still be invoked defensively -
        // mode is ALL_SONGS, not STARRED, so the legacy Fragment's removal guard never applies.
        val model = vm(
            libraryRoot = true,
            allSongs = { _, _, _ -> listOf(track("t1", starred = true)) },
        )
        model.load()
        advanceUntilIdle()

        model.toggleHeartOptimistic("t1")

        assertEquals(1, model.uiState.value.rows.size)
    }

    // --- Random / By Artist / By Genre (reached via the Songs sort menu only) -----------------

    @Test
    fun `selecting Random from the Songs sort menu switches data and label`() = runTest {
        val model = vm(random = { count -> List(count) { track("r$it") } })
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.RANDOM)
        advanceUntilIdle()

        assertEquals(SortOrder.RANDOM, model.uiState.value.sortOrder)
        assertEquals(Settings.MAX_SONGS, model.uiState.value.rows.size)
    }

    @Test
    fun `selecting BY_ARTIST only updates the chip - it does not fetch until an artist is chosen`() = runTest {
        var allSongsCalls = 0
        val model = vm(allSongs = { _, _, _ -> allSongsCalls++; emptyList() })
        model.load()
        advanceUntilIdle()
        model.beginArtistSort()

        assertEquals(SortOrder.BY_ARTIST, model.uiState.value.sortOrder)
        assertEquals(1, allSongsCalls) // only the initial load
    }

    @Test
    fun `loadArtists sorts case-and-locale-aware by name`() = runTest {
        val model = vm(artists = { listOf(Artist(id = "a1", name = "the Kinks"), Artist(id = "a2", name = "ABBA")) })
        val artists = model.loadArtists()
        assertEquals(listOf("ABBA", "the Kinks"), artists.map { it.name })
    }

    @Test
    fun `selectArtist fetches that artist's paged songs`() = runTest {
        val model = vm(
            artistSongs = { artistId, artistName, _, _, _ ->
                assertEquals("a1", artistId)
                assertEquals("Bill Evans", artistName)
                listOf(track("t1", artist = "Bill Evans", artistId = "a1"))
            },
        )
        model.selectArtist("a1", "Bill Evans")
        advanceUntilIdle()

        assertEquals(listOf("t1"), model.uiState.value.rows.map { it.id })
        assertEquals(SortOrder.BY_ARTIST, model.uiState.value.sortOrder)
    }

    @Test
    fun `selectGenre fetches that genre's paged songs`() = runTest {
        val model = vm(
            genreSongs = { genre, _, _ -> assertEquals("Jazz", genre); listOf(track("t1")) },
        )
        model.selectGenre("Jazz")
        advanceUntilIdle()

        assertEquals(listOf("t1"), model.uiState.value.rows.map { it.id })
        assertEquals(SortOrder.BY_GENRE, model.uiState.value.sortOrder)
    }

    @Test
    fun `switching from By Artist back to All Songs clears the selected artist`() = runTest {
        val requestedArtistIds = mutableListOf<String?>()
        val model = vm(
            artistSongs = { artistId, _, _, _, _ -> requestedArtistIds += artistId; listOf(track("a-track")) },
            allSongs = { _, _, _ -> listOf(track("all-track")) },
        )
        model.selectArtist("a1", "Bill Evans")
        advanceUntilIdle()
        model.setSortOrder(SortOrder.ALL_SONGS)
        advanceUntilIdle()

        assertEquals(listOf("all-track"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `setSortOrder is a no-op when the mode has not actually changed`() = runTest {
        var calls = 0
        val model = vm(allSongs = { _, _, _ -> calls++; emptyList() })
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.ALL_SONGS)
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    // --- Row lookup --------------------------------------------------------------------------

    @Test
    fun `itemFor and tracksSnapshot resolve the currently loaded tracks`() = runTest {
        val model = vm(allSongs = { _, _, _ -> listOf(track("t1", title = "Bravo")) })
        model.load()
        advanceUntilIdle()

        assertEquals("Bravo", model.itemFor("t1")?.title)
        assertNull(model.itemFor("nope"))
        assertEquals(1, model.tracksSnapshot().size)
    }
}
