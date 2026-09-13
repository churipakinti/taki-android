/*
 * TrackListViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.tracklist.TrackListRow
import org.moire.ultrasonic.ui.tracklist.TrackListUiState
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util
import org.moire.ultrasonic.view.SortOrder

/**
 * Owns the Compose Track List state (issue #10 phase 4F1) as one
 * [StateFlow]<[TrackListUiState]>. Backs both the "Songs" destination (`libraryRoot`) and the
 * dedicated Liked Songs destination (`getStarred`), a 1:1 projection of the relevant
 * `TrackCollectionModel` methods and `TrackCollectionFragment` dispatch:
 *
 * - `ALL_SONGS`/`BY_ARTIST`/`BY_GENRE` are paged (`TrackCollectionModel.getAllSongs`/
 *   `getSongsForArtist`/`getSongsForGenre`'s exact offset/loading/`canLoadMore` bookkeeping,
 *   ported verbatim per mode); `RANDOM` pages with no exhaustion cutoff (`getRandomSongs`
 *   has none either); `STARRED` never pages (`getStarred()`/`getStarred2()` are a single call).
 * - **No load-once-across-back-navigation guard anywhere** - unlike `AlbumListViewModel`/
 *   `ArtistListViewModel`, the legacy `TrackCollectionFragment.getLiveData()` has no
 *   "already loaded, skip" check for any of these branches, so returning to either screen always
 *   re-fetches. [isLoading] is therefore set for *every* [load] call, append included - the
 *   legacy `swipeRefresh.isRefreshing = true/false` wraps the whole dispatch unconditionally,
 *   so an infinite-scroll append also briefly shows the pull-to-refresh spinner. Both are
 *   preserved exactly, not "fixed" (see the phase 4F1 report).
 * - `BY_ARTIST`/`BY_GENRE` always re-prompt their picker dialog on tap (Fragment-hosted, see
 *   [beginArtistSort]/[beginGenreSort]/[loadArtists]/[loadGenres]/[selectArtist]/[selectGenre]),
 *   exactly like the same quirk `AlbumListViewModel`'s `BY_GENRE` already has.
 * - The heart column ([TrackListUiState.showHeart]) is a fixed per-instance flag from
 *   `navArgs.getStarred`, never derived from the active sort - selecting "Liked" from the
 *   "Songs" screen's own sort menu does not turn hearts on there either, matching
 *   `LibraryTrackBinder(showHeart = navArgs.getStarred)` exactly.
 *
 * Navigation and playback stay in the Fragment; this class only reads.
 */
class TrackListViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(TrackListUiState())
    val uiState: StateFlow<TrackListUiState> = _uiState.asStateFlow()

    private var allTracks: List<Track> = emptyList()
    private var mode: TrackListMode = TrackListMode.ALL_SONGS
    private var libraryRoot: Boolean = false
    private var initialized: Boolean = false

    private var loadJob: Job? = null

    private var allSongsNextOffset = 0
    private var allSongsLoading = false
    private var canLoadMoreAllSongs = true

    private var artistSongsNextOffset = 0
    private var artistSongsLoading = false
    private var canLoadMoreArtistSongs = true

    private var genreSongsNextOffset = 0
    private var genreSongsLoading = false
    private var canLoadMoreGenreSongs = true

    private var selectedArtistId: String? = null
    private var selectedArtistName: String? = null
    private var selectedGenreName: String? = null

    /** id3 -> `getStarred2`; folder -> `getStarred`. Test seam. */
    internal var starredLoader: suspend () -> List<Track> = {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val result = if (ActiveServerProvider.shouldUseId3Tags()) {
                service.getStarred2()
            } else {
                service.getStarred()
            }
            Util.getSongsFromSearchResult(result).getChildren().filterIsInstance<Track>()
        }
    }

    /** `TrackCollectionModel.getAllSongs`'s `search(query = "")` call. Test seam. */
    internal var allSongsLoader: suspend (count: Int, offset: Int, folderId: String?) -> List<Track> =
        { count, offset, folderId ->
            withContext(Dispatchers.IO) {
                val criteria = SearchCriteria(
                    query = "",
                    artistCount = 0,
                    albumCount = 0,
                    songCount = count,
                    songOffset = offset,
                    musicFolderId = folderId,
                )
                MusicServiceFactory.getMusicService().search(criteria)?.songs.orEmpty()
            }
        }

    /** `service.getRandomSongs`. Test seam. */
    internal var randomLoader: suspend (count: Int) -> List<Track> = { count ->
        withContext(Dispatchers.IO) {
            MusicServiceFactory.getMusicService().getRandomSongs(count).getChildren()
                .filterIsInstance<Track>()
        }
    }

    /** `TrackCollectionModel.getSongsForArtist`'s search + exact-match filter, ported
     *  verbatim. Test seam. */
    internal var artistSongsLoader:
        suspend (artistId: String, artistName: String, count: Int, offset: Int, folderId: String?) -> List<Track> =
        { artistId, artistName, count, offset, folderId ->
            withContext(Dispatchers.IO) {
                val criteria = SearchCriteria(
                    query = artistName,
                    artistCount = 0,
                    albumCount = 0,
                    songCount = count,
                    songOffset = offset,
                    musicFolderId = folderId,
                    artistId = artistId,
                )
                val searchSongs = MusicServiceFactory.getMusicService().search(criteria)?.songs.orEmpty()
                searchSongs.filter { track ->
                    track.artistId == artistId || track.artist.equals(artistName, ignoreCase = true)
                }
            }
        }

    /** `service.getSongsByGenre`. Test seam. */
    internal var genreSongsLoader: suspend (genre: String, count: Int, offset: Int) -> List<Track> =
        { genre, count, offset ->
            withContext(Dispatchers.IO) {
                MusicServiceFactory.getMusicService().getSongsByGenre(genre, count, offset)
                    .getChildren().filterIsInstance<Track>()
            }
        }

    /** `TrackCollectionModel.getArtists`. Test seam. */
    internal var artistsLoader: suspend () -> List<ArtistOrIndex> = {
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            if (ActiveServerProvider.shouldUseId3Tags()) {
                service.getArtists(false)
            } else {
                service.getIndexes(activeServerProvider.getActiveServer().musicFolderId, false).orEmpty()
            }
        }
    }

    /** `TrackCollectionModel.getGenres`. Test seam. */
    internal var genresLoader: suspend () -> List<Genre> = {
        withContext(Dispatchers.IO) { MusicServiceFactory.getMusicService().getGenres(true) }
    }

    /**
     * Sets up the fixed, nav-arg-derived parameters for this screen instance - mirrors what
     * `TrackCollectionFragment`'s `navArgs` fix for the lifetime of the Fragment. A no-op after
     * the first call, matching [org.moire.ultrasonic.model.AlbumListViewModel.initialize]'s
     * rationale (the Fragment calls this unconditionally from `onViewCreated`).
     */
    fun initialize(libraryRoot: Boolean, getStarred: Boolean) {
        if (initialized) return
        initialized = true
        this.libraryRoot = libraryRoot
        mode = if (getStarred) TrackListMode.STARRED else TrackListMode.ALL_SONGS
        _uiState.update {
            it.copy(
                sortOrder = mode.toSortOrder(),
                availableSortOrders = availableSortOrders().toImmutableList(),
                showControls = libraryRoot,
                showHeart = getStarred,
            )
        }
    }

    /**
     * Always re-fetches - see the class kdoc for why no load-once guard exists here (so there is
     * no separate `refresh` parameter either - every call already behaves like one). [append]
     * only ever applies to `ALL_SONGS`/`BY_ARTIST`/`BY_GENRE`/`RANDOM`; `STARRED` ignores it
     * (`getStarred()` never took one either).
     */
    fun load(append: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { dispatchLoad(append) }
    }

    fun refresh() = load()

    /** The Compose equivalent of `TrackCollectionFragment.loadMoreTracks`'s condition. */
    fun loadMore() {
        val canAppend = when (mode) {
            TrackListMode.RANDOM -> true
            TrackListMode.BY_GENRE -> canLoadMoreGenreSongs
            TrackListMode.BY_ARTIST -> canLoadMoreArtistSongs
            TrackListMode.ALL_SONGS -> canLoadMoreAllSongs
            TrackListMode.STARRED -> false
        }
        if (!canAppend) return
        if (loadJob?.isActive == true) return
        load(append = true)
    }

    /** `SortOrder.BY_ARTIST`, tapped: mirrors `TrackCollectionFragment.setOrderType` reflecting
     *  the new selection in the chip immediately, before any artist is chosen. */
    fun beginArtistSort() {
        _uiState.update { it.copy(sortOrder = SortOrder.BY_ARTIST) }
    }

    /** `SortOrder.BY_GENRE`, tapped - see [beginArtistSort]. */
    fun beginGenreSort() {
        _uiState.update { it.copy(sortOrder = SortOrder.BY_GENRE) }
    }

    suspend fun loadArtists(): List<ArtistOrIndex> =
        artistsLoader().sortedBy { it.name.orEmpty().lowercase(Locale.ROOT) }

    suspend fun loadGenres(): List<Genre> = genresLoader()

    fun selectArtist(id: String, name: String) {
        selectedArtistId = id
        selectedArtistName = name
        selectedGenreName = null
        mode = TrackListMode.BY_ARTIST
        _uiState.update { it.copy(sortOrder = SortOrder.BY_ARTIST) }
        load()
    }

    fun selectGenre(name: String) {
        selectedGenreName = name
        selectedArtistId = null
        selectedArtistName = null
        mode = TrackListMode.BY_GENRE
        _uiState.update { it.copy(sortOrder = SortOrder.BY_GENRE) }
        load()
    }

    /**
     * `SortOrder`, selected from the "Songs" screen's own sort menu. `BY_ARTIST`/`BY_GENRE`
     * always route through [beginArtistSort]/[beginGenreSort] (the Fragment intercepts them
     * before calling this).
     */
    fun setSortOrder(order: SortOrder) {
        when (order) {
            SortOrder.BY_ARTIST -> { beginArtistSort(); return }
            SortOrder.BY_GENRE -> { beginGenreSort(); return }
            else -> Unit
        }
        val newMode = order.toMode() ?: return
        if (newMode == mode) return
        selectedArtistId = null
        selectedArtistName = null
        selectedGenreName = null
        mode = newMode
        _uiState.update { it.copy(sortOrder = order) }
        load()
    }

    /** `TrackCollectionFragment.toggleLibraryHeart`: flips the track's own starred flag, and -
     *  only in `STARRED` mode - removes the now-unliked row immediately (optimistic), exactly
     *  like the legacy Fragment mutating `listModel.currentList` in place. The Fragment
     *  publishes the actual RxBus rating update itself, same division of responsibility as
     *  `AlbumListViewModel`/`AlbumDetailViewModel`'s star toggles. */
    fun toggleHeartOptimistic(trackId: String): Boolean? {
        val track = allTracks.firstOrNull { it.id == trackId } ?: return null
        val newStarred = !track.starred
        track.starred = newStarred
        if (mode == TrackListMode.STARRED && !newStarred) {
            allTracks = allTracks.filterNot { it.id == trackId }
        }
        publish()
        return newStarred
    }

    fun itemFor(id: String): Track? = allTracks.firstOrNull { it.id == id }

    /** All rows currently loaded (paged appends included) - the Fragment's "play from here"/
     *  "play all" queue, exactly like `TrackCollectionFragment.getAllTracks()`. */
    fun tracksSnapshot(): List<Track> = allTracks

    private suspend fun dispatchLoad(append: Boolean) {
        _uiState.update { it.copy(isLoading = true, loadFailed = false) }
        val result = try {
            when (mode) {
                TrackListMode.STARRED -> LoadResult(starredLoader(), append = false)
                TrackListMode.ALL_SONGS -> loadAllSongs(append)
                TrackListMode.RANDOM -> LoadResult(randomLoader(Settings.MAX_SONGS), append)
                TrackListMode.BY_ARTIST -> loadArtistSongs(append)
                TrackListMode.BY_GENRE -> loadGenreSongs(append)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
            null
        }

        if (result == null) {
            _uiState.update { it.copy(isLoading = false, loadFailed = allTracks.isEmpty()) }
            return
        }

        allTracks = if (result.append) {
            (allTracks + result.tracks).distinctBy { it.id }
        } else {
            result.tracks
        }
        publish()
    }

    private suspend fun loadAllSongs(append: Boolean): LoadResult {
        if (allSongsLoading || (append && !canLoadMoreAllSongs)) return LoadResult(emptyList(), append)
        allSongsLoading = true
        try {
            if (!append) {
                allSongsNextOffset = 0
                canLoadMoreAllSongs = true
            }
            val count = Settings.MAX_SONGS
            val folderId = activeServerProvider.getActiveServer().musicFolderId
            val songs = allSongsLoader(count, allSongsNextOffset, folderId)
            allSongsNextOffset += songs.size
            canLoadMoreAllSongs = songs.size == count
            return LoadResult(songs, append)
        } finally {
            allSongsLoading = false
        }
    }

    private suspend fun loadArtistSongs(append: Boolean): LoadResult {
        val artistId = selectedArtistId
        val artistName = selectedArtistName
        if (artistId == null || artistName == null) return LoadResult(emptyList(), append)
        if (artistSongsLoading || (append && !canLoadMoreArtistSongs)) return LoadResult(emptyList(), append)
        artistSongsLoading = true
        try {
            if (!append) {
                artistSongsNextOffset = 0
                canLoadMoreArtistSongs = true
            }
            val count = Settings.MAX_SONGS
            val folderId = activeServerProvider.getActiveServer().musicFolderId
            val songs = artistSongsLoader(artistId, artistName, count, artistSongsNextOffset, folderId)
            artistSongsNextOffset += songs.size
            canLoadMoreArtistSongs = songs.size == count
            return LoadResult(songs, append)
        } finally {
            artistSongsLoading = false
        }
    }

    private suspend fun loadGenreSongs(append: Boolean): LoadResult {
        val genre = selectedGenreName ?: return LoadResult(emptyList(), append)
        if (genreSongsLoading || (append && !canLoadMoreGenreSongs)) return LoadResult(emptyList(), append)
        genreSongsLoading = true
        try {
            if (!append) {
                genreSongsNextOffset = 0
                canLoadMoreGenreSongs = true
            }
            val count = Settings.MAX_SONGS
            val songs = genreSongsLoader(genre, count, genreSongsNextOffset)
            genreSongsNextOffset += songs.size
            canLoadMoreGenreSongs = songs.size == count
            return LoadResult(songs, append)
        } finally {
            genreSongsLoading = false
        }
    }

    private fun publish() {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                loadFailed = false,
                availableSortOrders = availableSortOrders().toImmutableList(),
                rows = allTracks.map { it.toRow() }.toImmutableList(),
            )
        }
    }

    /** `TrackCollectionFragment.getListOfSortOrders()`. */
    private fun availableSortOrders(): List<SortOrder> {
        if (!libraryRoot) return emptyList()
        val supported = mutableListOf(
            SortOrder.ALL_SONGS,
            SortOrder.RANDOM,
            SortOrder.BY_ARTIST,
            SortOrder.BY_GENRE,
        )
        if (!ActiveServerProvider.isOffline()) supported.add(SortOrder.STARRED)
        return supported
    }

    private fun Track.toRow() = TrackListRow(
        id = id,
        title = title ?: name.orEmpty(),
        subtitle = listOfNotNull(
            artist?.takeIf { it.isNotBlank() },
            album?.takeIf { it.isNotBlank() },
        ).joinToString(SUBTITLE_SEPARATOR),
        artworkModel = coverArtRequestOrNull(large = false),
        liked = starred,
    )

    private fun TrackListMode.toSortOrder(): SortOrder = when (this) {
        TrackListMode.ALL_SONGS -> SortOrder.ALL_SONGS
        TrackListMode.RANDOM -> SortOrder.RANDOM
        TrackListMode.STARRED -> SortOrder.STARRED
        TrackListMode.BY_ARTIST -> SortOrder.BY_ARTIST
        TrackListMode.BY_GENRE -> SortOrder.BY_GENRE
    }

    private fun SortOrder.toMode(): TrackListMode? = when (this) {
        SortOrder.ALL_SONGS -> TrackListMode.ALL_SONGS
        SortOrder.RANDOM -> TrackListMode.RANDOM
        SortOrder.STARRED -> TrackListMode.STARRED
        else -> null
    }

    private data class LoadResult(val tracks: List<Track>, val append: Boolean)

    private enum class TrackListMode { ALL_SONGS, RANDOM, STARRED, BY_ARTIST, BY_GENRE }

    private companion object {
        const val SUBTITLE_SEPARATOR = " · "
    }
}
