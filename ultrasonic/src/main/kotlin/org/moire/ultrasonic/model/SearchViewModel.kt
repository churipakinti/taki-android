/*
 * SearchViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import android.provider.SearchRecentSuggestions
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Index
import org.moire.ultrasonic.domain.SearchCriteria
import org.moire.ultrasonic.domain.SearchResult
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.provider.SearchSuggestionProvider
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.search.SearchAlbumUi
import org.moire.ultrasonic.ui.search.SearchArtistUi
import org.moire.ultrasonic.ui.search.SearchSongUi
import org.moire.ultrasonic.ui.search.SearchUiState
import org.moire.ultrasonic.util.RecentSearches
import org.moire.ultrasonic.util.Settings

private const val LIVE_SEARCH_DEBOUNCE_MS = 300L
private const val LIVE_SEARCH_MIN_QUERY_LENGTH = 2

/**
 * Owns the Search screen's query and result state for the Compose [SearchUiState]. A faithful
 * port of `SearchFragment`'s behaviour:
 *
 *  - live search after a 300ms debounce, minimum 2 characters, and never re-running the exact
 *    same query the debounce last fired;
 *  - each request cancels the previous one, and a [LatestRequestTracker] guards against a slow
 *    older response landing after a newer one (a cancelled job can't stop an in-flight call);
 *  - the network request uses `Settings.MAX_*`; the shown slice is `Settings.DEFAULT_*`, with
 *    "Show more" expanding a single group to its full length;
 *  - video songs are dropped (music-only surface);
 *  - a search in flight never blanks results already on screen;
 *  - recent searches read/write `RecentSearches` and mirror to `SearchRecentSuggestions`,
 *    exactly as the old screen did.
 *
 * The state survives Fragment view recreation (retained ViewModel), so returning from a result
 * restores the query and results without a new server call.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val recentSearches = RecentSearches(application)
    private val requestTracker = LatestRequestTracker()

    private var liveSearchJob: Job? = null
    private var searchJob: Job? = null
    private var lastLiveSearchQuery: String? = null

    /** Untrimmed last result; "Show more" and re-projection read from it, no re-fetch. */
    private var fullResult: SearchResult? = null
    private var expandArtists = false
    private var expandAlbums = false
    private var expandSongs = false

    /** Test seam - the real server call otherwise. */
    internal var searchService: suspend (SearchCriteria) -> SearchResult? = { criteria ->
        withContext(Dispatchers.IO) { MusicServiceFactory.getMusicService().search(criteria) }
    }

    init {
        refreshRecentSearches()
    }

    fun onQueryChange(text: String) {
        _uiState.update { it.copy(query = text) }
        scheduleLiveSearch(text)
    }

    /** Keyboard "Search" action / explicit submit - immediate, no debounce or min length. */
    fun onSubmit() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        saveRecentQuery(query)
        liveSearchJob?.cancel()
        lastLiveSearchQuery = query
        runSearch(query)
    }

    fun onRecentSearchTap(query: String) {
        saveRecentQuery(query)
        _uiState.update { it.copy(query = query) }
        liveSearchJob?.cancel()
        lastLiveSearchQuery = query
        runSearch(query)
    }

    fun onClearQuery() {
        liveSearchJob?.cancel()
        searchJob?.cancel()
        lastLiveSearchQuery = null
        clearResults()
        _uiState.update { it.copy(query = "", isSearching = false, submitted = false) }
        refreshRecentSearches()
    }

    fun onRemoveRecentSearch(query: String) {
        recentSearches.remove(query)
        refreshRecentSearches()
    }

    fun onClearAllRecentSearches() {
        recentSearches.clear()
        withSuggestions { it.clearHistory() }
        refreshRecentSearches()
    }

    /** From an ACTION_SEARCH / voice intent - the Activity already saved the recent query. */
    fun setInitialQuery(query: String) {
        val trimmed = query.trim()
        _uiState.update { it.copy(query = trimmed) }
        if (trimmed.isEmpty()) return
        lastLiveSearchQuery = trimmed
        runSearch(trimmed)
    }

    fun onShowMoreArtists() { expandArtists = true; reproject() }
    fun onShowMoreAlbums() { expandAlbums = true; reproject() }
    fun onShowMoreSongs() { expandSongs = true; reproject() }

    /** A result row was opened - persist the query that produced it (matches the old screen). */
    fun onResultOpened() {
        val query = _uiState.value.query.trim()
        if (query.isNotEmpty()) saveRecentQuery(query)
    }

    /** The full [Track] behind a shown song row, for the host's play command. */
    fun trackFor(id: String): Track? = fullResult?.songs?.firstOrNull { it.id == id }

    /**
     * What an autoplay intent should do once results are in: the first non-video song, else
     * the first album. Null when nothing is playable.
     */
    fun autoplayTarget(): AutoplayTarget? {
        val result = fullResult ?: return null
        result.songs.firstOrNull { !it.isVideo }?.let { return AutoplayTarget.PlaySong(it) }
        result.albums.firstOrNull()?.let { return AutoplayTarget.OpenAlbum(it.id, it.title) }
        return null
    }

    sealed interface AutoplayTarget {
        data class PlaySong(val track: Track) : AutoplayTarget
        data class OpenAlbum(val id: String, val name: String?) : AutoplayTarget
    }

    private fun scheduleLiveSearch(text: String) {
        val query = text.trim()
        liveSearchJob?.cancel()
        if (query.isEmpty()) {
            searchJob?.cancel()
            lastLiveSearchQuery = null
            clearResults()
            _uiState.update { it.copy(isSearching = false, submitted = false) }
            refreshRecentSearches()
            return
        }
        if (query.length < LIVE_SEARCH_MIN_QUERY_LENGTH || query == lastLiveSearchQuery) return
        liveSearchJob = viewModelScope.launch {
            delay(LIVE_SEARCH_DEBOUNCE_MS)
            lastLiveSearchQuery = query
            runSearch(query)
        }
    }

    private fun runSearch(query: String) {
        searchJob?.cancel()
        expandArtists = false
        expandAlbums = false
        expandSongs = false
        _uiState.update { it.copy(isSearching = true) }
        searchJob = viewModelScope.launch {
            val requestId = requestTracker.begin()
            val result = try {
                searchService(
                    SearchCriteria(
                        query = query,
                        artistCount = Settings.MAX_ARTISTS,
                        albumCount = Settings.MAX_ALBUMS,
                        songCount = Settings.MAX_SONGS,
                    ),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }
            if (!requestTracker.isCurrent(requestId)) return@launch
            fullResult = result
            _uiState.update { it.copy(isSearching = false, submitted = true) }
            reproject()
        }
    }

    // Trims the last result to the shown slice (DEFAULT_*, or the full length for a group the
    // user expanded) and maps to UI models. Only ever a few dozen items and no I/O - the
    // cover-art key is a synchronous hash - so it runs on the caller's thread (the search
    // coroutine, or a "Show more" tap).
    private fun reproject() {
        val result = fullResult
        if (result == null) {
            clearResults()
            return
        }
        val songs = result.songs.filterNot { it.isVideo }
        val shownArtists =
            if (expandArtists) result.artists else result.artists.take(Settings.DEFAULT_ARTISTS)
        val shownAlbums =
            if (expandAlbums) result.albums else result.albums.take(Settings.DEFAULT_ALBUMS)
        val shownSongs = if (expandSongs) songs else songs.take(Settings.DEFAULT_SONGS)
        _uiState.update {
            it.copy(
                artists = shownArtists.map { artist ->
                    SearchArtistUi(artist.id, artist.name.orEmpty(), isIndex = artist is Index)
                }.toImmutableList(),
                albums = shownAlbums.map { album -> album.toUi() }.toImmutableList(),
                songs = shownSongs.map { song -> song.toUi() }.toImmutableList(),
                artistsHaveMore = result.artists.size > shownArtists.size,
                albumsHaveMore = result.albums.size > shownAlbums.size,
                songsHaveMore = songs.size > shownSongs.size,
            )
        }
    }

    private fun Album.toUi() = SearchAlbumUi(
        id = id,
        title = title.orEmpty(),
        subtitle = artist.orEmpty(),
        artworkModel = coverArtRequestOrNull(large = false),
    )

    private fun Track.toUi() = SearchSongUi(
        id = id,
        title = title.orEmpty(),
        subtitle = artist.orEmpty(),
        artworkModel = coverArtRequestOrNull(large = false),
    )

    private fun clearResults() {
        fullResult = null
        expandArtists = false
        expandAlbums = false
        expandSongs = false
        _uiState.update {
            it.copy(
                artists = persistentListOf(),
                albums = persistentListOf(),
                songs = persistentListOf(),
                artistsHaveMore = false,
                albumsHaveMore = false,
                songsHaveMore = false,
            )
        }
    }

    private fun refreshRecentSearches() {
        _uiState.update { it.copy(recentSearches = recentSearches.get().toImmutableList()) }
    }

    private fun saveRecentQuery(query: String) {
        val normalized = query.trim()
        if (normalized.isEmpty()) return
        recentSearches.save(normalized)
        withSuggestions { it.saveRecentQuery(normalized, null) }
        refreshRecentSearches()
    }

    // The Android search-suggestions provider is a best-effort mirror of RecentSearches (it
    // feeds the system voice/quick-search box). A failure there - e.g. the provider not being
    // available - must not break the in-app recent-search list or crash.
    private inline fun withSuggestions(block: (SearchRecentSuggestions) -> Unit) {
        runCatching {
            block(
                SearchRecentSuggestions(
                    getApplication(),
                    SearchSuggestionProvider.AUTHORITY,
                    SearchSuggestionProvider.MODE,
                ),
            )
        }
    }
}
