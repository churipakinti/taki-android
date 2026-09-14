/*
 * PlaylistDetailViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.playlist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import java.io.IOException
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
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.PlaylistDetailViewModel
import org.robolectric.RobolectricTestRunner

/**
 * [PlaylistDetailViewModel] projection, ported 1:1 from `TrackCollectionFragment`'s
 * `navArgs.playlistId != null` mode: the flat (unsorted-by-us, server-order) track list, the
 * "single artist or Various Artists" header derivation shared with Album Detail, and - unlike
 * [org.moire.ultrasonic.model.AlbumDetailViewModel] - **no load-once guard**, since the legacy
 * `TrackCollectionModel.getPlaylist`/`CachedMusicService.getPlaylist` never cached at all (see
 * [PlaylistDetailUiState]'s kdoc). The one server call is replaced with a controllable fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PlaylistDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun args(
        playlistId: String = "pl1",
        playlistName: String = "Road Trip",
        radioAvailable: Boolean = true,
    ) = PlaylistDetailArgs(playlistId = playlistId, playlistName = playlistName, radioAvailable = radioAvailable)

    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String? = "Miles Davis",
        no: Int? = null,
        duration: Int? = 180,
        video: Boolean = false,
    ) = Track(id = id, title = title, artist = artist, track = no, duration = duration, isVideo = video)

    private fun vm(loader: suspend (String, String) -> List<Track> = { _, _ -> listOf(track("1"), track("2")) }) =
        PlaylistDetailViewModel(app).apply { playlistLoader = loader }

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a loaded playlist fills the header and the rows, title fixed from args`() = runTest {
        val model = vm { _, _ -> listOf(track("1", duration = 120), track("2", duration = 60)) }
        model.load(args(playlistName = "Road Trip"))
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Road Trip", state.title)
        assertEquals("pl1", state.playlistId)
        assertEquals(2, state.songCount)
        assertEquals("3:00", state.totalDuration)
        assertEquals(2, state.rows.size)
    }

    @Test
    fun `the header artist is the single track artist, or Various Artists`() = runTest {
        val single = vm { _, _ -> listOf(track("1", artist = "Bill Evans"), track("2", artist = "Bill Evans")) }
        single.load(args())
        advanceUntilIdle()
        assertEquals("Bill Evans", single.uiState.value.artist)

        val various = vm { _, _ -> listOf(track("1", artist = "Bill Evans"), track("2", artist = "Miles Davis")) }
        various.load(args())
        advanceUntilIdle()
        assertEquals("Various Artists", various.uiState.value.artist)
    }

    @Test
    fun `every row always carries its artist, unlike Album Detail`() = runTest {
        // Album Detail only shows a per-row artist for a multi-artist album; a playlist always
        // shows it (TrackCollectionFragment's showArtist = { _ -> true } for !navArgs.isAlbum).
        val model = vm { _, _ -> listOf(track("1", artist = "Bill Evans")) }
        model.load(args())
        advanceUntilIdle()
        assertEquals("Bill Evans", model.uiState.value.rows.single().artist)
    }

    @Test
    fun `tracks stay in the server's playlist order - no client-side sort`() = runTest {
        val model = vm { _, _ -> listOf(track("z"), track("a"), track("m")) }
        model.load(args())
        advanceUntilIdle()
        assertEquals(listOf("z", "a", "m"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `video tracks are kept in the playlist list`() = runTest {
        val model = vm { _, _ -> listOf(track("s1"), track("v1", video = true)) }
        model.load(args())
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.rows.size)
        assertTrue(model.uiState.value.rows.first { it.id == "v1" }.isVideo)
    }

    @Test
    fun `a failed load ends as a finished empty playlist`() = runTest {
        val model = vm { _, _ -> throw IOException("server down") }
        model.load(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
        assertTrue(state.showEmpty)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = vm { _, _ -> throw CancellationException("scope died") }
        model.load(args())
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `unlike Album Detail, calling load twice always re-fetches - there is no cache to skip`() = runTest {
        var calls = 0
        val model = vm { _, _ -> calls++; listOf(track("1")) }
        model.load(args())
        advanceUntilIdle()
        model.load(args())
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `refresh always re-fetches too`() = runTest {
        var calls = 0
        val model = vm { _, _ -> calls++; listOf(track("1")) }
        model.load(args())
        advanceUntilIdle()
        model.refresh(args())
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `a refresh while one is already running is ignored`() = runTest {
        var calls = 0
        val model = vm { _, _ -> calls++; listOf(track("1")) }
        model.load(args())
        model.refresh(args()) // ignored - the initial load is still in flight
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    @Test
    fun `a failed refresh keeps the playlist that is already on screen`() = runTest {
        var fail = false
        val model = vm { _, _ -> if (fail) throw IOException("network dropped") else listOf(track("1")) }
        model.load(args())
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasContent)

        fail = true
        model.refresh(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertTrue(state.hasContent)
    }

    @Test
    fun `playback accessors expose the full playlist track list`() = runTest {
        val model = vm { _, _ -> listOf(track("a"), track("b")) }
        model.load(args())
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), model.tracksSnapshot().map { it.id })
        assertEquals("Track a", model.trackFor("a")?.title)
        assertNull(model.trackFor("nope"))
    }

    // --- Remove from playlist -------------------------------------------------------------

    @Test
    fun `indexOf resolves a track's position, positional not by-id`() = runTest {
        val model = vm { _, _ -> listOf(track("a"), track("b"), track("c")) }
        model.load(args())
        advanceUntilIdle()

        assertEquals(0, model.indexOf("a"))
        assertEquals(2, model.indexOf("c"))
        assertNull(model.indexOf("nope"))
    }

    @Test
    fun `removeTrackAt drops the row immediately and updates songCount`() = runTest {
        val model = vm { _, _ -> listOf(track("a"), track("b")) }
        model.load(args())
        advanceUntilIdle()

        model.removeTrackAt("a")

        assertEquals(listOf("b"), model.uiState.value.rows.map { it.id })
        assertEquals(1, model.uiState.value.songCount)
        assertEquals(listOf("b"), model.tracksSnapshot().map { it.id })
    }
}
