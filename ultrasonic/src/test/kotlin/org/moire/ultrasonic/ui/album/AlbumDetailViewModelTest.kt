/*
 * AlbumDetailViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.AlbumDetailViewModel
import org.robolectric.RobolectricTestRunner

/**
 * [AlbumDetailViewModel] projection, ported 1:1 from `TrackCollectionFragment`'s id3 album
 * mode: sort by disc + track, multi-disc grouping, the multi-artist per-row artist rule, the
 * single-artist navigation gate, the slower notes/starred fold-in, and failure -> empty. The
 * two server calls are replaced with controllable fakes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlbumDetailViewModelTest {

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
        id: String = "al1",
        name: String? = "Kind of Blue",
        isId3: Boolean = true,
        isDownloadedAlbum: Boolean = false,
    ) = AlbumDetailArgs(id = id, name = name, isId3 = isId3, isDownloadedAlbum = isDownloadedAlbum)

    private fun track(
        id: String,
        title: String = "Track $id",
        artist: String? = "Miles Davis",
        artistId: String? = "ar1",
        disc: Int? = 1,
        no: Int? = null,
        duration: Int? = 180,
        video: Boolean = false,
    ) = Track(
        id = id,
        title = title,
        album = "Kind of Blue",
        artist = artist,
        artistId = artistId,
        discNumber = disc,
        track = no,
        duration = duration,
        isVideo = video,
    )

    private fun vm(
        meta: suspend (String) -> AlbumDetailViewModel.AlbumMeta = {
            AlbumDetailViewModel.AlbumMeta(null, null)
        },
        offlineLoader: suspend (String) -> List<Track> = { listOf(track("1"), track("2")) },
        loader: suspend (AlbumDetailArgs) -> List<Track>? = { listOf(track("1"), track("2")) },
    ) = AlbumDetailViewModel(app).apply {
        albumLoader = loader
        metaLoader = meta
        offlineAlbumLoader = offlineLoader
    }

    private fun trackRows(model: AlbumDetailViewModel) =
        model.uiState.value.rows.filterIsInstance<AlbumDetailRow.Track>()

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a loaded album fills the header and the rows`() = runTest {
        val model = vm { listOf(track("1", duration = 120), track("2", duration = 60)) }
        model.load(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Kind of Blue", state.title)
        assertEquals("Miles Davis", state.artist)
        assertEquals("ar1", state.artistId)
        assertEquals(2, state.songCount)
        assertEquals("3:00", state.totalDuration)
        assertEquals(2, trackRows(model).size)
    }

    @Test
    fun `tracks are ordered by disc then track number`() = runTest {
        val model = vm {
            listOf(
                track("d2t1", disc = 2, no = 1),
                track("d1t2", disc = 1, no = 2),
                track("d1t1", disc = 1, no = 1),
            )
        }
        model.load(args())
        advanceUntilIdle()

        assertEquals(listOf("d1t1", "d1t2", "d2t1"), trackRows(model).map { it.id })
    }

    @Test
    fun `a multi-disc album gets quiet disc markers, a single-disc album does not`() = runTest {
        val multi = vm {
            listOf(track("a", disc = 1, no = 1), track("b", disc = 2, no = 1))
        }
        multi.load(args())
        advanceUntilIdle()
        assertTrue(multi.uiState.value.hasMultipleDiscs)
        assertEquals(
            listOf(1, 2),
            multi.uiState.value.rows.filterIsInstance<AlbumDetailRow.Disc>().map { it.number },
        )

        val single = vm { listOf(track("a", disc = 1), track("b", disc = 1)) }
        single.load(args())
        advanceUntilIdle()
        assertFalse(single.uiState.value.hasMultipleDiscs)
        assertTrue(single.uiState.value.rows.none { it is AlbumDetailRow.Disc })
    }

    @Test
    fun `the per-row artist shows only when the album has several artists`() = runTest {
        val single = vm { listOf(track("1"), track("2")) }
        single.load(args())
        advanceUntilIdle()
        assertTrue(trackRows(single).all { it.artist == null })

        val various = vm {
            listOf(
                track("1", artist = "Bill Evans", artistId = "ar-be"),
                track("2", artist = "Miles Davis", artistId = "ar-md"),
            )
        }
        various.load(args())
        advanceUntilIdle()
        assertEquals(listOf("Bill Evans", "Miles Davis"), trackRows(various).map { it.artist })
        // No single artist id -> the artist line is not a navigation target.
        assertNull(various.uiState.value.artistId)
    }

    @Test
    fun `video tracks are kept in the album list`() = runTest {
        val model = vm { listOf(track("s1"), track("v1", video = true)) }
        model.load(args())
        advanceUntilIdle()
        assertEquals(2, trackRows(model).size)
        assertTrue(trackRows(model).first { it.id == "v1" }.isVideo)
    }

    @Test
    fun `notes and the server star state fold in after the track list`() = runTest {
        val model = vm(
            loader = { listOf(track("1")) },
            meta = { AlbumDetailViewModel.AlbumMeta(notes = "A landmark session.", starred = true) },
        )
        model.load(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals("A landmark session.", state.notes)
        assertTrue(state.infoAvailable)
        assertTrue(state.isStarred)
        assertTrue(state.starVisible)
    }

    @Test
    fun `a failed load ends as a finished empty album`() = runTest {
        val model = vm { throw IOException("server down") }
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
        val model = vm { throw CancellationException("scope died") }
        model.load(args())
        runCatching { advanceUntilIdle() }
        // The generic catch turns a real error into loadFailed; a CancellationException is
        // rethrown instead, so the state must not have flipped to "failed".
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `the same album is not re-fetched, but refresh forces it`() = runTest {
        var calls = 0
        val model = vm { calls++; listOf(track("1")) }
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
    fun `a refresh keeps the current rows on screen, then reprojects the new data`() = runTest {
        var responses = listOf(track("a", title = "Old A"), track("b", title = "Old B"))
        val gate = CompletableDeferred<Unit>()
        var firstDone = false
        val model = vm {
            if (firstDone) gate.await()
            firstDone = true
            responses
        }
        model.load(args())
        advanceUntilIdle()
        assertEquals(listOf("Old A", "Old B"), trackRows(model).map { it.title })

        responses = listOf(track("a", title = "New A"))
        model.refresh()
        advanceUntilIdle()
        // The fetch is still gated: the old rows are still shown, isLoading is true.
        assertTrue(model.uiState.value.isLoading)
        assertEquals(listOf("Old A", "Old B"), trackRows(model).map { it.title })

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertEquals(listOf("New A"), trackRows(model).map { it.title })
    }

    @Test
    fun `a second refresh while one is running is ignored`() = runTest {
        var calls = 0
        val gate = CompletableDeferred<Unit>()
        val model = vm {
            calls++
            if (calls >= 2) gate.await()
            listOf(track("1"))
        }
        model.load(args())
        advanceUntilIdle()
        assertEquals(1, calls)

        model.refresh()
        advanceUntilIdle()
        model.refresh() // ignored - the first refresh is still in flight
        advanceUntilIdle()
        assertEquals(2, calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `a failed refresh keeps the album that is already on screen`() = runTest {
        var fail = false
        val model = vm { if (fail) throw IOException("network dropped") else listOf(track("1")) }
        model.load(args())
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasContent)

        fail = true
        model.refresh()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed) // content is still valid, so not the "failed" state
        assertTrue(state.hasContent)
        assertFalse(state.showEmpty)
    }

    @Test
    fun `playback accessors expose the full sorted track list`() = runTest {
        val model = vm {
            listOf(track("d2", disc = 2, no = 1), track("d1", disc = 1, no = 1))
        }
        model.load(args())
        advanceUntilIdle()

        assertEquals(listOf("d1", "d2"), model.tracksSnapshot().map { it.id })
        assertEquals("Track d1", model.trackFor("d1")?.title)
        assertNull(model.trackFor("nope"))
        assertEquals(listOf("d2"), model.tracksForDisc(2).map { it.id })
    }

    @Test
    fun `an optimistic star flip is reflected immediately`() {
        val model = vm()
        model.setStarredOptimistic(true)
        assertTrue(model.uiState.value.isStarred)
        model.setStarredOptimistic(false)
        assertFalse(model.uiState.value.isStarred)
    }

    // --- Phase 4D: folder-mode + offline/downloaded albums ------------------------------

    @Test
    fun `a folder-mode album (isId3 = false) still loads and projects normally`() = runTest {
        var loaderArgsSeen: AlbumDetailArgs? = null
        val model = vm(loader = { a -> loaderArgsSeen = a; listOf(track("1"), track("2")) })
        model.load(args(isId3 = false))
        advanceUntilIdle()

        assertEquals(false, loaderArgsSeen?.isId3)
        val state = model.uiState.value
        assertEquals(2, state.songCount)
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
    }

    @Test
    fun `folder-mode albums still show the star, same as id3 albums`() = runTest {
        val model = vm()
        model.load(args(isId3 = false))
        advanceUntilIdle()
        assertTrue(model.uiState.value.starVisible)
    }

    @Test
    fun `folder-mode albums skip the notes-starred network fold-in, same as before`() = runTest {
        var metaCalls = 0
        val model = vm(meta = { metaCalls++; AlbumDetailViewModel.AlbumMeta(null, null) })
        model.load(args(isId3 = false))
        advanceUntilIdle()
        assertEquals(0, metaCalls)
    }

    @Test
    fun `a downloaded album loads from the offline loader, not the network one`() = runTest {
        var onlineCalls = 0
        var offlineCalls = 0
        val model = vm(
            loader = { onlineCalls++; listOf(track("online")) },
            offlineLoader = { offlineCalls++; listOf(track("local-1"), track("local-2")) },
        )
        model.load(args(isDownloadedAlbum = true))
        advanceUntilIdle()

        assertEquals(0, onlineCalls)
        assertEquals(1, offlineCalls)
        assertEquals(2, model.uiState.value.songCount)
    }

    @Test
    fun `a downloaded album shows the star but skips the notes-starred network fold-in`() = runTest {
        var metaCalls = 0
        val model = vm(
            meta = { metaCalls++; AlbumDetailViewModel.AlbumMeta("notes", true) },
            offlineLoader = { listOf(track("local")) },
        )
        // isId3 = true here on purpose: isDownloadedAlbum must win regardless of isId3.
        model.load(args(isId3 = true, isDownloadedAlbum = true))
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(0, metaCalls)
        assertTrue(state.starVisible)
        assertNull(state.notes)
    }

    @Test
    fun `a downloaded album with no tracks ends as a finished empty album, not a crash`() = runTest {
        val model = vm(offlineLoader = { emptyList() })
        model.load(args(isDownloadedAlbum = true))
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.showEmpty)
    }

    @Test
    fun `refreshing a downloaded album re-reads the offline loader`() = runTest {
        var offlineCalls = 0
        val model = vm(offlineLoader = { offlineCalls++; listOf(track("local")) })
        model.load(args(isDownloadedAlbum = true))
        advanceUntilIdle()
        assertEquals(1, offlineCalls)

        model.refresh()
        advanceUntilIdle()
        assertEquals(2, offlineCalls)
    }
}
