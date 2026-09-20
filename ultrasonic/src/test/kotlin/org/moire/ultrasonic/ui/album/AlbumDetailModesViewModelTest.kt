/*
 * AlbumDetailModesViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.album

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.AlbumDetailViewModel
import org.moire.ultrasonic.model.toIndicator
import org.moire.ultrasonic.service.DownloadState
import org.robolectric.RobolectricTestRunner

/**
 * The folder-mode and offline/downloaded modes of [AlbumDetailViewModel] (issue #10 phase 4H1):
 * folder sub-folder rows (the legacy `AlbumRowDelegate` rows), the downloaded album's local-only
 * load and per-track download status, and the offline-mode gating of the server-dependent
 * actions. The online id3 behaviour has its own suite ([AlbumDetailViewModelTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlbumDetailModesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun track(id: String, disc: Int? = 1, no: Int? = null, album: String? = "Album") = Track(
        id = id,
        title = "Track $id",
        album = album,
        artist = "Artist",
        artistId = "ar1",
        discNumber = disc,
        track = no,
        duration = 100,
    )

    private fun folder(id: String, title: String? = "Folder $id", artist: String? = null) =
        Album(id = id, title = title, artist = artist, parent = "root")

    private fun folderArgs() = AlbumDetailArgs(id = "dir", name = "Dir", isId3 = false)

    private fun downloadedArgs(online: Boolean = true) = AlbumDetailArgs(
        id = "al1",
        name = "Downloaded",
        isId3 = true,
        isDownloadedAlbum = true,
        online = online,
    )

    private fun vm(
        loader: suspend (AlbumDetailArgs) -> List<MusicDirectory.Child>? = { emptyList() },
        offline: suspend (String) -> List<Track> = { emptyList() },
        resolver: suspend (Track) -> DownloadState = { DownloadState.DONE },
    ) = AlbumDetailViewModel(app).apply {
        albumLoader = loader
        offlineAlbumLoader = offline
        statusResolver = resolver
        metaLoader = { error("a downloaded/folder album must not fetch notes/starred") }
    }

    private fun AlbumDetailViewModel.load(args: AlbumDetailArgs, scope: kotlinx.coroutines.test.TestScope) {
        load(args)
        scope.advanceUntilIdle()
    }

    // --- Folder mode: sub-folder rows ---------------------------------------------------------

    @Test
    fun `folder mode keeps sub-folders as rows alongside tracks`() = runTest {
        val model = vm(loader = { listOf(track("t1", no = 1), folder("f1"), track("t2", no = 2)) })
        model.load(folderArgs(), this)

        val kinds = model.uiState.value.rows.map { it::class.simpleName }
        assertEquals(2, kinds.count { it == "Track" })
        assertEquals(1, kinds.count { it == "Folder" })
        assertEquals(2, model.uiState.value.songCount) // folders are not songs
    }

    @Test
    fun `folder and track rows are ordered together by the display comparator`() = runTest {
        // EntryByDiscAndTrackComparator compares album, then disc, then track, then path.
        val model = vm(
            loader = {
                listOf(
                    Track(id = "t2", title = "T2", album = "B", discNumber = 1, track = 1),
                    Album(id = "f1", title = "F1", album = "A", parent = "root"),
                    Track(id = "t1", title = "T1", album = "A", discNumber = 1, track = 1),
                )
            },
        )
        model.load(folderArgs(), this)
        val ids = model.uiState.value.rows.map {
            when (it) {
                is AlbumDetailRow.Folder -> it.id
                is AlbumDetailRow.Track -> it.id
                is AlbumDetailRow.Disc -> "disc"
            }
        }
        // Same album, the folder (disc 0) sorts ahead of disc 1 track 1; album B sorts last.
        assertEquals(listOf("f1", "t1", "t2"), ids)
    }

    @Test
    fun `a folder row shows its title, falls back to name, and hides a blank artist`() = runTest {
        val model = vm(
            loader = {
                listOf(
                    folder("f1", title = "CD1", artist = "  "),
                    Album(id = "f2", title = null, name = "By Name", artist = "Someone", parent = "root"),
                    track("t"),
                )
            },
        )
        model.load(folderArgs(), this)
        val folders = model.uiState.value.rows.filterIsInstance<AlbumDetailRow.Folder>()
            .associateBy { it.id }
        assertEquals("CD1", folders.getValue("f1").title)
        assertNull(folders.getValue("f1").artist)
        assertEquals("By Name", folders.getValue("f2").title)
        assertEquals("Someone", folders.getValue("f2").artist)
    }

    @Test
    fun `folderFor returns the tapped directory with its parent, unknown ids resolve to null`() =
        runTest {
            val model = vm(loader = { listOf(folder("f1"), track("t")) })
            model.load(folderArgs(), this)
            assertEquals("root", model.folderFor("f1")?.parent)
            assertNull(model.folderFor("t"))
            assertNull(model.folderFor("nope"))
        }

    @Test
    fun `Play and Shuffle only ever see tracks, never folders`() = runTest {
        val model = vm(loader = { listOf(folder("f1"), track("t1"), track("t2")) })
        model.load(folderArgs(), this)
        assertEquals(listOf("t1", "t2"), model.tracksSnapshot().map { it.id }.sorted())
        assertNull(model.trackFor("f1"))
    }

    @Test
    fun `a directory with only sub-folders has no tracks, no artist line, and is not an error`() =
        runTest {
            val model = vm(loader = { listOf(folder("f1"), folder("f2")) })
            model.load(folderArgs(), this)
            val state = model.uiState.value
            assertFalse(state.hasTracks)
            assertFalse(state.loadFailed)
            assertEquals("", state.artist)
            assertEquals(2, state.rows.size)
        }

    @Test
    fun `a directory with no children at all is the empty state`() = runTest {
        val model = vm(loader = { emptyList() })
        model.load(folderArgs(), this)
        assertTrue(model.uiState.value.loadFailed)
    }

    @Test
    fun `folder mode calls the online loader with isId3 false and never the offline one`() = runTest {
        var seen: AlbumDetailArgs? = null
        val model = vm(
            loader = { seen = it; listOf(track("t")) },
            offline = { error("offline loader must not run") },
        )
        model.load(folderArgs(), this)
        assertFalse(seen!!.isId3)
    }

    @Test
    fun `a folder album never loads notes or the starred state`() = runTest {
        // metaLoader in vm() throws if called - reaching here means it was not.
        val model = vm(loader = { listOf(track("t")) })
        model.load(folderArgs(), this)
        assertFalse(model.uiState.value.infoAvailable)
    }

    @Test
    fun `refresh re-queries the folder and keeps the rows on failure`() = runTest {
        var calls = 0
        var fail = false
        val model = vm(
            loader = {
                calls++
                if (fail) null else listOf(track("t"), folder("f"))
            },
        )
        model.load(folderArgs(), this)
        fail = true
        model.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)
        assertEquals(2, model.uiState.value.rows.size)
        assertFalse(model.uiState.value.loadFailed)
    }

    @Test
    fun `reloading the same folder args without refresh does not re-query`() = runTest {
        var calls = 0
        val model = vm(loader = { calls++; listOf(track("t")) })
        model.load(folderArgs(), this)
        model.load(folderArgs(), this) // back-navigation recreates the view
        assertEquals(1, calls)
    }

    // --- Downloaded / offline mode ------------------------------------------------------------

    @Test
    fun `a downloaded album loads from the local database only`() = runTest {
        val model = vm(
            loader = { error("online loader must not run for a downloaded album") },
            offline = { id -> listOf(track("$id-1", no = 1), track("$id-2", no = 2)) },
        )
        model.load(downloadedArgs(), this)
        val state = model.uiState.value
        assertEquals(2, state.songCount)
        assertFalse(state.loadFailed)
        assertFalse(state.infoAvailable)
    }

    @Test
    fun `downloaded tracks keep disc then track order`() = runTest {
        val model = vm(
            offline = {
                listOf(
                    track("d2t1", disc = 2, no = 1),
                    track("d1t2", disc = 1, no = 2),
                    track("d1t1", disc = 1, no = 1),
                )
            },
        )
        model.load(downloadedArgs(), this)
        val ids = model.uiState.value.rows.filterIsInstance<AlbumDetailRow.Track>().map { it.id }
        assertEquals(listOf("d1t1", "d1t2", "d2t1"), ids)
    }

    @Test
    fun `a downloaded album with nothing local is the empty state`() = runTest {
        val model = vm(offline = { emptyList() })
        model.load(downloadedArgs(), this)
        assertTrue(model.uiState.value.loadFailed)
    }

    @Test
    fun `a failing local load is the empty state, not a crash`() = runTest {
        val model = vm(offline = { error("db down") })
        model.load(downloadedArgs(), this)
        assertTrue(model.uiState.value.loadFailed)
    }

    @Test
    fun `only a downloaded album shows per-track download status`() = runTest {
        val downloaded = vm(offline = { listOf(track("t")) })
        downloaded.load(downloadedArgs(), this)
        assertTrue(downloaded.uiState.value.showDownloadStatus)

        val online = vm(loader = { listOf(track("t")) })
        online.load(AlbumDetailArgs("al1", "A", isId3 = true), this)
        assertFalse(online.uiState.value.showDownloadStatus)
        val folderMode = vm(loader = { listOf(track("t")) })
        folderMode.load(folderArgs(), this)
        assertFalse(folderMode.uiState.value.showDownloadStatus)
    }

    @Test
    fun `requestTrackStatus resolves a downloaded row once`() = runTest {
        var resolves = 0
        val model = vm(offline = { listOf(track("t")) }, resolver = { resolves++; DownloadState.PINNED })
        model.load(downloadedArgs(), this)

        model.requestTrackStatus("t")
        advanceUntilIdle()
        model.requestTrackStatus("t") // the row re-enters composition
        advanceUntilIdle()

        assertEquals(1, resolves)
        assertEquals(
            TrackDownloadIndicator.Kind.DOWNLOADED,
            model.uiState.value.trackStatuses["t"]?.kind,
        )
    }

    @Test
    fun `an idle track resolves to no indicator`() = runTest {
        val model = vm(offline = { listOf(track("t")) }, resolver = { DownloadState.IDLE })
        model.load(downloadedArgs(), this)
        model.requestTrackStatus("t")
        advanceUntilIdle()
        assertNull(model.uiState.value.trackStatuses["t"])
    }

    @Test
    fun `requestTrackStatus is a no-op for an online album`() = runTest {
        var resolves = 0
        val model = vm(loader = { listOf(track("t")) }, resolver = { resolves++; DownloadState.DONE })
        model.load(AlbumDetailArgs("al1", "A", isId3 = true), this)
        model.requestTrackStatus("t")
        advanceUntilIdle()
        assertEquals(0, resolves)
        assertTrue(model.uiState.value.trackStatuses.isEmpty())
    }

    @Test
    fun `a failing status lookup is swallowed and leaves the row without an indicator`() = runTest {
        val model = vm(offline = { listOf(track("t")) }, resolver = { error("io") })
        model.load(downloadedArgs(), this)
        model.requestTrackStatus("t")
        advanceUntilIdle()
        assertNull(model.uiState.value.trackStatuses["t"])
    }

    @Test
    fun `live download-state changes update one row, with determinate progress`() = runTest {
        val model = vm(offline = { listOf(track("a"), track("b")) })
        model.load(downloadedArgs(), this)

        model.onTrackDownloadState("a", DownloadState.DOWNLOADING, progress = 42)
        var indicator = model.uiState.value.trackStatuses["a"]
        assertEquals(TrackDownloadIndicator.Kind.DOWNLOADING, indicator?.kind)
        assertEquals(42, indicator?.progress)
        assertNull(model.uiState.value.trackStatuses["b"])

        model.onTrackDownloadState("a", DownloadState.DONE, progress = null)
        indicator = model.uiState.value.trackStatuses["a"]
        assertEquals(TrackDownloadIndicator.Kind.DOWNLOADED, indicator?.kind)
    }

    @Test
    fun `a cancelled download clears the indicator and a foreign track id is ignored`() = runTest {
        val model = vm(offline = { listOf(track("a")) })
        model.load(downloadedArgs(), this)
        model.onTrackDownloadState("a", DownloadState.DONE, null)
        model.onTrackDownloadState("a", DownloadState.CANCELLED, null)
        assertNull(model.uiState.value.trackStatuses["a"])

        model.onTrackDownloadState("stranger", DownloadState.DONE, null)
        assertTrue(model.uiState.value.trackStatuses.isEmpty())
    }

    @Test
    fun `the state to indicator mapping matches the legacy row`() {
        assertEquals(TrackDownloadIndicator.Kind.DOWNLOADED, DownloadState.DONE.toIndicator(null)?.kind)
        assertEquals(TrackDownloadIndicator.Kind.DOWNLOADED, DownloadState.PINNED.toIndicator(null)?.kind)
        assertEquals(TrackDownloadIndicator.Kind.FAILED, DownloadState.FAILED.toIndicator(null)?.kind)
        assertEquals(TrackDownloadIndicator.Kind.QUEUED, DownloadState.QUEUED.toIndicator(null)?.kind)
        assertEquals(TrackDownloadIndicator.Kind.QUEUED, DownloadState.RETRYING.toIndicator(null)?.kind)
        assertEquals(7, DownloadState.DOWNLOADING.toIndicator(7)?.progress)
        assertNull(DownloadState.IDLE.toIndicator(null))
        assertNull(DownloadState.CANCELLED.toIndicator(null))
        assertNull(DownloadState.UNKNOWN.toIndicator(null))
    }

    // --- Offline-mode gating -------------------------------------------------------------------

    @Test
    fun `offline mode hides the server-dependent actions, online mode keeps them`() = runTest {
        val offline = vm(offline = { listOf(track("t")) })
        offline.load(downloadedArgs(online = false), this)
        assertFalse(offline.uiState.value.online)
        assertFalse(offline.uiState.value.starVisible)

        val online = vm(offline = { listOf(track("t")) })
        online.load(downloadedArgs(online = true), this)
        assertTrue(online.uiState.value.online)
        assertTrue(online.uiState.value.starVisible)
    }
}
