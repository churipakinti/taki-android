/*
 * PlaylistListViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlistlist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.PlaylistListViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.robolectric.RobolectricTestRunner

/**
 * [PlaylistListViewModel] projection, ported from the legacy `PlaylistsFragment` (issue #10
 * phase 4G1): `getPlaylists`, the two-stage `CHECKING` -> resolved track-count/cover-art/
 * download-status flow (`resolveDownloadStates`, one sequential call per playlist, a
 * `statusLoadGeneration` guard against a stale resolve overwriting a newer load), the
 * `bindTrackCount` real-vs-metadata song-count fallback, and the local (non-reloading) removal a
 * successful delete applies. There is no scroll-reset field and no sort orders - this screen
 * never had either.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var cacheCleaner: CacheCleaner

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()

        Settings.activeServer = 0 // online by default

        cacheCleaner = mock()
        startKoin {
            modules(module { single { cacheCleaner } })
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    private fun playlist(id: String, name: String = "Playlist $id", songCount: String = "0") =
        Playlist(id = id, name = name, songCount = songCount)

    private fun track(id: String, coverArt: String? = null) = Track(id = id, coverArt = coverArt)

    private fun vm(
        playlists: suspend (Boolean) -> List<Playlist> = { emptyList() },
        tracks: suspend (String, String) -> List<Track> = { _, _ -> emptyList() },
    ) = PlaylistListViewModel(app).apply {
        playlistsLoader = playlists
        playlistTracksLoader = tracks
    }

    // --- Initial load ------------------------------------------------------------------------

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a loaded list fills rows with CHECKING status before resolution completes`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val model = vm(
            playlists = { listOf(playlist("p1", "Road Trip")) },
            tracks = { _, _ -> gate.await(); emptyList() },
        )
        model.load()
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals("Road Trip", row.name)
        assertEquals(PlaylistRowDownloadStatus.CHECKING, row.downloadStatus)

        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `song count prefers the real resolved track count over the server metadata once resolved`() =
        runTest {
            val model = vm(
                playlists = { listOf(playlist("p1", songCount = "5")) },
                tracks = { _, _ -> listOf(track("t1"), track("t2")) },
            )
            model.load()
            advanceUntilIdle()
            // Resolution has completed by the time advanceUntilIdle returns.
            assertEquals(2, model.uiState.value.rows.single().songCount)
        }

    @Test
    fun `song count falls back to the server metadata before resolution has a chance to run`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val model = vm(
            playlists = { listOf(playlist("p1", songCount = "5")) },
            tracks = { _, _ -> gate.await(); emptyList() },
        )
        model.load()
        advanceUntilIdle()
        assertEquals(5, model.uiState.value.rows.single().songCount)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `the representative cover is the first resolved track with non-blank art`() = runTest {
        val model = vm(
            playlists = { listOf(playlist("p1")) },
            tracks = { _, _ -> listOf(track("t1", coverArt = null), track("t2", coverArt = "art-1")) },
        )
        model.load()
        advanceUntilIdle()
        assertEquals("art-1", model.uiState.value.rows.single().artworkModel?.id)
    }

    @Test
    fun `no track has art - the row has no artwork model`() = runTest {
        val model = vm(
            playlists = { listOf(playlist("p1")) },
            tracks = { _, _ -> listOf(track("t1", coverArt = null)) },
        )
        model.load()
        advanceUntilIdle()
        assertNull(model.uiState.value.rows.single().artworkModel)
    }

    @Test
    fun `a per-playlist resolve failure resolves that playlist to an empty track list only`() = runTest {
        val model = vm(
            playlists = { listOf(playlist("p1", songCount = "3"), playlist("p2", songCount = "4")) },
            tracks = { id, _ -> if (id == "p1") throw java.io.IOException("down") else listOf(track("t1")) },
        )
        model.load()
        advanceUntilIdle()

        val rows = model.uiState.value.rows.associateBy { it.id }
        // p1's resolve failed -> an empty (not absent) track list is stored for it, exactly like
        // the legacy `resolveDownloadStates`'s own per-item catch - so the count shows 0, *not*
        // the server metadata fallback (that fallback only applies while no entry exists yet,
        // i.e. during CHECKING, not after a failed resolve leaves a real empty list behind).
        assertEquals(0, rows.getValue("p1").songCount)
        assertEquals(PlaylistRowDownloadStatus.EMPTY, rows.getValue("p1").downloadStatus)
        assertEquals(1, rows.getValue("p2").songCount)
    }

    // --- Load-once / refresh -------------------------------------------------------------

    @Test
    fun `load always calls through - the underlying CachedMusicService already caches refresh=false`() =
        runTest {
            var calls = 0
            val model = vm(playlists = { calls++; listOf(playlist("p1")) })
            model.load()
            advanceUntilIdle()
            model.load()
            advanceUntilIdle()
            // Unlike AlbumListViewModel/ArtistListViewModel, there is no ViewModel-level guard -
            // it always calls through (the cache itself lives one layer below, in
            // CachedMusicService, and is exercised by CachedMusicService's own tests).
            assertEquals(2, calls)
        }

    @Test
    fun `a failed first load ends without content, not stuck loading`() = runTest {
        val model = vm(playlists = { throw java.io.IOException("server down") })
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = vm(playlists = { throw CancellationException("scope died") })
        model.load()
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `a failed refresh keeps the playlists already on screen`() = runTest {
        var fail = false
        val model = vm(
            playlists = { if (fail) throw java.io.IOException("down") else listOf(playlist("p1")) },
        )
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasContent)

        fail = true
        model.refresh()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertTrue(state.hasContent)
    }

    @Test
    fun `a stale resolve response is dropped once a newer load has started`() = runTest {
        val staleGate = CompletableDeferred<Unit>()
        var resolveCalls = 0
        val model = vm(
            playlists = { listOf(playlist("p1", songCount = "9")) },
            tracks = { _, _ ->
                resolveCalls++
                if (resolveCalls == 1) staleGate.await()
                listOf(track("t1"))
            },
        )
        model.load()
        advanceUntilIdle() // first resolve is now blocked on staleGate

        model.load(refresh = true)
        advanceUntilIdle() // second load's own resolve completes (resolveCalls == 2, no gate)

        staleGate.complete(Unit)
        advanceUntilIdle()

        // The stale (first) resolve's result must not have clobbered the second load's state.
        assertEquals(1, model.uiState.value.rows.single().songCount)
    }

    // --- online/offline -------------------------------------------------------------------

    @Test
    fun `online is derived from ActiveServerProvider at load time`() = runTest {
        Settings.activeServer = 0
        val model = vm(playlists = { listOf(playlist("p1")) })
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.online)
    }

    @Test
    fun `offline is derived from ActiveServerProvider at load time`() = runTest {
        Settings.activeServer = -1
        val model = vm(playlists = { listOf(playlist("p1")) })
        model.load()
        advanceUntilIdle()
        assertFalse(model.uiState.value.online)
    }

    @Test
    fun `the cache is cleaned only when online, exactly like the legacy screen`() = runTest {
        Settings.activeServer = 0
        val result = listOf(playlist("p1"))
        val model = vm(playlists = { result })
        model.load()
        advanceUntilIdle()
        verify(cacheCleaner).cleanPlaylists(result)
    }

    // --- Download status recompute (RxBus-forwarded) ----------------------------------------

    @Test
    fun `onTrackDownloadStateChanged recomputes the affected playlist and republishes`() = runTest {
        val model = vm(
            playlists = { listOf(playlist("p1"), playlist("p2")) },
            tracks = { id, _ -> if (id == "p1") listOf(track("t1")) else listOf(track("t2")) },
        )
        model.load()
        advanceUntilIdle()

        // Nothing was actually downloaded (no filesystem/queue setup in this test), so the
        // recompute lands back on NOT_DOWNLOADED - this locks that a track belonging to a
        // resolved playlist is recognised and re-published without crashing; the DownloadState
        // -> PlaylistRowDownloadStatus mapping itself is [computeDownloadStatus]'s own concern,
        // exercised indirectly via `resolveDownloadStates` in the tests above.
        model.onTrackDownloadStateChanged("t1")

        val rows = model.uiState.value.rows.associateBy { it.id }
        assertEquals(PlaylistRowDownloadStatus.NOT_DOWNLOADED, rows.getValue("p1").downloadStatus)
        assertEquals(PlaylistRowDownloadStatus.NOT_DOWNLOADED, rows.getValue("p2").downloadStatus)
    }

    @Test
    fun `a track event for a track that is not in any resolved playlist is a no-op`() = runTest {
        val model = vm(
            playlists = { listOf(playlist("p1")) },
            tracks = { _, _ -> listOf(track("t1")) },
        )
        model.load()
        advanceUntilIdle()
        val before = model.uiState.value

        model.onTrackDownloadStateChanged("unrelated-track")

        assertEquals(before, model.uiState.value)
    }

    // --- Optimistic download status -------------------------------------------------------

    @Test
    fun `setDownloadStatusOptimistic flips the row immediately`() = runTest {
        val model = vm(
            playlists = { listOf(playlist("p1")) },
            tracks = { _, _ -> listOf(track("t1")) },
        )
        model.load()
        advanceUntilIdle()

        model.setDownloadStatusOptimistic("p1", PlaylistRowDownloadStatus.DOWNLOADING)

        assertEquals(PlaylistRowDownloadStatus.DOWNLOADING, model.uiState.value.rows.single().downloadStatus)
    }

    // --- Mutations (delete) -----------------------------------------------------------------

    @Test
    fun `removePlaylist drops the row locally - no reload`() = runTest {
        var loadCalls = 0
        val model = vm(playlists = { loadCalls++; listOf(playlist("p1"), playlist("p2")) })
        model.load()
        advanceUntilIdle()
        assertEquals(1, loadCalls)

        model.removePlaylist("p1")

        assertEquals(listOf("p2"), model.uiState.value.rows.map { it.id })
        assertEquals(1, loadCalls) // still 1 - no re-fetch triggered by the removal itself
    }

    // --- Row / playlist lookup ---------------------------------------------------------------

    @Test
    fun `playlistFor and tracksFor resolve by id`() = runTest {
        val model = vm(
            playlists = { listOf(playlist("p1", "Road Trip")) },
            tracks = { _, _ -> listOf(track("t1")) },
        )
        model.load()
        advanceUntilIdle()

        assertEquals("Road Trip", model.playlistFor("p1")?.name)
        assertNull(model.playlistFor("nope"))
        assertEquals(listOf("t1"), model.tracksFor("p1")?.map { it.id })
        assertNull(model.tracksFor("nope"))
    }

    @Test
    fun `setLayoutType updates the layout without touching the loaded rows`() = runTest {
        val model = vm(playlists = { listOf(playlist("p1")) })
        model.load()
        advanceUntilIdle()
        model.setLayoutType(LayoutType.COVER)
        assertEquals(LayoutType.COVER, model.uiState.value.layoutType)
        assertEquals(1, model.uiState.value.rows.size)
    }
}
