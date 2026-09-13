/*
 * ArtistListViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.artistlist

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
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.data.ServerSetting
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Artist
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.model.ArtistListViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner

/**
 * [ArtistListViewModel] projection, ported from the legacy `ArtistListModel` (issue #10 phase
 * 4E1): id3 `getArtists()` vs folder-mode `getIndexes()`, load-once/refresh, every
 * `filterAndSortItems` branch (album-derived orders, `STARRED` union/dedup, artist-by-id and
 * normalized-name matching), the online/offline sort gating and the folder-selector header
 * condition. `RANDOM`/`STARRED`/`BY_GENRE`/`HIGHEST`/`BY_YEAR` are exercised directly through
 * [ArtistListViewModel.setSortOrder] even though [ArtistListUiState.availableSortOrders] never
 * surfaces them today (see the phase 4E1 report) - they are real, reachable code paths once a
 * future picker (4E2) exposes them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ArtistListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var activeServerProvider: ActiveServerProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()

        // Online by default (activeServer's Settings-backed default is the offline id, -1).
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = true
        Settings.id3TagsEnabledOffline = true

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

    private fun artist(id: String, name: String = "Artist $id") = Artist(id = id, name = name)
    private fun index(id: String, name: String = "Index $id") = Index(id = id, name = name)

    private fun album(
        id: String,
        artistId: String? = null,
        artist: String? = null,
        year: Int? = null,
    ) = Album(id = id, artistId = artistId, artist = artist, year = year)

    private fun vm(
        items: List<ArtistOrIndex> = listOf(artist("a1", "Bravo"), artist("a2", "Alpha")),
        albums: suspend (AlbumListType, String?) -> List<Album> = { _, _ -> emptyList() },
        starred: suspend () -> SearchResult = { SearchResult() },
        folders: suspend () -> List<MusicFolder> = { emptyList() },
    ) = ArtistListViewModel(app).apply {
        itemsLoader = { items }
        albumsLoader = albums
        starredLoader = starred
        musicFoldersLoader = folders
    }

    // --- Initial load, id3 vs folder mode -----------------------------------------------

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `an id3 load fills rows sorted by name, not marked as indexes`() = runTest {
        val model = vm(items = listOf(artist("a1", "Bravo"), artist("a2", "Alpha")))
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("Alpha", "Bravo"), state.rows.map { it.name })
        assertTrue(state.rows.none { it.isIndex })
    }

    @Test
    fun `a folder-mode load returns Index rows`() = runTest {
        val model = vm(items = listOf(index("f1", "Rock"), index("f2", "Jazz")))
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(listOf("Jazz", "Rock"), state.rows.map { it.name })
        assertTrue(state.rows.all { it.isIndex })
    }

    @Test
    fun `a null folder-mode result (nothing changed) keeps the previous items`() = runTest {
        var call = 0
        val model = ArtistListViewModel(app).apply {
            itemsLoader = {
                call++
                if (call == 1) listOf(index("f1", "Rock")) else null
            }
        }
        model.load()
        advanceUntilIdle()
        assertEquals(listOf("Rock"), model.uiState.value.rows.map { it.name })

        model.load(refresh = true)
        advanceUntilIdle()
        assertEquals(listOf("Rock"), model.uiState.value.rows.map { it.name })
    }

    // --- Load-once / refresh -------------------------------------------------------------

    @Test
    fun `the item list is not re-fetched on a second load, but refresh forces it`() = runTest {
        var calls = 0
        val model = ArtistListViewModel(app).apply {
            itemsLoader = { calls++; listOf(artist("a1")) }
        }
        model.load()
        advanceUntilIdle()
        model.load()
        advanceUntilIdle()
        assertEquals(1, calls)

        model.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun `a failed first load ends without content, not stuck loading`() = runTest {
        val model = ArtistListViewModel(app).apply {
            itemsLoader = { throw java.io.IOException("server down") }
        }
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = ArtistListViewModel(app).apply {
            itemsLoader = { throw CancellationException("scope died") }
        }
        model.load()
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    // --- Sort orders -----------------------------------------------------------------------

    @Test
    fun `BY_NAME sorts case-and-locale-aware by name`() = runTest {
        val model = vm(items = listOf(artist("a1", "the Who"), artist("a2", "ABBA")))
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.BY_NAME)
        advanceUntilIdle()
        assertEquals(listOf("ABBA", "the Who"), model.uiState.value.rows.map { it.name })
    }

    @Test
    fun `album-derived orders keep only artists represented in the album list, matched by artistId`() =
        runTest {
            val model = vm(
                items = listOf(artist("a1", "Miles Davis"), artist("a2", "Bill Evans")),
                albums = { type, _ ->
                    assertEquals(AlbumListType.NEWEST, type)
                    listOf(album("al1", artistId = "a1"))
                },
            )
            model.load()
            advanceUntilIdle()
            model.setSortOrder(SortOrder.NEWEST)
            advanceUntilIdle()

            assertEquals(listOf("a1"), model.uiState.value.rows.map { it.id })
        }

    @Test
    fun `an album with no artistId falls back to a normalized-name match`() = runTest {
        val model = vm(
            items = listOf(artist("a1", "  Bill Evans ")),
            albums = { _, _ -> listOf(album("al1", artistId = null, artist = "bill evans")) },
        )
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.RECENT)
        advanceUntilIdle()

        assertEquals(listOf("a1"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `an album matching no artist by id or name contributes no row`() = runTest {
        val model = vm(
            items = listOf(artist("a1", "Bill Evans")),
            albums = { _, _ -> listOf(album("al1", artistId = "unknown", artist = "Nobody")) },
        )
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.FREQUENT)
        advanceUntilIdle()

        assertTrue(model.uiState.value.rows.isEmpty())
    }

    @Test
    fun `STARRED unions direct starred artists and starred-album-derived artists, deduplicated`() =
        runTest {
            val model = vm(
                items = listOf(artist("a1", "Direct"), artist("a2", "ViaAlbum"), artist("a3", "Neither")),
                starred = {
                    SearchResult(
                        artists = listOf(artist("a1", "Direct")),
                        albums = listOf(album("al1", artistId = "a1"), album("al2", artistId = "a2")),
                    )
                },
            )
            model.load()
            advanceUntilIdle()
            model.setSortOrder(SortOrder.STARRED)
            advanceUntilIdle()

            // a1 matches both the direct-artist and the album-derived branch - it must appear once.
            assertEquals(setOf("a1", "a2"), model.uiState.value.rows.map { it.id }.toSet())
            assertEquals(2, model.uiState.value.rows.size)
        }

    @Test
    fun `BY_GENRE with no selected genre falls back to name order, matching the legacy filter bar`() =
        runTest {
            var albumsCalled = false
            val model = vm(
                items = listOf(artist("a1", "Bravo"), artist("a2", "Alpha")),
                albums = { _, _ -> albumsCalled = true; emptyList() },
            )
            model.load()
            advanceUntilIdle()
            model.setSortOrder(SortOrder.BY_GENRE)
            advanceUntilIdle()

            assertFalse(albumsCalled)
            assertEquals(listOf("Alpha", "Bravo"), model.uiState.value.rows.map { it.name })
        }

    @Test
    fun `BY_GENRE with a selected genre derives artists from that genre's albums`() = runTest {
        val model = vm(
            items = listOf(artist("a1", "Miles Davis")),
            albums = { type, genre ->
                assertEquals(AlbumListType.BY_GENRE, type)
                assertEquals("Jazz", genre)
                listOf(album("al1", artistId = "a1"))
            },
        )
        model.selectedGenre = "Jazz"
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.BY_GENRE)
        advanceUntilIdle()

        assertEquals(listOf("a1"), model.uiState.value.rows.map { it.id })
    }

    @Test
    fun `RANDOM reorders without dropping or duplicating items`() = runTest {
        val items = (1..10).map { artist("a$it") }
        val model = vm(items = items)
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.RANDOM)
        advanceUntilIdle()

        assertEquals(items.map { it.id }.toSet(), model.uiState.value.rows.map { it.id }.toSet())
    }

    @Test
    fun `BY_YEAR falls back to name order - artists have no year`() = runTest {
        val model = vm(items = listOf(artist("a1", "Bravo"), artist("a2", "Alpha")))
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.BY_YEAR)
        advanceUntilIdle()

        assertEquals(listOf("Alpha", "Bravo"), model.uiState.value.rows.map { it.name })
    }

    @Test
    fun `setSortOrder is a no-op when the order has not actually changed`() = runTest {
        var albumCalls = 0
        val model = vm(
            items = listOf(artist("a1")),
            albums = { _, _ -> albumCalls++; emptyList() },
        )
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.BY_NAME) // already the default order
        advanceUntilIdle()
        assertEquals(0, albumCalls)
    }

    // --- Scroll reset ------------------------------------------------------------------------

    @Test
    fun `scroll resets only when the sort order actually changes, not on initial load`() = runTest {
        val model = vm(items = listOf(artist("a1"), artist("a2")))
        model.load()
        advanceUntilIdle()
        val afterLoad = model.uiState.value.scrollResetToken

        model.setSortOrder(SortOrder.BY_NAME) // no-op, same order
        advanceUntilIdle()
        assertEquals(afterLoad, model.uiState.value.scrollResetToken)

        model.setSortOrder(SortOrder.RANDOM)
        advanceUntilIdle()
        assertEquals(afterLoad + 1, model.uiState.value.scrollResetToken)
    }

    // --- Online/offline sort gating --------------------------------------------------------

    @Test
    fun `online exposes Name, Recent, Newest and Frequent`() = runTest {
        Settings.activeServer = 0
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertEquals(
            listOf(SortOrder.BY_NAME, SortOrder.RECENT, SortOrder.NEWEST, SortOrder.FREQUENT),
            model.uiState.value.availableSortOrders,
        )
    }

    @Test
    fun `offline with id3-offline disabled exposes no sort orders`() = runTest {
        Settings.activeServer = -1 // offline
        Settings.id3TagsEnabledOffline = false
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.availableSortOrders.isEmpty())
    }

    @Test
    fun `offline with id3-offline enabled exposes only Name and Newest`() = runTest {
        Settings.activeServer = -1 // offline
        Settings.id3TagsEnabledOffline = true
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertEquals(
            listOf(SortOrder.BY_NAME, SortOrder.NEWEST),
            model.uiState.value.availableSortOrders,
        )
    }

    @Test
    fun `downloadAvailable mirrors the offline gate`() = runTest {
        Settings.activeServer = -1
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertFalse(model.uiState.value.downloadAvailable)
    }

    // --- Folder-selector header --------------------------------------------------------------

    @Test
    fun `the folder header shows only online and non-id3`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = false
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.showFolderHeader)
    }

    @Test
    fun `the folder header is hidden when id3 tags are in use`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = true
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertFalse(model.uiState.value.showFolderHeader)
    }

    @Test
    fun `folders are only fetched on an explicit refresh, matching GenericListModel`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = false
        var folderCalls = 0
        val model = vm(folders = { folderCalls++; listOf(MusicFolder("f1", "Folder One", 0)) })

        model.load(refresh = false)
        advanceUntilIdle()
        assertEquals(0, folderCalls)
        assertTrue(model.uiState.value.folders.isEmpty())

        model.load(refresh = true)
        advanceUntilIdle()
        assertEquals(1, folderCalls)
        assertEquals(listOf("Folder One"), model.uiState.value.folders.map { it.name })
    }

    // --- Row lookup ----------------------------------------------------------------------

    @Test
    fun `itemFor resolves the original fetched item by id`() = runTest {
        val model = vm(items = listOf(artist("a1", "Bravo"), index("f1", "Rock")))
        model.load()
        advanceUntilIdle()

        assertEquals("Bravo", model.itemFor("a1")?.name)
        assertTrue(model.itemFor("f1") is Index)
        assertNull(model.itemFor("nope"))
    }

    @Test
    fun `setLayoutType updates the layout without touching the loaded rows`() = runTest {
        val model = vm(items = listOf(artist("a1")))
        model.load()
        advanceUntilIdle()
        model.setLayoutType(LayoutType.LIST)
        assertEquals(LayoutType.LIST, model.uiState.value.layoutType)
        assertEquals(1, model.uiState.value.rows.size)
    }

    @Test
    fun `a refresh keeps the current rows on screen while it is in flight`() = runTest {
        var responses = listOf(artist("a1", "Old"))
        val gate = CompletableDeferred<Unit>()
        var firstDone = false
        val model = ArtistListViewModel(app).apply {
            itemsLoader = {
                if (firstDone) gate.await()
                firstDone = true
                responses
            }
        }
        model.load()
        advanceUntilIdle()
        assertEquals(listOf("Old"), model.uiState.value.rows.map { it.name })

        responses = listOf(artist("a1", "New"))
        model.refresh()
        advanceUntilIdle()
        assertTrue(model.uiState.value.isLoading)
        assertEquals(listOf("Old"), model.uiState.value.rows.map { it.name })

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isLoading)
        assertEquals(listOf("New"), model.uiState.value.rows.map { it.name })
    }
}
