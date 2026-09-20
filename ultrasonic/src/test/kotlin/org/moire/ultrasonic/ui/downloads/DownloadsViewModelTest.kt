/*
 * DownloadsViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.downloads

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.DownloadsEvent
import org.moire.ultrasonic.model.DownloadsViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * [DownloadsViewModel] projection, ported from the legacy `DownloadsFragment` +
 * `TrackCollectionModel.getDownloadedAlbums` (issue #10 phase 4G3): a flat list of downloaded
 * albums from the local database (identical online/offline), and the immediate, unconfirmed
 * remove-album action. The loaders are test seams - the database ordering/count logic itself is
 * the loader's job and unchanged.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DownloadsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun album(id: String, title: String, artist: String? = "Artist", songs: Long = 3) =
        Album(id = id, title = title, artist = artist, songCount = songs)

    private fun track(id: String) = Track(id = id)

    private fun vm(
        albums: suspend () -> List<Album> = { emptyList() },
        tracks: suspend (String) -> List<Track> = { emptyList() },
        deleter: suspend (List<Track>) -> Unit = {},
    ) = DownloadsViewModel(app).apply {
        mappingDispatcher = dispatcher
        albumsLoader = albums
        albumTracksLoader = tracks
        trackDeleter = deleter
    }

    // --- Initial load ------------------------------------------------------------------------

    @Test
    fun `the default state is loading with no rows`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
        assertFalse(state.showEmpty)
    }

    @Test
    fun `a loaded list publishes a row per album with title, artist and local song count`() = runTest {
        val model = vm(albums = { listOf(album("a1", "Abbey Road", "The Beatles", 17)) })
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        val row = state.rows.single()
        assertEquals("a1", row.id)
        assertEquals("Abbey Road", row.title)
        assertEquals("The Beatles", row.artist)
        assertEquals(17, row.songCount)
    }

    @Test
    fun `album order is preserved exactly as the loader returns it`() = runTest {
        // The loader (getDownloadedAlbums) already sorts by lower-cased title; the ViewModel
        // must not re-sort.
        val model = vm(albums = { listOf(album("1", "beta"), album("2", "Alpha"), album("3", "gamma")) })
        model.load()
        advanceUntilIdle()
        assertEquals(listOf("1", "2", "3"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `an album with no cover art has no artwork model`() = runTest {
        val model = vm(albums = { listOf(album("a1", "X")) })
        model.load()
        advanceUntilIdle()
        assertNull(model.uiState.value.rows.single().artworkModel)
    }

    @Test
    fun `a missing title and song count fall back to empty and zero`() = runTest {
        val model = vm(albums = { listOf(Album(id = "a1")) })
        model.load()
        advanceUntilIdle()
        val row = model.uiState.value.rows.single()
        assertEquals("", row.title)
        assertEquals(0, row.songCount)
    }

    @Test
    fun `nothing downloaded publishes the empty state`() = runTest {
        val model = vm(albums = { emptyList() })
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.showEmpty)
    }

    // --- Refresh / re-query semantics --------------------------------------------------------

    @Test
    fun `load re-queries every time - no load-once guard, matching the legacy per-view reload`() =
        runTest {
            var calls = 0
            val model = vm(albums = { calls++; listOf(album("a$calls", "T$calls")) })
            model.load()
            advanceUntilIdle()
            model.load() // e.g. back-navigation from a downloaded album recreates the view
            advanceUntilIdle()
            assertEquals(2, calls)
            assertEquals("a2", model.uiState.value.rows.single().id)
        }

    @Test
    fun `refresh re-queries the local database`() = runTest {
        var calls = 0
        val model = vm(albums = { calls++; listOf(album("a1", "T")) })
        model.load()
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `a refresh keeps the previous rows visible while it loads`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var first = true
        val model = vm(albums = {
            if (first) {
                first = false
            } else {
                gate.await()
            }
            listOf(album("a1", "T"))
        })
        model.load()
        advanceUntilIdle()

        model.refresh()
        advanceUntilIdle()
        assertTrue(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.hasContent) // pull-to-refresh spinner, not a blank screen

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
    }

    // --- Errors ------------------------------------------------------------------------------

    @Test
    fun `a failing load stops loading and emits an error event`() = runTest {
        val model = vm(albums = { error("db down") })
        val events = mutableListOf<DownloadsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events.add(it) } }

        model.load()
        advanceUntilIdle()

        assertFalse(model.uiState.value.isLoading)
        val error = events.single() as DownloadsEvent.Error
        assertEquals("db down", error.cause.message)
    }

    // --- Remove album ------------------------------------------------------------------------

    @Test
    fun `removing an album deletes exactly its local tracks, re-queries, and reports the count`() =
        runTest {
            var remaining = listOf(album("a1", "One"), album("a2", "Two"))
            val deleted = mutableListOf<List<Track>>()
            val model = vm(
                albums = { remaining },
                tracks = { id -> if (id == "a1") listOf(track("t1"), track("t2")) else emptyList() },
                deleter = { deleted.add(it); remaining = remaining.filterNot { a -> a.id == "a1" } },
            )
            val events = mutableListOf<DownloadsEvent>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events.add(it) } }
            model.load()
            advanceUntilIdle()

            model.removeAlbum("a1")
            advanceUntilIdle()

            assertEquals(listOf("t1", "t2"), deleted.single().map { it.id })
            assertEquals(listOf("a2"), model.uiState.value.rows.map { it.id })
            assertEquals(DownloadsEvent.Removed(2), events.single())
        }

    @Test
    fun `removing the last album leaves the empty state`() = runTest {
        var remaining = listOf(album("a1", "One"))
        val model = vm(
            albums = { remaining },
            tracks = { listOf(track("t1")) },
            deleter = { remaining = emptyList() },
        )
        model.load()
        advanceUntilIdle()
        model.removeAlbum("a1")
        advanceUntilIdle()
        assertTrue(model.uiState.value.showEmpty)
    }

    @Test
    fun `a rapid second tap on the same album's trash while it is removing is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var deleteCalls = 0
        val model = vm(
            albums = { listOf(album("a1", "One")) },
            tracks = { listOf(track("t1")) },
            deleter = { deleteCalls++; gate.await() },
        )
        model.load()
        advanceUntilIdle()

        model.removeAlbum("a1")
        advanceUntilIdle()
        model.removeAlbum("a1")
        advanceUntilIdle()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, deleteCalls)
    }

    @Test
    fun `a failing delete emits an error event and leaves the list untouched`() = runTest {
        val model = vm(
            albums = { listOf(album("a1", "One")) },
            tracks = { listOf(track("t1")) },
            deleter = { error("disk full") },
        )
        val events = mutableListOf<DownloadsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.events.collect { events.add(it) } }
        model.load()
        advanceUntilIdle()

        model.removeAlbum("a1")
        advanceUntilIdle()

        assertEquals(listOf("a1"), model.uiState.value.rows.map { it.id })
        assertEquals("disk full", (events.single() as DownloadsEvent.Error).cause.message)
    }

    @Test
    fun `an album can be removed again after a failed removal`() = runTest {
        var fail = true
        var deleteCalls = 0
        val model = vm(
            albums = { listOf(album("a1", "One")) },
            tracks = { listOf(track("t1")) },
            deleter = { deleteCalls++; if (fail) error("boom") },
        )
        model.load()
        advanceUntilIdle()
        model.removeAlbum("a1")
        advanceUntilIdle()
        fail = false
        model.removeAlbum("a1")
        advanceUntilIdle()
        assertEquals(2, deleteCalls)
    }
}
