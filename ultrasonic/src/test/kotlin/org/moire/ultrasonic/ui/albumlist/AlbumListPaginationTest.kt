/*
 * AlbumListPaginationTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.albumlist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
import org.junit.Assert.assertSame
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
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.model.AlbumListViewModel
import org.moire.ultrasonic.model.mergeAlbumPage
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner

/**
 * Issue #50: offset paging over a collection that changes between requests can return an album
 * twice, and the album grid is keyed by album id, so a repeated id used to crash the app. These
 * tests drive the real [AlbumListViewModel] against a small in-memory "server" that honours
 * `size` and `offset` exactly, and check the one property the grid needs (each id once) together
 * with the paging rules that must not change (the offset follows the pages received, the end is a
 * page the server sent empty).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlbumListPaginationTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = true
        Settings.id3TagsEnabledOffline = true
        val provider = mock<ActiveServerProvider> {
            on { getActiveServer(any()) } doReturn ServerSetting().apply { musicFolderId = null }
        }
        startKoin { modules(module { single { provider } }) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    private fun album(id: String, title: String = "Album $id") = Album(id = id, title = title)

    /** A server-side list that answers `getAlbumList2` by slicing the *current* collection. */
    private class Server(var albums: List<Album>) {
        val offsets = mutableListOf<Int>()
        fun page(offset: Int, size: Int): List<Album> {
            offsets += offset
            return albums.drop(offset).take(size)
        }
    }

    private fun vm(server: Server, size: Int = 20, beforePage: (Int) -> Unit = {}) =
        AlbumListViewModel(app).apply {
            albumsLoader = { _, pageSize, offset, _, _ ->
                beforePage(offset)
                server.page(offset, pageSize)
            }
            initialize(AlbumListType.SORTED_BY_NAME, false, null, null, size, 0)
        }

    private fun AlbumListViewModel.ids() = uiState.value.rows.map { it.id }

    private fun library(count: Int) = (0 until count).map { album("id%02d".format(it)) }

    // --- normal pagination --------------------------------------------------------------------

    @Test
    fun `two ordinary pages are appended in server order with no duplicates`() = runTest {
        val server = Server(library(28))
        val model = vm(server)
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(library(28).map { it.id }, model.ids())
        assertEquals(listOf(0, 20), server.offsets)
    }

    // --- duplicates -----------------------------------------------------------------------------

    @Test
    fun `an album returned by two pages is shown once and keeps its first position`() = runTest {
        val first = library(20)
        val server = Server(first + first.takeLast(1) + library(28).drop(20))
        val model = vm(server)
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        val ids = model.ids()
        assertEquals(ids.distinct(), ids)
        assertEquals("id19", ids[19])
        assertEquals(28, ids.size)
    }

    @Test
    fun `an album repeated inside one page is shown once`() = runTest {
        val server = Server(listOf(album("a"), album("b"), album("a"), album("c"), album("b")))
        val model = vm(server)
        model.load()
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), model.ids())
    }

    @Test
    fun `two different albums with the same title and artist are both kept`() = runTest {
        val server = Server(listOf(album("a", title = "Greatest Hits"), album("b", title = "Greatest Hits")))
        val model = vm(server)
        model.load()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), model.ids())
    }

    // --- the library changes between two page requests (the #50 scenario) -------------------------

    @Test
    fun `an album inserted before page two does not crash or repeat gen17`() = runTest {
        // 28 albums; the 20th (last of page 1) is gen17, as in the emulator reproduction.
        val original = (0 until 28).map { album(if (it == 19) "gen17" else "id%02d".format(it)) }
        val server = Server(original)
        var inserted = false
        val model = vm(server) { offset ->
            if (offset == 20 && !inserted) {
                inserted = true
                server.albums = listOf(album("Aaa-inserted")) + server.albums // sorts first
            }
        }
        model.load()
        advanceUntilIdle()
        model.loadMore() // page 2 now starts with gen17 again
        advanceUntilIdle()

        val ids = model.ids()
        assertEquals("gen17 once", 1, ids.count { it == "gen17" })
        assertEquals(ids.distinct(), ids)
        assertEquals(28, ids.size) // the shifted collection added 8 new rows to the first 20
        // the next request is at offset 40 (pages received), and it is the empty end page
        model.loadMore()
        advanceUntilIdle()
        assertEquals(listOf(0, 20, 40), server.offsets)

        // a refresh reflects the changed library, still one row per album
        model.refresh()
        advanceUntilIdle()
        assertEquals("Aaa-inserted", model.ids().first())
        assertEquals(model.ids().distinct(), model.ids())
    }

    // --- offset and end of pagination --------------------------------------------------------------

    @Test
    fun `the next offset follows the pages received, not the albums that were new`() = runTest {
        val first = library(20)
        // page 2 repeats one album of page 1 and adds 19 new ones: 20 received, 19 new
        val server = Server(first + first.takeLast(1) + (100 until 119).map { album("n$it") })
        val model = vm(server)
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(39, model.ids().size)
        assertEquals(listOf(0, 20, 40), server.offsets) // 40, not 39
    }

    @Test
    fun `a page made only of albums already shown is bounded and never loops`() = runTest {
        val first = library(20)
        // every later page is the same 20 albums again
        val server = Server(first)
        val model = vm(server) { }
        model.albumsLoader = { _, size, offset, _, _ ->
            server.offsets += offset
            first.take(size)
        }
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(20, model.ids().size)
        // 1 first page + the requested page + at most 3 more in a row, then it stops by itself
        assertEquals(listOf(0, 20, 40, 60, 80), server.offsets)

        // a later page with something new is picked up again
        model.albumsLoader = { _, _, offset, _, _ -> server.offsets += offset; listOf(album("fresh")) }
        model.loadMore()
        advanceUntilIdle()
        assertEquals(21, model.ids().size)
        assertEquals("fresh", model.ids().last())
    }

    @Test
    fun `a page of only duplicates is not the end, a page the server sent empty is`() = runTest {
        val first = library(20)
        val server = Server(first)
        var phase = 0
        val model = vm(server)
        model.albumsLoader = { _, size, offset, _, _ ->
            server.offsets += offset
            when {
                offset == 0 -> first.take(size)
                phase == 0 -> first.take(size) // duplicates only
                else -> emptyList() // the real end
            }
        }
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        val afterDuplicates = server.offsets.size
        phase = 1
        model.loadMore() // still allowed: duplicates did not end the list
        advanceUntilIdle()
        assertTrue(server.offsets.size > afterDuplicates)

        val afterEnd = server.offsets.size
        model.loadMore() // the empty page ended it
        advanceUntilIdle()
        assertEquals(afterEnd, server.offsets.size)
    }

    // --- refresh, identity, stale responses -------------------------------------------------------

    @Test
    fun `refresh after several pages restarts at offset zero with one row per album`() = runTest {
        val first = library(20)
        val server = Server(first + first.takeLast(2) + library(28).drop(20))
        val model = vm(server)
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        model.refresh()
        advanceUntilIdle()

        assertEquals(first.map { it.id }, model.ids())
        assertEquals(0, server.offsets.last())
    }

    @Test
    fun `an already shown album keeps its row and its content when a later page carries it again`() {
        val shown = album("a", title = "Old title")
        val merged = mergeAlbumPage(listOf(shown, album("b")), listOf(album("a", title = "New title"), album("c")))

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertSame(shown, merged[0]) // first occurrence wins; a refresh picks up newer metadata
    }

    @Test
    fun `merging a page that adds nothing returns the same list`() {
        val existing = listOf(album("a"), album("b"))
        assertSame(existing, mergeAlbumPage(existing, listOf(album("b"), album("a"))))
    }

    @Test
    fun `rows already on screen are not replaced when a page is appended`() = runTest {
        val server = Server(library(28))
        val model = vm(server)
        model.load()
        advanceUntilIdle()
        val firstRows = model.uiState.value.rows.take(20)
        model.loadMore()
        advanceUntilIdle()

        // same ids, same order, and equal row data for the rows that were already visible
        assertEquals(firstRows, model.uiState.value.rows.take(20))
    }

    @Test
    fun `a response for the previous sort order never reaches the new list`() = runTest {
        val gate = CompletableDeferred<List<Album>>()
        val model = AlbumListViewModel(app).apply {
            albumsLoader = { type, _, offset, _, _ ->
                when {
                    type == AlbumListType.SORTED_BY_NAME && offset == 0 -> library(20)
                    type == AlbumListType.SORTED_BY_NAME -> gate.await() // page 2 of the old order, held
                    else -> listOf(album("by-artist-1"), album("by-artist-2"))
                }
            }
            initialize(AlbumListType.SORTED_BY_NAME, false, null, null, 20, 0)
        }
        model.load()
        advanceUntilIdle()
        model.loadMore() // suspended in the loader
        advanceUntilIdle()

        model.setSortOrder(SortOrder.BY_ARTIST)
        advanceUntilIdle()
        gate.complete(listOf(album("late-from-old-order")))
        advanceUntilIdle()

        assertEquals(listOf("by-artist-1", "by-artist-2"), model.ids())
    }
}
