/*
 * FolderBrowserViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.folderbrowser

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
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.model.FolderBrowserViewModel
import org.robolectric.RobolectricTestRunner

/**
 * [FolderBrowserViewModel] projection, ported 1:1 from `TrackCollectionFragment`'s
 * folder/non-ID3 mode (issue #10 phase 4M1): mixed directory/track rows in server order, failure
 * -> empty, and the heart/refresh seams. The server call is replaced with a controllable fake.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FolderBrowserViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        app = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun args(id: String = "dir1", name: String? = "Rock", online: Boolean = true) =
        FolderBrowserArgs(id = id, name = name, online = online)

    private fun dir(id: String, title: String = "Dir $id", artist: String? = "The Band") = Album(
        id = id,
        title = title,
        name = title,
        artist = artist,
        parent = "dir1",
    )

    private fun track(id: String, title: String = "Track $id", starred: Boolean = false) = Track(
        id = id,
        title = title,
        album = "Some Album",
        artist = "Some Artist",
        starred = starred,
    )

    private fun vm(
        loader: suspend (FolderBrowserArgs) -> List<MusicDirectory.Child>? = {
            listOf(dir("d1"), track("t1"))
        },
    ) = FolderBrowserViewModel(app).apply { directoryLoader = loader }

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `mixed directory and track rows load in server order`() = runTest {
        val model = vm { listOf(track("t1"), dir("d1"), track("t2")) }
        model.load(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Rock", state.title)
        assertEquals(
            listOf(
                FolderBrowserRow.Track::class,
                FolderBrowserRow.Directory::class,
                FolderBrowserRow.Track::class,
            ),
            state.rows.map { it::class },
        )
        assertEquals(listOf("t1", "d1", "t2"), state.rows.map { row ->
            when (row) {
                is FolderBrowserRow.Directory -> row.id
                is FolderBrowserRow.Track -> row.id
            }
        })
    }

    @Test
    fun `a directory row carries its title, artist and parent`() = runTest {
        val model = vm { listOf(dir("d1", title = "Live at the Fillmore", artist = "The Band")) }
        model.load(args())
        advanceUntilIdle()

        val row = model.uiState.value.rows.single() as FolderBrowserRow.Directory
        assertEquals("Live at the Fillmore", row.title)
        assertEquals("The Band", row.artist)
        assertEquals("dir1", row.parent)
    }

    @Test
    fun `a track row joins artist and album into the subtitle`() = runTest {
        val model = vm { listOf(track("t1")) }
        model.load(args())
        advanceUntilIdle()

        val row = model.uiState.value.rows.single() as FolderBrowserRow.Track
        assertEquals("Some Artist · Some Album", row.subtitle)
    }

    @Test
    fun `an empty directory shows the empty state once loaded`() = runTest {
        // Mirrors AlbumDetailViewModel's own "No media found" contract exactly: zero children on
        // the first load *is* the loadFailed state (both screens render the same EmptyState for
        // it either way - see AlbumDetailScreen/FolderBrowserScreen, which only branch on
        // showEmpty, not loadFailed, for their empty-state rendering).
        val model = vm { emptyList() }
        model.load(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
        assertTrue(state.showEmpty)
    }

    @Test
    fun `a failed first load surfaces loadFailed`() = runTest {
        val model = vm { throw IOException("network dropped") }
        model.load(args())
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `a failed refresh keeps the content already on screen`() = runTest {
        var fail = false
        val model = vm { if (fail) throw IOException("network dropped") else listOf(track("t1")) }
        model.load(args())
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasContent)

        fail = true
        model.refresh()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.loadFailed) // content is still valid, so not the "failed" state
        assertTrue(state.hasContent)
    }

    @Test
    fun `refresh reloads with the last-used args`() = runTest {
        var calls = 0
        val model = vm {
            calls++
            listOf(track("t$calls"))
        }
        model.load(args())
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()

        assertEquals(2, calls)
        assertEquals("t2", (model.uiState.value.rows.single() as FolderBrowserRow.Track).id)
    }

    @Test
    fun `tracksSnapshot and itemFor only see track rows, never directories`() = runTest {
        val model = vm { listOf(dir("d1"), track("t1"), track("t2")) }
        model.load(args())
        advanceUntilIdle()

        assertEquals(listOf("t1", "t2"), model.tracksSnapshot().map { it.id })
        assertEquals("t1", model.itemFor("t1")?.id)
        assertNull(model.itemFor("d1"))
    }

    @Test
    fun `toggling the heart flips starred and the row's liked flag together`() = runTest {
        val model = vm { listOf(track("t1", starred = false)) }
        model.load(args())
        advanceUntilIdle()

        val newStarred = model.toggleHeartOptimistic("t1")

        assertEquals(true, newStarred)
        assertTrue(model.itemFor("t1")!!.starred)
        assertTrue((model.uiState.value.rows.single() as FolderBrowserRow.Track).liked)
    }

    @Test
    fun `toggling the heart for an unknown track is a no-op`() = runTest {
        val model = vm { listOf(track("t1")) }
        model.load(args())
        advanceUntilIdle()

        assertNull(model.toggleHeartOptimistic("missing"))
    }
}
