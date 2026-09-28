/*
 * CreatePlaylistViewModel.kt
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
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.ArtistOrIndex
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.createplaylist.CreatePlaylistTrackRow
import org.moire.ultrasonic.ui.createplaylist.CreatePlaylistUiState
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.view.SortOrder

/**
 * Owns the Compose Create Playlist state (issue #10 phase 4M2) as one
 * [StateFlow]<[CreatePlaylistUiState]>. A projection of the legacy `CreatePlaylistFragment`'s own
 * loaders: `ALL_SONGS`/`BY_ARTIST`/`BY_GENRE` are paged (the exact offset/`canLoadMore`
 * bookkeeping `TrackListViewModel` already ported from `TrackCollectionModel` for the shared
 * Track List screen - adapted here rather than shared, since this screen additionally needs
 * per-track selection state and a free-text search mode neither Track List nor its ViewModel
 * have), `RANDOM` pages with no exhaustion cutoff, and a submitted search query replaces the list
 * outright with no pagination (matches the legacy `runSearch`/`SearchListModel.search` exactly -
 * search was never paged there either).
 *
 * Selection is a [LinkedHashMap] exactly like the legacy `selectedTracks` field: insertion order
 * is preserved, and re-selecting a track after deselecting it moves it back to the *end* of the
 * order - this is what the created playlist's own track order becomes, so it must match legacy
 * behavior bit for bit, not just "a set of ids".
 *
 * Navigation, toasts, and the `ItemSelectionDialogFragment` artist/genre picker stay in the
 * Fragment; this class only reads and performs the one write (`createPlaylist`).
 */
class CreatePlaylistViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(CreatePlaylistUiState())
    val uiState: StateFlow<CreatePlaylistUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private var mode: Mode = Mode.ALL_SONGS
    private var showingSearchResults = false

    private var allTracks: List<Track> = emptyList()
    private val selectedTracks = linkedMapOf<String, Track>()

    private var allSongsNextOffset = 0
    private var canLoadMoreAllSongs = true

    private var artistSongsNextOffset = 0
    private var canLoadMoreArtistSongs = true

    private var genreSongsNextOffset = 0
    private var canLoadMoreGenreSongs = true

    private var selectedArtistId: String? = null
    private var selectedArtistName: String? = null
    private var selectedGenreName: String? = null

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

    /** `TrackCollectionModel.getSongsForArtist`'s search + exact-match filter, ported verbatim
     *  (same as `TrackListViewModel.artistSongsLoader`). Test seam. */
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

    /** `SearchListModel.search`'s exact call shape (song-only: `artistCount`/`albumCount` = 0).
     *  Test seam. */
    internal var searchLoader: suspend (query: String) -> List<Track> = { query ->
        withContext(Dispatchers.IO) {
            val criteria = SearchCriteria(
                query = query,
                artistCount = 0,
                albumCount = 0,
                songCount = Settings.MAX_SONGS,
            )
            MusicServiceFactory.getMusicService().search(criteria)?.songs.orEmpty()
        }
    }

    /** `getMusicService().createPlaylist(id = null, ...)` - always creates, never updates; the
     *  legacy screen has no edit mode. Test seam. */
    internal var playlistCreator: suspend (name: String, tracks: List<Track>) -> Unit = { name, tracks ->
        withContext(Dispatchers.IO) {
            MusicServiceFactory.getMusicService().createPlaylist(null, name, tracks)
        }
    }

    private var initialized = false

    /** Idempotent across Fragment view recreation - a no-op after the first call, matching
     *  `AlbumListViewModel.initialize`'s rationale (the Fragment calls this unconditionally from
     *  `onCreateView`). */
    fun load() {
        if (initialized) return
        initialized = true
        mode = Mode.ALL_SONGS
        showingSearchResults = false
        dispatch(append = false)
    }

    fun loadMore() {
        if (showingSearchResults || loadJob?.isActive == true) return
        val canAppend = when (mode) {
            Mode.RANDOM -> true
            Mode.BY_ARTIST -> canLoadMoreArtistSongs
            Mode.BY_GENRE -> canLoadMoreGenreSongs
            Mode.ALL_SONGS -> canLoadMoreAllSongs
        }
        if (!canAppend) return
        dispatch(append = true)
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /** Blank submit reverts to the current sort mode's list, exactly like the legacy
     *  `runSearch()` falling back to `loadOrder(SortOrder.ALL_SONGS)` - here it reloads whichever
     *  mode is currently selected rather than hardcoding `ALL_SONGS`, since a submit from e.g.
     *  "By Genre" with a since-cleared field should return to that genre's list, not jump away
     *  from it.
     *
     *  A failed search is swallowed exactly like every other load in this class (see
     *  [dispatchLoad]) rather than rethrown - this runs inside [viewModelScope], a coroutine
     *  scope the Fragment's own `toastingExceptionHandler`-wrapped launch can never observe, so
     *  rethrowing here would only crash the app on an uncaught exception (found live during
     *  phase 4M2 Pixel validation: a real `SocketTimeoutException` did exactly that before this
     *  was fixed), not surface a toast. */
    fun onSearchSubmit() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) {
            showingSearchResults = false
            dispatch(append = false)
            return
        }
        showingSearchResults = true
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val songs = try {
                searchLoader(query)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            allTracks = songs
            publish()
        }
    }

    fun onClear() {
        _uiState.update { it.copy(searchQuery = "") }
        onSearchSubmit()
    }

    /** `ALL_SONGS`/`RANDOM` load immediately; `BY_ARTIST`/`BY_GENRE` only update [sortOrder] here
     *  - the Fragment opens the picker dialog and calls [selectArtist]/[selectGenre] with the
     *  actual choice, exactly like the legacy `onOrderChanged`/`showArtistSelection`. */
    fun onSortOrderSelected(order: SortOrder) {
        when (order) {
            SortOrder.BY_ARTIST, SortOrder.BY_GENRE -> _uiState.update { it.copy(sortOrder = order) }
            else -> {
                mode = Mode.ALL_SONGS.takeIf { order == SortOrder.ALL_SONGS } ?: Mode.RANDOM
                showingSearchResults = false
                _uiState.update { it.copy(sortOrder = order, searchQuery = "") }
                dispatch(append = false)
            }
        }
    }

    suspend fun loadArtists(): List<ArtistOrIndex> = artistsLoader()

    suspend fun loadGenres(): List<Genre> = genresLoader()

    fun selectArtist(id: String, name: String) {
        selectedArtistId = id
        selectedArtistName = name
        mode = Mode.BY_ARTIST
        showingSearchResults = false
        _uiState.update { it.copy(sortOrder = SortOrder.BY_ARTIST, searchQuery = "") }
        dispatch(append = false)
    }

    fun selectGenre(name: String) {
        selectedGenreName = name
        mode = Mode.BY_GENRE
        showingSearchResults = false
        _uiState.update { it.copy(sortOrder = SortOrder.BY_GENRE, searchQuery = "") }
        dispatch(append = false)
    }

    /** Toggling out of `selectedTracks` and back in moves the id to the end of the
     *  [LinkedHashMap] - the exact legacy `selectedTracks.remove(...)` / `[id] = track`
     *  reinsertion-reorders-to-end behavior, since that order becomes the created playlist's own
     *  track order. */
    fun toggleTrack(trackId: String) {
        val track = allTracks.firstOrNull { it.id == trackId } ?: return
        if (selectedTracks.remove(trackId) == null) {
            selectedTracks[trackId] = track
        }
        publish()
    }

    /** The tracks to create the playlist with, in selection order - null when nothing is
     *  selected (mirrors the legacy `savePlaylist()`'s empty-selection guard). */
    fun selectedTracksSnapshot(): List<Track>? =
        selectedTracks.values.toList().takeIf { it.isNotEmpty() }

    /** Performs the actual `createPlaylist` call. Throws on failure (the Fragment's
     *  `toastingExceptionHandler` reports it) - the selection is never cleared here, so a failed
     *  save leaves every selected track intact for a retry, exactly as required. */
    suspend fun save(playlistName: String, tracks: List<Track>) {
        _uiState.update { it.copy(isSaving = true) }
        try {
            playlistCreator(playlistName, tracks)
        } finally {
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    private fun dispatch(append: Boolean) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { dispatchLoad(append) }
    }

    private suspend fun dispatchLoad(append: Boolean) {
        _uiState.update { it.copy(isLoading = true) }
        val result = try {
            when (mode) {
                Mode.ALL_SONGS -> loadAllSongs(append)
                Mode.RANDOM -> LoadResult(randomLoader(Settings.MAX_SONGS), append)
                Mode.BY_ARTIST -> loadArtistSongs(append)
                Mode.BY_GENRE -> loadGenreSongs(append)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
            null
        }

        if (result == null) {
            _uiState.update { it.copy(isLoading = false) }
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
        if (append && !canLoadMoreAllSongs) return LoadResult(emptyList(), append)
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
    }

    private suspend fun loadArtistSongs(append: Boolean): LoadResult {
        val artistId = selectedArtistId
        val artistName = selectedArtistName
        if (artistId == null || artistName == null) return LoadResult(emptyList(), append)
        if (append && !canLoadMoreArtistSongs) return LoadResult(emptyList(), append)
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
    }

    private suspend fun loadGenreSongs(append: Boolean): LoadResult {
        val genre = selectedGenreName ?: return LoadResult(emptyList(), append)
        if (append && !canLoadMoreGenreSongs) return LoadResult(emptyList(), append)
        if (!append) {
            genreSongsNextOffset = 0
            canLoadMoreGenreSongs = true
        }
        val count = Settings.MAX_SONGS
        val songs = genreSongsLoader(genre, count, genreSongsNextOffset)
        genreSongsNextOffset += songs.size
        canLoadMoreGenreSongs = songs.size == count
        return LoadResult(songs, append)
    }

    private fun publish() {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                selectedCount = selectedTracks.size,
                rows = allTracks.map { it.toRow() }.toImmutableList(),
            )
        }
    }

    private fun Track.toRow() = CreatePlaylistTrackRow(
        id = id,
        title = title ?: name.orEmpty(),
        subtitle = listOfNotNull(
            artist?.takeIf { it.isNotBlank() },
            album?.takeIf { it.isNotBlank() },
        ).joinToString(SUBTITLE_SEPARATOR),
        artworkModel = coverArtRequestOrNull(large = false),
        selected = selectedTracks.containsKey(id),
    )

    private data class LoadResult(val tracks: List<Track>, val append: Boolean)

    private enum class Mode { ALL_SONGS, RANDOM, BY_ARTIST, BY_GENRE }

    private companion object {
        const val SUBTITLE_SEPARATOR = " · "
    }
}
