/*
 * AlbumListViewModelTest.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.ui.albumlist

import android.app.Application
import androidx.test.core.app.ApplicationProvider
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
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.model.AlbumListViewModel
import org.moire.ultrasonic.service.RobolectricUAppContext
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder
import org.robolectric.RobolectricTestRunner

/**
 * [AlbumListViewModel] projection, ported from the legacy `AlbumListModel`/`AlbumListFragment`
 * (issue #10 phase 4E2): the id3/folder-mode paged load and its `loadedUntil` pagination
 * accounting, the "by artist" unpaged mode and its client-side re-sort
 * (`AlbumListModel.sortListByOrder`, including its `Album.name`-is-always-null no-op quirk), the
 * `RANDOM`+refresh "clear before refetch" behaviour, the `BY_GENRE` re-prompt flow, the
 * load-once/refresh guard (composed exactly like `fetchAlbums`'s `refresh = refresh or append`),
 * the online/offline/id3 sort-order gating for both modes, and the folder-selector header's
 * alphabetical-only gate (`AlbumListModel.showSelectFolderHeader()`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AlbumListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var activeServerProvider: ActiveServerProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        RobolectricUAppContext.install()
        app = ApplicationProvider.getApplicationContext()

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

    private fun album(
        id: String,
        title: String = "Album $id",
        artist: String? = null,
        year: Int? = null,
    ) = Album(id = id, title = title, artist = artist, year = year)

    private fun vm(
        type: AlbumListType = AlbumListType.SORTED_BY_NAME,
        byArtist: Boolean = false,
        artistId: String? = null,
        artistName: String? = null,
        size: Int = 20,
        offset: Int = 0,
        albums: suspend (AlbumListType, Int, Int, String?, String?) -> List<Album> =
            { _, _, _, _, _ -> emptyList() },
        artistAlbums: suspend (String, String?, Boolean) -> List<Album> = { _, _, _ -> emptyList() },
        genres: suspend () -> List<Genre> = { emptyList() },
        folders: suspend () -> List<MusicFolder> = { emptyList() },
    ) = AlbumListViewModel(app).apply {
        albumsLoader = albums
        artistAlbumsLoader = artistAlbums
        genresLoader = genres
        musicFoldersLoader = folders
        initialize(type, byArtist, artistId, artistName, size, offset)
    }

    // --- Initial load, paged mode ---------------------------------------------------------

    @Test
    fun `the default state is loading`() {
        val state = vm().uiState.value
        assertTrue(state.isLoading)
        assertFalse(state.hasContent)
    }

    @Test
    fun `a paged load fills rows from title and artist`() = runTest {
        val model = vm(
            albums = { _, _, _, _, _ -> listOf(album("a1", title = "OK Computer", artist = "Radiohead")) },
        )
        model.load()
        advanceUntilIdle()

        val row = model.uiState.value.rows.single()
        assertEquals("OK Computer", row.title)
        assertEquals("Radiohead", row.artist)
        assertFalse(model.uiState.value.isLoading)
    }

    @Test
    fun `the initial sort order and page size come from the nav args`() = runTest {
        var requestedSize = -1
        val model = vm(
            type = AlbumListType.STARRED,
            size = 50,
            albums = { _, size, _, _, _ -> requestedSize = size; emptyList() },
        )
        assertEquals(SortOrder.STARRED, model.uiState.value.sortOrder)
        model.load()
        advanceUntilIdle()
        assertEquals(50, requestedSize)
    }

    @Test
    fun `a zero or negative nav-arg size falls back to the default page size`() = runTest {
        var requestedSize = -1
        val model = vm(size = 0, albums = { _, size, _, _, _ -> requestedSize = size; emptyList() })
        model.load()
        advanceUntilIdle()
        assertEquals(20, requestedSize)
    }

    // --- Load-once / refresh ----------------------------------------------------------------

    @Test
    fun `the album list is not re-fetched on a second load, but refresh forces it`() = runTest {
        var calls = 0
        val model = vm(albums = { _, _, _, _, _ -> calls++; listOf(album("a1")) })
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
        val model = vm(albums = { _, _, _, _, _ -> throw java.io.IOException("server down") })
        model.load()
        advanceUntilIdle()

        val state = model.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.loadFailed)
    }

    @Test
    fun `a cancelled load is not mistaken for a failed load`() = runTest {
        val model = vm(albums = { _, _, _, _, _ -> throw CancellationException("scope died") })
        model.load()
        runCatching { advanceUntilIdle() }
        assertFalse(model.uiState.value.loadFailed)
    }

    // --- Pagination / infinite scroll --------------------------------------------------------

    @Test
    fun `loadMore appends the next page at an offset accumulated by page size`() = runTest {
        val offsets = mutableListOf<Int>()
        val model = vm(
            size = 20,
            albums = { _, _, offset, _, _ ->
                offsets += offset
                listOf(album("a-$offset"))
            },
        )
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, 20, 40), offsets)
        assertEquals(3, model.uiState.value.rows.size)
    }

    @Test
    fun `loadMore stops once a page comes back empty`() = runTest {
        var calls = 0
        val model = vm(
            albums = { _, _, offset, _, _ ->
                calls++
                if (offset == 0) listOf(album("a1")) else emptyList()
            },
        )
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        val callsAfterEmptyPage = calls
        model.loadMore()
        advanceUntilIdle()

        assertEquals(callsAfterEmptyPage, calls) // the second loadMore never re-fetched
    }

    @Test
    fun `a refresh resets pagination so the next loadMore starts from offset 0 again`() = runTest {
        val offsets = mutableListOf<Int>()
        val model = vm(albums = { _, _, offset, _, _ -> offsets += offset; listOf(album("a-$offset")) })
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        model.refresh()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(0, 20, 0, 20), offsets)
    }

    @Test
    fun `loadMore is a no-op in by-artist mode`() = runTest {
        var pagedCalls = 0
        val model = vm(
            byArtist = true,
            artistId = "ar1",
            albums = { _, _, _, _, _ -> pagedCalls++; emptyList() },
            artistAlbums = { _, _, _ -> listOf(album("a1"), album("a2")) },
        )
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()

        assertEquals(0, pagedCalls)
        assertEquals(2, model.uiState.value.rows.size)
    }

    // --- RANDOM refresh quirk ---------------------------------------------------------------

    @Test
    fun `refreshing a RANDOM list clears rows and restarts pagination before refetching`() = runTest {
        val offsets = mutableListOf<Int>()
        val model = vm(
            type = AlbumListType.RANDOM,
            albums = { _, _, offset, _, _ -> offsets += offset; listOf(album("a-$offset")) },
        )
        model.load()
        advanceUntilIdle()
        model.loadMore()
        advanceUntilIdle()
        assertEquals(2, model.uiState.value.rows.size)

        model.refresh()
        advanceUntilIdle()

        assertEquals(listOf(0, 20, 0), offsets)
        assertEquals(1, model.uiState.value.rows.size) // replaced, not appended to the old page
    }

    // --- By-artist mode: client-side re-sort -------------------------------------------------

    @Test
    fun `by-artist BY_YEAR sorts client-side without re-fetching`() = runTest {
        var fetches = 0
        val model = vm(
            byArtist = true,
            artistId = "ar1",
            artistAlbums = { _, _, _ ->
                fetches++
                listOf(album("a1", year = 2020), album("a2", year = 1999))
            },
        )
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.BY_YEAR)

        assertEquals(1, fetches)
        assertEquals(listOf("a2", "a1"), model.uiState.value.rows.map { it.id })
        assertEquals(SortOrder.BY_YEAR, model.uiState.value.sortOrder)
    }

    @Test
    fun `by-artist BY_NAME is a stable no-op - Album name is never populated by the API converters`() =
        runTest {
            val model = vm(
                byArtist = true,
                artistId = "ar1",
                artistAlbums = { _, _, _ -> listOf(album("a1"), album("a2"), album("a3")) },
            )
            model.load()
            advanceUntilIdle()
            model.setSortOrder(SortOrder.BY_NAME)

            assertEquals(listOf("a1", "a2", "a3"), model.uiState.value.rows.map { it.id })
        }

    @Test
    fun `by-artist mode always exposes exactly BY_NAME and BY_YEAR`() = runTest {
        Settings.activeServer = -1 // offline - would normally gate the paged-mode list to empty
        val model = vm(byArtist = true, artistId = "ar1")
        model.load()
        advanceUntilIdle()
        assertEquals(listOf(SortOrder.BY_NAME, SortOrder.BY_YEAR), model.uiState.value.availableSortOrders)
    }

    // --- BY_GENRE ------------------------------------------------------------------------------

    @Test
    fun `selecting BY_GENRE from the menu only updates the chip, it does not fetch`() = runTest {
        var albumCalls = 0
        val model = vm(albums = { _, _, _, _, _ -> albumCalls++; emptyList() })
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.BY_GENRE)

        assertEquals(SortOrder.BY_GENRE, model.uiState.value.sortOrder)
        assertEquals(1, albumCalls) // only the initial load, not a BY_GENRE fetch
    }

    @Test
    fun `loadGenres delegates to the genre-list loader`() = runTest {
        val model = vm(genres = { listOf(Genre("1", "Jazz"), Genre("2", "Rock")) })
        val genres = model.loadGenres()
        assertEquals(listOf("Jazz", "Rock"), genres.map { it.name })
    }

    @Test
    fun `selectGenre fetches that genre's paged album list`() = runTest {
        val model = vm(
            albums = { type, _, _, genre, _ ->
                assertEquals(AlbumListType.BY_GENRE, type)
                assertEquals("Jazz", genre)
                listOf(album("a1"))
            },
        )
        model.selectGenre("Jazz")
        advanceUntilIdle()

        assertEquals(listOf("a1"), model.uiState.value.rows.map { it.id })
    }

    // --- Sort orders (normal, paged mode) -----------------------------------------------------

    @Test
    fun `online, folder-mode exposes every sort order the legacy filter bar did`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = false // HIGHEST is only ever offered in folder mode
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertEquals(
            listOf(
                SortOrder.NEWEST,
                SortOrder.RECENT,
                SortOrder.FREQUENT,
                SortOrder.HIGHEST,
                SortOrder.RANDOM,
                SortOrder.STARRED,
                SortOrder.BY_NAME,
                SortOrder.BY_ARTIST,
                SortOrder.BY_GENRE,
            ),
            model.uiState.value.availableSortOrders,
        )
    }

    @Test
    fun `id3 online hides HIGHEST`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = true
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertFalse(SortOrder.HIGHEST in model.uiState.value.availableSortOrders)
    }

    @Test
    fun `offline with id3-offline disabled exposes no sort orders`() = runTest {
        Settings.activeServer = -1
        Settings.id3TagsEnabledOffline = false
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.availableSortOrders.isEmpty())
    }

    @Test
    fun `offline with id3-offline enabled exposes Newest, Name, Artist and Genre only`() = runTest {
        Settings.activeServer = -1
        Settings.id3TagsEnabledOffline = true
        val model = vm()
        model.load()
        advanceUntilIdle()
        assertEquals(
            listOf(SortOrder.NEWEST, SortOrder.BY_NAME, SortOrder.BY_ARTIST, SortOrder.BY_GENRE),
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

    @Test
    fun `changing sort order re-fetches with the new AlbumListType`() = runTest {
        val requestedTypes = mutableListOf<AlbumListType>()
        val model = vm(albums = { type, _, _, _, _ -> requestedTypes += type; emptyList() })
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.NEWEST)
        advanceUntilIdle()

        assertEquals(listOf(AlbumListType.SORTED_BY_NAME, AlbumListType.NEWEST), requestedTypes)
    }

    @Test
    fun `setSortOrder is a no-op when the order has not actually changed`() = runTest {
        var calls = 0
        val model = vm(albums = { _, _, _, _, _ -> calls++; emptyList() })
        model.load()
        advanceUntilIdle()
        model.setSortOrder(SortOrder.BY_NAME) // already the default for SORTED_BY_NAME
        advanceUntilIdle()
        assertEquals(1, calls)
    }

    // --- Folder-selector header ----------------------------------------------------------------

    @Test
    fun `the folder header shows only online, folder-mode, and alphabetical order`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = false
        val model = vm(type = AlbumListType.SORTED_BY_NAME)
        model.load()
        advanceUntilIdle()
        assertTrue(model.uiState.value.showFolderHeader)
    }

    @Test
    fun `the folder header is hidden for a non-alphabetical order even in folder mode`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = false
        val model = vm(type = AlbumListType.NEWEST)
        model.load()
        advanceUntilIdle()
        assertFalse(model.uiState.value.showFolderHeader)
    }

    @Test
    fun `the folder header is hidden when id3 tags are in use`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = true
        val model = vm(type = AlbumListType.SORTED_BY_NAME)
        model.load()
        advanceUntilIdle()
        assertFalse(model.uiState.value.showFolderHeader)
    }

    @Test
    fun `the folder header never shows in by-artist mode`() = runTest {
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = false
        val model = vm(byArtist = true, artistId = "ar1")
        model.load()
        advanceUntilIdle()
        assertFalse(model.uiState.value.showFolderHeader)
    }

    @Test
    fun `onFolderSelected refreshes only the folder list, not the album page`() = runTest {
        var albumCalls = 0
        Settings.activeServer = 0
        Settings.id3TagsEnabledOnline = false
        val model = vm(
            type = AlbumListType.SORTED_BY_NAME,
            albums = { _, _, _, _, _ -> albumCalls++; listOf(album("a1")) },
            folders = { listOf(MusicFolder("f1", "Classical", 0)) },
        )
        model.load()
        advanceUntilIdle()
        assertEquals(1, albumCalls)
        assertTrue(model.uiState.value.folders.isEmpty())

        model.onFolderSelected()
        advanceUntilIdle()

        assertEquals(1, albumCalls) // unchanged - the album page was not reloaded
        assertEquals(listOf("Classical"), model.uiState.value.folders.map { it.name })
    }

    // --- Row lookup / layout ---------------------------------------------------------------

    @Test
    fun `itemFor resolves the original fetched item by id`() = runTest {
        val model = vm(albums = { _, _, _, _, _ -> listOf(album("a1", title = "Bravo")) })
        model.load()
        advanceUntilIdle()

        assertEquals("Bravo", model.itemFor("a1")?.title)
        assertNull(model.itemFor("nope"))
    }

    @Test
    fun `setLayoutType updates the layout without touching the loaded rows`() = runTest {
        val model = vm(albums = { _, _, _, _, _ -> listOf(album("a1")) })
        model.load()
        advanceUntilIdle()
        model.setLayoutType(LayoutType.LIST)
        assertEquals(LayoutType.LIST, model.uiState.value.layoutType)
        assertEquals(1, model.uiState.value.rows.size)
    }
}
