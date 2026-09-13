/*
 * AlbumListViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.albumlist.AlbumListRow
import org.moire.ultrasonic.ui.albumlist.AlbumListUiState
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder

/**
 * Owns the Compose Album List state (issue #10 phase 4E2) as one
 * [StateFlow]<[AlbumListUiState]>. A 1:1 projection of `AlbumListModel`/`AlbumListFragment`:
 *
 * - the id3 `getAlbumList2()` / folder-mode `getAlbumList()` paged load (page size from the nav
 *   arg, default 20), with the exact same offset/`loadedUntil` accumulation
 *   `AlbumListModel.getAlbums` used for "append" (infinite scroll);
 * - the "by artist" mode (`getAlbumsOfArtist`), which is a single unpaged fetch with its own
 *   fixed `[BY_NAME, BY_YEAR]` sort-order capability and a client-side re-sort
 *   (`AlbumListModel.sortListByOrder`) instead of a re-fetch;
 * - the `BY_GENRE` order, which (like the legacy screen) always re-prompts a genre choice - the
 *   actual dialog is Fragment-hosted (see [beginGenreSort]/[loadGenres]/[selectGenre]), this
 *   class only owns the resulting fetch;
 * - the load-once-across-back-navigation guard and the `RANDOM`+refresh "clear before refetch"
 *   quirk, both ported from `AlbumListModel.getAlbums` verbatim;
 * - the folder-selector header, gated on "online, folder-mode, *and currently sorted
 *   alphabetically*" - `AlbumListModel.showSelectFolderHeader()`'s exact condition, not just
 *   online/non-id3 (contrast [org.moire.ultrasonic.model.ArtistListViewModel]).
 *
 * Navigation and playback stay in the Fragment; this class only reads.
 */
class AlbumListViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(AlbumListUiState())
    val uiState: StateFlow<AlbumListUiState> = _uiState.asStateFlow()

    /** The full, currently-loaded album set - `AlbumListModel.list`'s equivalent. Re-sorting in
     *  "by artist" mode never re-fetches this. */
    private var allAlbums: List<Album> = emptyList()

    /** `AlbumListModel.lastType` - the type of the last successful *paged* fetch. Never set in
     *  "by artist" mode, exactly like the legacy model (`getAlbumsOfArtist` never touches it),
     *  which is why the folder header never shows there. */
    private var lastType: AlbumListType? = null
    private var loadedUntil: Int = 0
    private var hasMorePages: Boolean = true

    private var byArtist: Boolean = false
    private var artistId: String? = null
    private var artistName: String? = null
    private var pageSize: Int = DEFAULT_PAGE_SIZE
    private var initialOffset: Int = 0

    /** `BY_GENRE`'s genre filter, set only via [selectGenre] - the Fragment-hosted genre-picker
     *  dialog result, exactly like the legacy `AlbumListFragment.selectedGenre`. */
    internal var selectedGenre: String? = null

    private var initialized: Boolean = false

    private var loadJob: Job? = null

    /** id3 -> `getAlbumList2`; folder -> `getAlbumList`. Test seam. */
    internal var albumsLoader:
        suspend (AlbumListType, size: Int, offset: Int, genre: String?, folderId: String?) -> List<Album> =
        { type, size, offset, genre, folderId ->
            withContext(Dispatchers.IO) {
                val service = MusicServiceFactory.getMusicService()
                if (ActiveServerProvider.shouldUseId3Tags()) {
                    service.getAlbumList2(type, size, offset, genre, folderId)
                } else {
                    service.getAlbumList(type, size, offset, folderId)
                }
            }
        }

    /** "By artist" mode's data source. Test seam. */
    internal var artistAlbumsLoader: suspend (id: String, name: String?, refresh: Boolean) -> List<Album> =
        { id, name, refresh ->
            withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService().getAlbumsOfArtist(id, name, refresh)
            }
        }

    /** `BY_GENRE`'s genre-list source, fetched by the Fragment before showing its picker
     *  dialog. Test seam. */
    internal var genresLoader: suspend () -> List<Genre> = {
        withContext(Dispatchers.IO) { MusicServiceFactory.getMusicService().getGenres(true) }
    }

    /** The folder-selector header's own dropdown data, fetched only by [onFolderSelected] - see
     *  its kdoc for why a plain load/refresh never touches this. Test seam. */
    internal var musicFoldersLoader: suspend () -> List<MusicFolder> = {
        withContext(Dispatchers.IO) { MusicServiceFactory.getMusicService().getMusicFolders(true) }
    }

    /**
     * Sets up the fixed, nav-arg-derived parameters for this screen instance - mirrors what
     * `AlbumListFragment`'s constructor args / `navArgs` fix for the lifetime of the Fragment.
     * The Fragment calls this unconditionally from `onViewCreated` (its View, unlike this
     * retained ViewModel, is recreated on back-navigation); a no-op after the first call so a
     * user-chosen sort order or genre survives exactly like the legacy `orderType`/
     * `selectedGenre` Fragment fields did.
     */
    fun initialize(
        type: AlbumListType,
        byArtist: Boolean,
        artistId: String?,
        artistName: String?,
        size: Int,
        offset: Int,
    ) {
        if (initialized) return
        initialized = true
        this.byArtist = byArtist
        this.artistId = artistId
        this.artistName = artistName
        this.pageSize = if (size > 0) size else DEFAULT_PAGE_SIZE
        this.initialOffset = offset
        // getAlbumsOfArtist never touches AlbumListModel.lastType - the folder header must never
        // gate on it in "by artist" mode either.
        if (!byArtist) lastType = type

        val initialOrder = type.toSortOrder()
        _uiState.update {
            it.copy(
                sortOrder = initialOrder,
                availableSortOrders = availableSortOrders().toImmutableList(),
            )
        }
    }

    /**
     * Load once. Idempotent across Fragment view recreation for the *paged* (non "by artist")
     * path - returns immediately if [allAlbums] is already populated for the same
     * [AlbumListType] and no explicit refresh was requested, exactly like
     * `AlbumListModel.getAlbums`'s `!refresh && list.value?.isEmpty() == false && albumListType
     * == lastType` guard. "By artist" mode has no such guard (`getAlbumsOfArtist` never had one
     * either).
     */
    fun load(refresh: Boolean = false, append: Boolean = false) {
        loadJob?.cancel()

        if (byArtist) {
            loadJob = viewModelScope.launch { loadByArtist(refresh) }
            return
        }

        val order = _uiState.value.sortOrder
        if (order == SortOrder.BY_GENRE && selectedGenre == null) {
            // Never actually reachable from a fresh nav (no caller starts on BY_GENRE) - the
            // real entry point for a genre fetch is selectGenre(), once the Fragment-hosted
            // picker has resolved a choice.
            return
        }
        val requestedType = order.toAlbumListType()
        // AlbumListFragment.fetchAlbums always calls getAlbums with `refresh = refresh or
        // append` - an in-flight append is itself a reason to bypass the load-once guard below,
        // exactly like the legacy composition.
        val effectiveRefresh = refresh || append
        if (!effectiveRefresh && allAlbums.isNotEmpty() && requestedType == lastType) return
        lastType = requestedType

        loadJob = viewModelScope.launch { loadPaged(requestedType, effectiveRefresh, append) }
    }

    /** Pull-to-refresh: re-fetch through [load], keeping the current sort order/genre. */
    fun refresh() {
        hasMorePages = true
        load(refresh = true)
    }

    /**
     * The Compose equivalent of `EndlessScrollListener.onLoadMore` - fired by the screen when
     * the scroll position nears the end of the loaded rows. A no-op in "by artist" mode (that
     * fetch is never paged), while a load is already in flight, or once a page has come back
     * empty (mirrors `EndlessScrollListener`'s own `loading` flag getting permanently stuck true
     * after a page adds nothing new - the practical effect of that flag bug is "stop trying
     * after one empty page", reproduced here explicitly via [hasMorePages]).
     */
    fun loadMore() {
        if (byArtist || !hasMorePages) return
        if (loadJob?.isActive == true) return
        load(refresh = false, append = true)
    }

    /**
     * `SortOrder.BY_GENRE`, tapped: mirrors `AlbumListFragment.setOrderType` immediately
     * reflecting the new selection in the filter chip, before any genre is actually chosen -
     * the Fragment follows this with [loadGenres] + its own picker dialog + [selectGenre].
     */
    fun beginGenreSort() {
        _uiState.update { it.copy(sortOrder = SortOrder.BY_GENRE) }
    }

    /** The genre-list fetch behind the Fragment's picker dialog. Suspends; the Fragment decides
     *  whether to show the dialog (never shown when this returns empty, exactly like
     *  `AlbumListFragment.fetchAlbumsByGenre`). */
    suspend fun loadGenres(): List<Genre> = genresLoader()

    /** The genre picker resolved to [name] - fetches that genre's paged album list, exactly
     *  like `AlbumListFragment`'s `ItemSelectionDialogFragment` result listener. */
    fun selectGenre(name: String) {
        selectedGenre = name
        hasMorePages = true
        lastType = AlbumListType.BY_GENRE
        loadJob?.cancel()
        loadJob = viewModelScope.launch { loadPaged(AlbumListType.BY_GENRE, refresh = true, append = false) }
    }

    /**
     * `SortOrder`, selected from the sort menu. `BY_GENRE` always routes through
     * [beginGenreSort] (the Fragment intercepts it before calling this). "By artist" mode
     * re-sorts [allAlbums] client-side instead of re-fetching, exactly like
     * `AlbumListModel.sortListByOrder`.
     */
    fun setSortOrder(order: SortOrder) {
        if (order == SortOrder.BY_GENRE) {
            beginGenreSort()
            return
        }
        if (byArtist) {
            sortByArtistOrder(order)
            return
        }
        if (order == _uiState.value.sortOrder) return
        _uiState.update { it.copy(sortOrder = order) }
        hasMorePages = true
        load(refresh = true, append = false)
    }

    fun setLayoutType(type: LayoutType) {
        _uiState.update { it.copy(layoutType = type) }
    }

    /**
     * The folder-selector header's selection. Mirrors a real, pre-existing gap in the legacy
     * screen: `EntryListFragment`'s folder-changed subscriber calls `GenericListModel.refresh()`,
     * which for `AlbumListModel` resolves to the un-overridden `GenericListModel.load()` base
     * method - that only re-fetches the *folder list itself* (so the header's own dropdown stays
     * current), never the albums. Selecting a different folder here does not reload the album
     * page either, preserved as-is rather than fixed as part of this migration (see the phase
     * 4E2 report).
     */
    fun onFolderSelected() {
        viewModelScope.launch { refreshFoldersOnly() }
    }

    /** The original fetched item for a row id - the Fragment's navigation/context-menu dispatch
     *  reads this, exactly like [org.moire.ultrasonic.model.ArtistListViewModel.itemFor]. */
    fun itemFor(id: String): Album? = allAlbums.firstOrNull { it.id == id }

    private suspend fun loadByArtist(refresh: Boolean) {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        val result = try {
            artistAlbumsLoader(artistId!!, artistName, refresh)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
            null
        }
        if (result == null) {
            _uiState.update { it.copy(isLoading = false, loadFailed = allAlbums.isEmpty()) }
            return
        }
        allAlbums = result
        publish()
    }

    private suspend fun loadPaged(type: AlbumListType, refresh: Boolean, append: Boolean) {
        // Legacy `fetchAlbums(append = true)` never touches `swipeRefresh.isRefreshing` - only
        // an explicit (non-append) refresh does. isLoading drives the pull-to-refresh spinner,
        // so it stays untouched here too; an append failure/success only ever changes the rows.
        if (!append) _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        // Avoid items jumping around when refreshing a RANDOM list - AlbumListModel.getAlbums's
        // exact quirk.
        if (refresh && !append && type == AlbumListType.RANDOM) {
            allAlbums = emptyList()
            loadedUntil = 0
        }

        var offset = initialOffset
        if (append) offset += (pageSize + loadedUntil)

        val folderId = if (showFolderHeaderNow(type)) {
            activeServerProvider.getActiveServer().musicFolderId
        } else {
            null
        }
        val genre = if (type == AlbumListType.BY_GENRE) selectedGenre else null

        val fetched = try {
            albumsLoader(type, pageSize, offset, genre, folderId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
            null
        }

        if (fetched == null) {
            _uiState.update { it.copy(isLoading = false, loadFailed = allAlbums.isEmpty()) }
            return
        }

        if (append && fetched.isEmpty()) hasMorePages = false

        allAlbums = if (append) allAlbums + fetched else fetched
        loadedUntil = offset

        publish()
    }

    private var cachedFolders: List<MusicFolder> = emptyList()

    /** `AlbumListModel` never overrides `GenericListModel.load()`, so - unlike a normal album
     *  reload - only the folder-changed event actually re-fetches [cachedFolders] (via the base
     *  `load()`'s own gate, reproduced here). A plain pull-to-refresh or server switch never
     *  touches it, and [cachedFolders] starts empty (the header's dropdown offers only "All
     *  Folders" until the user has changed folders at least once) - both preserved exactly as
     *  the legacy screen behaves, not fixed as part of this migration (see the phase 4E2
     *  report). */
    private suspend fun refreshFoldersOnly() {
        cachedFolders = try {
            musicFoldersLoader()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
            cachedFolders
        }
        publish()
    }

    private fun publish() {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                loadFailed = false,
                availableSortOrders = availableSortOrders().toImmutableList(),
                showFolderHeader = showFolderHeaderNow(lastType),
                folders = cachedFolders.toImmutableList(),
                selectedFolderId = activeServerProvider.getActiveServer().musicFolderId,
                downloadAvailable = !ActiveServerProvider.isOffline(),
                rows = allAlbums.map { it.toRow() }.toImmutableList(),
            )
        }
    }

    /** `AlbumListModel.sortListByOrder` ported verbatim, including its `BY_NAME` branch sorting
     *  by `Album.name` - a field the API converters never populate (always null), so that branch
     *  is a pre-existing no-op. Preserved rather than "fixed" (see the phase 4E2 report). */
    private fun sortByArtistOrder(order: SortOrder) {
        allAlbums = when (order) {
            SortOrder.BY_YEAR -> allAlbums.sortedBy { it.year }
            else -> allAlbums.sortedBy { it.name }
        }
        _uiState.update { it.copy(sortOrder = order) }
        publish()
    }

    /** `AlbumListFragment.getListOfSortOrders()` for the normal (non "by artist") path; the
     *  fixed `[BY_NAME, BY_YEAR]` capability override for "by artist" mode
     *  (`AlbumListFragment.setupFilterBar`). */
    @Suppress("CyclomaticComplexMethod")
    private fun availableSortOrders(): List<SortOrder> {
        if (byArtist) return listOf(SortOrder.BY_NAME, SortOrder.BY_YEAR)

        val useId3 = Settings.id3TagsEnabledOnline
        val useId3Offline = Settings.id3TagsEnabledOffline
        val isOnline = !ActiveServerProvider.isOffline()

        return buildList {
            if (isOnline || useId3Offline) add(SortOrder.NEWEST)
            if (isOnline) add(SortOrder.RECENT)
            if (isOnline) add(SortOrder.FREQUENT)
            if (isOnline && !useId3) add(SortOrder.HIGHEST)
            if (isOnline) add(SortOrder.RANDOM)
            if (isOnline) add(SortOrder.STARRED)
            if (isOnline || useId3Offline) add(SortOrder.BY_NAME)
            if (isOnline || useId3Offline) add(SortOrder.BY_ARTIST)
            if (isOnline || useId3Offline) add(SortOrder.BY_GENRE)
        }
    }

    /** `AlbumListModel.showSelectFolderHeader()`: online, folder-mode, *and* currently sorted
     *  alphabetically - never true in "by artist" mode since [lastType] is never set there. */
    private fun showFolderHeaderNow(type: AlbumListType?): Boolean {
        val isAlphabetical = type == AlbumListType.SORTED_BY_NAME || type == AlbumListType.SORTED_BY_ARTIST
        return !ActiveServerProvider.isOffline() && !ActiveServerProvider.shouldUseId3Tags() && isAlphabetical
    }

    private fun Album.toRow() = AlbumListRow(
        id = id,
        title = title.orEmpty(),
        artist = artist.orEmpty(),
        artworkModel = coverArtRequestOrNull(large = false),
    )

    private fun AlbumListType.toSortOrder(): SortOrder = when (this) {
        AlbumListType.RANDOM -> SortOrder.RANDOM
        AlbumListType.NEWEST -> SortOrder.NEWEST
        AlbumListType.HIGHEST -> SortOrder.HIGHEST
        AlbumListType.FREQUENT -> SortOrder.FREQUENT
        AlbumListType.RECENT -> SortOrder.RECENT
        AlbumListType.SORTED_BY_NAME -> SortOrder.BY_NAME
        AlbumListType.SORTED_BY_ARTIST -> SortOrder.BY_ARTIST
        AlbumListType.BY_GENRE -> SortOrder.BY_GENRE
        AlbumListType.STARRED -> SortOrder.STARRED
        AlbumListType.BY_YEAR -> SortOrder.BY_YEAR
    }

    private fun SortOrder.toAlbumListType(): AlbumListType = when (this) {
        SortOrder.ALL_SONGS -> error("All songs is only supported by the song library")
        SortOrder.RANDOM -> AlbumListType.RANDOM
        SortOrder.NEWEST -> AlbumListType.NEWEST
        SortOrder.HIGHEST -> AlbumListType.HIGHEST
        SortOrder.FREQUENT -> AlbumListType.FREQUENT
        SortOrder.RECENT -> AlbumListType.RECENT
        SortOrder.BY_NAME -> AlbumListType.SORTED_BY_NAME
        SortOrder.BY_ARTIST -> AlbumListType.SORTED_BY_ARTIST
        SortOrder.BY_GENRE -> AlbumListType.BY_GENRE
        SortOrder.STARRED -> AlbumListType.STARRED
        SortOrder.BY_YEAR -> AlbumListType.BY_YEAR
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE = 20
    }
}
