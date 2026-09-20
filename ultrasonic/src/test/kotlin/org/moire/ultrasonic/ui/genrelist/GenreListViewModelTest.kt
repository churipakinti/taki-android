/*
 * GenreListViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.genrelist

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
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.GenreListViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.robolectric.RobolectricTestRunner

/**
 * [GenreListViewModel] projection, ported from the legacy `SelectGenreFragment` (issue #10
 * phase 4G2): `getGenres` (already cache-backed server-side), and the visibility-driven,
 * semaphore-gated, generation-guarded representative-cover resolve (`onCoverNeeded`, only ever
 * invoked per-row by the Screen - never eagerly for the whole list).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class GenreListViewModelTest {

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

    private fun genre(name: String) = Genre(index = name.take(1), name = name)

    private fun track(id: String, coverArt: String? = null) = Track(id = id, coverArt = coverArt)

    private fun vm(
        genres: suspend (Boolean) -> List<Genre> = { emptyList() },
        cover: suspend (String) -> Track? = { null },
    ) = GenreListViewModel(app).apply {
        genresLoader = genres
        genreCoverLoader = cover
    }

    // --- Initial load ------------------------------------------------------------------------

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a loaded genre list publishes rows with no artwork before any cover is requested`() = runTest {
        val model = vm(genres = { listOf(genre("Rock"), genre("Jazz")) })
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Rock", "Jazz"), state.rows.map { it.name })
        assertTrue(state.rows.all { it.artworkModel == null })
    }

    @Test
    fun `genre order is preserved exactly as returned by the loader`() = runTest {
        // CachedMusicService already sorts alphabetically server-side - the ViewModel must not
        // re-sort, or a genuinely custom server order would be silently overridden twice.
        val model = vm(genres = { listOf(genre("Zydeco"), genre("Ambient"), genre("Metal")) })
        model.load()
        advanceUntilIdle()
        assertEquals(listOf("Zydeco", "Ambient", "Metal"), model.uiState.value.rows.map { it.name })
    }

    @Test
    fun `an empty genre list is published as-is`() = runTest {
        val model = vm(genres = { emptyList() })
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.showEmpty)
    }

    // --- Cover resolution (onCoverNeeded, visibility-driven) ----------------------------------

    @Test
    fun `onCoverNeeded resolves the representative cover for that genre only`() = runTest {
        val model = vm(
            genres = { listOf(genre("Rock"), genre("Jazz")) },
            cover = { name -> if (name == "Rock") track("t1", coverArt = "art-1") else null },
        )
        model.load()
        advanceUntilIdle()

        model.onCoverNeeded("Rock")
        advanceUntilIdle()

        val rows = model.uiState.value.rows.associateBy { it.name }
        assertEquals("art-1", rows.getValue("Rock").artworkModel?.id)
        assertNull(rows.getValue("Jazz").artworkModel)
    }

    @Test
    fun `a genre whose cover loader finds nothing stays permanently without artwork, not re-requested`() =
        runTest {
            var calls = 0
            val model = vm(
                genres = { listOf(genre("Rock")) },
                cover = { calls++; null },
            )
            model.load()
            advanceUntilIdle()

            model.onCoverNeeded("Rock")
            advanceUntilIdle()
            model.onCoverNeeded("Rock") // e.g. the cell re-enters composition after a scroll
            advanceUntilIdle()

            assertNull(model.uiState.value.rows.single().artworkModel)
            assertEquals(1, calls) // de-duped, exactly like the legacy requestedCovers set
        }

    @Test
    fun `a repeat onCoverNeeded call for an already-resolved genre does not re-fetch`() = runTest {
        var calls = 0
        val model = vm(
            genres = { listOf(genre("Rock")) },
            cover = { calls++; track("t1", coverArt = "art-1") },
        )
        model.load()
        advanceUntilIdle()
        model.onCoverNeeded("Rock")
        advanceUntilIdle()
        model.onCoverNeeded("Rock")
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `a stale cover resolve is dropped once a refresh has reset the generation`() = runTest {
        val staleGate = CompletableDeferred<Unit>()
        var resolveCalls = 0
        val model = vm(
            genres = { listOf(genre("Rock")) },
            cover = {
                resolveCalls++
                if (resolveCalls == 1) staleGate.await()
                track("stale", coverArt = "stale-art")
            },
        )
        model.load()
        advanceUntilIdle()
        model.onCoverNeeded("Rock") // blocks on staleGate

        model.refresh() // resets the generation and clears requestedCovers/coverTracks
        advanceUntilIdle()
        model.onCoverNeeded("Rock") // resolves immediately (resolveCalls == 2, no gate)
        advanceUntilIdle()

        staleGate.complete(Unit)
        advanceUntilIdle()

        // The stale (first, pre-refresh) resolve must not have clobbered the post-refresh state.
        assertEquals("stale-art", model.uiState.value.rows.single().artworkModel?.id)
    }

    // --- Load-once / refresh -------------------------------------------------------------

    @Test
    fun `load always calls through - the underlying CachedMusicService already caches refresh=false`() =
        runTest {
            var calls = 0
            val model = vm(genres = { calls++; listOf(genre("Rock")) })
            model.load()
            advanceUntilIdle()
            model.load()
            advanceUntilIdle()
            // No ViewModel-level load-once guard, matching PlaylistListViewModel - the cache
            // itself lives one layer below, in CachedMusicService.
            assertEquals(2, calls)
        }

    @Test
    fun `a failed first load ends without content, not stuck loading`() = runTest {
        val model = vm(genres = { throw java.io.IOException("server down") })
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = vm(genres = { throw CancellationException("scope died") })
        model.load()
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `a failed refresh keeps the genres already on screen`() = runTest {
        var fail = false
        val model = vm(genres = { if (fail) throw java.io.IOException("down") else listOf(genre("Rock")) })
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
    fun `a refresh clears previously resolved covers, matching the legacy clearCovers flag`() = runTest {
        val model = vm(
            genres = { listOf(genre("Rock")) },
            cover = { track("t1", coverArt = "art-1") },
        )
        model.load()
        advanceUntilIdle()
        model.onCoverNeeded("Rock")
        advanceUntilIdle()
        assertEquals("art-1", model.uiState.value.rows.single().artworkModel?.id)

        model.refresh()
        advanceUntilIdle()
        // Cover map was cleared by the refresh; not yet re-requested for the new row instance.
        assertNull(model.uiState.value.rows.single().artworkModel)
    }

    @Test
    fun `a refresh bumps coverGeneration in the published state - the Screen keys its fetch effect on it`() =
        runTest {
            val model = vm(genres = { listOf(genre("Rock")) })
            model.load()
            advanceUntilIdle()
            val before = model.uiState.value.coverGeneration

            model.refresh()
            advanceUntilIdle()

            assertEquals(before + 1, model.uiState.value.coverGeneration)
        }
}
