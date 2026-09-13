/*
 * ArtistDetailViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artist

import java.io.IOException
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.CoverArtRequest
import org.moire.ultrasonic.model.ArtistDetailViewModel
import org.robolectric.RobolectricTestRunner

/**
 * [ArtistDetailViewModel] projection, ported from the legacy `ArtistDetailModel`: the
 * year-desc album sort, the ranked top-5 popular preview, the HTML-stripped biography, the
 * issue #16 cover-art gap-fill, load-once across back navigation, and refresh. The one
 * server-touching seam (`dataLoader`) is replaced by a fake. The top-songs -> search ->
 * first-album track fallback lives inside the default `dataLoader` and is a verbatim copy of
 * the (previously untested) legacy chain - it is exercised on the Pixel 7, not here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtistDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun args(
        id: String = "ar1",
        name: String = "Héroes del Silencio",
        knownCoverArt: String? = "cover-ar1",
    ) = ArtistDetailArgs(artistId = id, artistName = name, knownCoverArt = knownCoverArt)

    // No coverArt: coverArtRequestOrNull returns null early, so the projection never touches
    // FileUtil / the media-root Storage (which cannot initialise in a resource-less test).
    private fun album(id: String, title: String = "Album $id", year: Int? = 2000) =
        Album(id = id, title = title, year = year, artist = "Héroes del Silencio")

    private fun track(id: String, artistId: String = "ar1") =
        Track(id = id, title = "Track $id", album = "An Album", artistId = artistId, duration = 200)

    private fun data(
        albums: List<Album> = listOf(album("a1")),
        tracks: List<Track> = listOf(track("t1"), track("t2")),
        biography: String? = null,
        similar: List<Artist> = emptyList(),
        resolvedCoverArt: String? = null,
    ) = ArtistDetailViewModel.ArtistData(albums, tracks, biography, similar, resolvedCoverArt)

    private fun vm(
        loader: suspend (ArtistDetailArgs) -> ArtistDetailViewModel.ArtistData = { data() },
    ) = ArtistDetailViewModel().apply {
        dataLoader = loader
        // Stub the artist-art key builder (unit tests have no media-root Storage).
        heroArtwork = { _, coverArtId ->
            coverArtId?.takeIf { it.isNotBlank() }?.let { CoverArtRequest(it, "key-$it", size = 0) }
        }
    }

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a loaded artist fills the name, albums, popular preview and biography`() = runTest {
        val model = vm {
            data(
                albums = listOf(album("old", year = 1990), album("new", year = 2005)),
                tracks = (1..8).map { track("t$it") },
                biography = "<p>A Spanish <b>rock</b> band.</p>",
            )
        }
        model.load(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Héroes del Silencio", state.artistName)
        // Year-descending, then title.
        assertEquals(listOf("new", "old"), state.albums.map { it.id })
        assertEquals(2, state.albumCount)
        // Popular preview capped at 5, 1-based ranks.
        assertEquals(5, state.popularTracks.size)
        assertEquals(listOf("1", "2", "3", "4", "5"), state.popularTracks.map { it.rank })
        // Biography HTML-stripped.
        assertEquals("A Spanish rock band.", state.biography)
        assertTrue(state.showAbout)
    }

    @Test
    fun `a supplied cover-art id fills the hero immediately, before the load resolves`() {
        val model = vm { CompletableDeferred<ArtistDetailViewModel.ArtistData>().await() }
        model.load(args(knownCoverArt = "cover-ar1"))
        // Still loading (dataLoader never completes) but the hero model is already set.
        assertTrue(model.uiState.value.isLoading)
        assertNotNull(model.uiState.value.artworkModel)
    }

    @Test
    fun `without a supplied cover-art id the hero fills from the resolved id (issue 16)`() = runTest {
        val model = vm { data(resolvedCoverArt = "resolved-ar1") }
        model.load(args(knownCoverArt = null))
        assertNull(model.uiState.value.artworkModel)
        advanceUntilIdle()
        assertNotNull(model.uiState.value.artworkModel)
    }

    @Test
    fun `an artist with no albums and no tracks ends as a finished empty screen`() = runTest {
        val model = vm { data(albums = emptyList(), tracks = emptyList()) }
        model.load(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.showEmpty)
    }

    @Test
    fun `a failed first load ends without content, not stuck loading`() = runTest {
        val model = vm { throw IOException("server down") }
        model.load(args())
        advanceUntilIdle()

        assertFalse(model.uiState.value.isLoading)
        assertTrue(model.uiState.value.loadFailed)
        assertTrue(model.uiState.value.showEmpty)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = vm { throw CancellationException("scope died") }
        model.load(args())
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `the same artist is not re-fetched, but refresh forces it`() = runTest {
        var calls = 0
        val model = vm { calls++; data() }
        model.load(args())
        advanceUntilIdle()
        model.load(args())
        advanceUntilIdle()
        assertEquals(1, calls)

        model.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `an empty artist still counts as loaded - it is not re-fetched on the way back`() = runTest {
        var calls = 0
        val model = vm { calls++; data(albums = emptyList(), tracks = emptyList()) }
        model.load(args())
        advanceUntilIdle()
        model.load(args())
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `a refresh keeps the sections on screen, then reprojects the new data`() = runTest {
        var response = data(albums = listOf(album("old", "Old")))
        val gate = CompletableDeferred<Unit>()
        var first = true
        val model = vm {
            if (!first) gate.await()
            first = false
            response
        }
        model.load(args())
        advanceUntilIdle()
        assertEquals(listOf("Old"), model.uiState.value.albums.map { it.title })

        response = data(albums = listOf(album("new", "New")))
        model.refresh()
        advanceUntilIdle()
        assertTrue(model.uiState.value.isLoading)
        assertEquals(listOf("Old"), model.uiState.value.albums.map { it.title })

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertEquals(listOf("New"), model.uiState.value.albums.map { it.title })
    }

    @Test
    fun `a second refresh while one is running is ignored`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val model = vm {
            calls++
            if (calls >= 2) gate.await()
            data()
        }
        model.load(args())
        advanceUntilIdle()

        model.refresh()
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `playback accessors expose the full fetched track list`() = runTest {
        val model = vm { data(tracks = listOf(track("t1"), track("t2"), track("t3"))) }
        model.load(args())
        advanceUntilIdle()

        assertEquals(listOf("t1", "t2", "t3"), model.tracksSnapshot().map { it.id })
        assertEquals("Track t2", model.trackFor("t2")?.title)
        assertNull(model.trackFor("nope"))
    }
}
