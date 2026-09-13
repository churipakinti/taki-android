/*
 * ArtistListViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.text.Collator
import java.util.Locale
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
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.MusicFolder
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.imageloader.artistArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.artistlist.ArtistListRow
import org.moire.ultrasonic.ui.artistlist.ArtistListUiState
import org.moire.ultrasonic.util.LayoutType
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder

/**
 * Owns the Compose Artist List state (issue #10 phase 4E1) as one
 * [StateFlow]<[ArtistListUiState]>. A 1:1 projection of `ArtistListModel`: the id3
 * `getArtists()` / folder-mode `getIndexes()` load, the load-once-across-back-navigation guard,
 * the album-derived sort orders (`NEWEST`/`RECENT`/`FREQUENT`/`HIGHEST` fetch an album list and
 * keep only the artists represented in it; `STARRED` unions starred-artist and starred-album
 * matches; `BY_GENRE` with a genre re-derives the same way), and the folder-selector header for
 * non-id3 servers. Navigation and playback stay in the Fragment; this class only reads.
 *
 * The state survives Fragment view recreation (retained ViewModel), so returning from Artist
 * Detail / a folder restores the list with no new server call.
 */
class ArtistListViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(ArtistListUiState())
    val uiState: StateFlow<ArtistListUiState> = _uiState.asStateFlow()

    /** The full, unfiltered/unsorted item set from the last successful load - re-sorting for a
     *  new order never re-fetches this, exactly like `ArtistListModel.allArtists`. */
    private var allItems: List<ArtistOrIndex> = emptyList()
    private var cachedFolders: List<MusicFolder> = emptyList()
    private var sortJob: Job? = null

    /** `BY_GENRE`'s genre filter. The legacy filter bar never exposed a way to set this
     *  (`ArtistListFragment.getListOfSortOrders()` never includes `BY_GENRE`), so it is never
     *  set by the real UI in phase 4E1 - kept `internal var` so the branch stays testable
     *  instead of dead, ready for 4E2's genre-picker interop decision. */
    internal var selectedGenre: String? = null

    /** id3 -> `getArtists`; folder -> `getIndexes`. A null folder-mode result means "nothing
     *  changed" (see `MusicService.getIndexes`) - the caller keeps [allItems]. Test seam. */
    internal var itemsLoader: suspend (refresh: Boolean) -> List<ArtistOrIndex>? = { refresh ->
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            if (ActiveServerProvider.shouldUseId3Tags()) {
                service.getArtists(refresh)
            } else {
                service.getIndexes(activeServerProvider.getActiveServer().musicFolderId, refresh)
            }
        }
    }

    /** The album-backed sort orders' data source - id3 -> `getAlbumList2`, folder ->
     *  `getAlbumList`, exactly like `ArtistListModel.getAlbums`. Test seam. */
    internal var albumsLoader: suspend (AlbumListType, genre: String?) -> List<Album> =
        { type, genre ->
            withContext(Dispatchers.IO) {
                val service = MusicServiceFactory.getMusicService()
                val musicFolderId = activeServerProvider.getActiveServer().musicFolderId
                if (genre != null || ActiveServerProvider.shouldUseId3Tags()) {
                    service.getAlbumList2(type, Settings.MAX_ALBUMS, 0, genre, musicFolderId)
                } else {
                    service.getAlbumList(type, Settings.MAX_ALBUMS, 0, musicFolderId)
                }
            }
        }

    /** `STARRED`'s data source - id3 -> `getStarred2`, folder -> `getStarred`. Test seam. */
    internal var starredLoader: suspend () -> SearchResult = {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            if (ActiveServerProvider.shouldUseId3Tags()) service.getStarred2() else service.getStarred()
        }
    }

    /** The folder-selector header's data source. Only ever called when [showFolderHeaderNow]
     *  and a refresh is in flight, mirroring `GenericListModel.load`'s
     *  `showSelectFolderHeader() && !isOffline && !useId3Tags && refresh` gate exactly. Test
     *  seam. */
    internal var musicFoldersLoader: suspend () -> List<MusicFolder> = {
        withContext(Dispatchers.IO) { MusicServiceFactory.getMusicService().getMusicFolders(true) }
    }

    /**
     * Load once. Idempotent across Fragment view recreation - returns immediately if
     * [allItems] is already populated and no explicit refresh was requested, exactly like
     * `ArtistListModel.getItems`'s `artists.value?.isEmpty() != false || refresh` guard.
     */
    fun load(refresh: Boolean = false) {
        if (!refresh && allItems.isNotEmpty()) return

        _uiState.update { it.copy(isLoading = true, loadFailed = false) }

        viewModelScope.launch {
            val result = try {
                itemsLoader(refresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }

            if (result != null) allItems = result

            if (result == null && allItems.isEmpty()) {
                _uiState.update { it.copy(isLoading = false, loadFailed = true) }
                return@launch
            }

            applySort(_uiState.value.sortOrder, resetScroll = false, refreshFolders = refresh)
        }
    }

    /** Pull-to-refresh: re-fetch through [load], keeping the current sort order. */
    fun refresh() = load(refresh = true)

    /**
     * Re-derive the displayed rows for a new order from the already-loaded [allItems] - never
     * re-fetches the base artist/index list, exactly like `ArtistListModel.setSortOrder`. A
     * no-op if the order hasn't actually changed (matches the legacy `newOrder != orderType`
     * guard that gates `resetScrollOnNextUpdate`).
     */
    fun setSortOrder(order: SortOrder) {
        if (order == _uiState.value.sortOrder) return
        applySort(order, resetScroll = true, refreshFolders = false)
    }

    fun setLayoutType(type: LayoutType) {
        _uiState.update { it.copy(layoutType = type) }
    }

    /** The original fetched item for a row id - the Fragment's context-menu dispatch reads
     *  this, exactly like `AlbumDetailViewModel.trackFor`/`ArtistDetailViewModel`'s row
     *  lookups. */
    fun itemFor(id: String): ArtistOrIndex? = allItems.firstOrNull { it.id == id }

    private fun applySort(order: SortOrder, resetScroll: Boolean, refreshFolders: Boolean) {
        sortJob?.cancel()
        sortJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            if (refreshFolders && showFolderHeaderNow()) {
                cachedFolders = try {
                    musicFoldersLoader()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception,
                ) {
                    cachedFolders
                }
            }

            val sorted = try {
                filterAndSort(order)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                emptyList()
            }

            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    loadFailed = false,
                    sortOrder = order,
                    availableSortOrders = availableSortOrders().toImmutableList(),
                    showFolderHeader = showFolderHeaderNow(),
                    folders = cachedFolders.toImmutableList(),
                    selectedFolderId = activeServerProvider.getActiveServer().musicFolderId,
                    downloadAvailable = !ActiveServerProvider.isOffline(),
                    scrollResetToken = if (resetScroll) current.scrollResetToken + 1 else current.scrollResetToken,
                    rows = sorted.map { it.toRow() }.toImmutableList(),
                )
            }
        }
    }

    /** 1:1 port of `ArtistListModel.filterAndSortItems`. */
    private suspend fun filterAndSort(order: SortOrder): List<ArtistOrIndex> = when (order) {
        SortOrder.ALL_SONGS,
        SortOrder.BY_NAME,
        SortOrder.BY_ARTIST,
        -> allItems.sortedWith(comparator)

        SortOrder.RANDOM -> allItems.shuffled()

        SortOrder.STARRED -> starredArtists()

        SortOrder.BY_GENRE -> {
            val genre = selectedGenre
            if (genre == null) {
                allItems.sortedWith(comparator)
            } else {
                artistsFromAlbums(albumsLoader(AlbumListType.BY_GENRE, genre))
            }
        }

        SortOrder.NEWEST,
        SortOrder.RECENT,
        SortOrder.FREQUENT,
        SortOrder.HIGHEST,
        -> artistsFromAlbums(albumsLoader(order.toAlbumListType(), null))

        // The artist filter does not expose this option, because artists do not have a year.
        SortOrder.BY_YEAR -> allItems.sortedWith(comparator)
    }

    private suspend fun starredArtists(): List<ArtistOrIndex> {
        val starred = starredLoader()
        val direct = matchArtists(allItems, starred.artists)
        val fromAlbums = artistsFromAlbums(starred.albums)
        return (direct + fromAlbums).distinctBy { it.id }
    }

    private fun artistsFromAlbums(albums: List<Album>): List<ArtistOrIndex> {
        val byId = allItems.associateBy { it.id }
        val byName = allItems.associateBy { normalizeName(it.name) }
        return albums.mapNotNull { album ->
            album.artistId?.let(byId::get) ?: byName[normalizeName(album.artist)]
        }.distinctBy { it.id }
    }

    private fun matchArtists(
        items: List<ArtistOrIndex>,
        candidates: List<ArtistOrIndex>,
    ): List<ArtistOrIndex> {
        val byId = items.associateBy { it.id }
        val byName = items.associateBy { normalizeName(it.name) }
        return candidates.mapNotNull { candidate ->
            byId[candidate.id] ?: byName[normalizeName(candidate.name)]
        }.distinctBy { it.id }
    }

    private fun normalizeName(name: String?): String = name.orEmpty().trim().lowercase(Locale.ROOT)

    private fun availableSortOrders(): List<SortOrder> {
        val useId3Offline = Settings.id3TagsEnabledOffline
        val isOnline = !ActiveServerProvider.isOffline()
        return buildList {
            if (isOnline || useId3Offline) add(SortOrder.BY_NAME)
            if (isOnline) add(SortOrder.RECENT)
            if (isOnline || useId3Offline) add(SortOrder.NEWEST)
            if (isOnline) add(SortOrder.FREQUENT)
        }
    }

    private fun showFolderHeaderNow(): Boolean =
        !ActiveServerProvider.isOffline() && !ActiveServerProvider.shouldUseId3Tags()

    private fun SortOrder.toAlbumListType(): AlbumListType = when (this) {
        SortOrder.NEWEST -> AlbumListType.NEWEST
        SortOrder.RECENT -> AlbumListType.RECENT
        SortOrder.FREQUENT -> AlbumListType.FREQUENT
        SortOrder.HIGHEST -> AlbumListType.HIGHEST
        else -> error("Unsupported album-backed artist order: $this")
    }

    private fun ArtistOrIndex.toRow() = ArtistListRow(
        id = id,
        name = name.orEmpty(),
        artworkModel = artistArtRequestOrNull(name, coverArt, large = false),
        isIndex = this is Index,
    )

    private companion object {
        val comparator: Comparator<ArtistOrIndex> = compareBy(Collator.getInstance()) { it.name }
    }
}
