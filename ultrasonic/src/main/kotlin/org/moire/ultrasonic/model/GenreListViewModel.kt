/*
 * GenreListViewModel.kt
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.domain.Genre
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.genrelist.GenreListRow
import org.moire.ultrasonic.ui.genrelist.GenreListUiState

/**
 * Owns the Compose Genres List state (issue #10 phase 4G2) as one
 * [StateFlow]<[GenreListUiState]>. A projection of exactly what the legacy `SelectGenreFragment`
 * already loaded/derived: `MusicService.getGenres` (already cache-backed server-side by
 * `CachedMusicService` - a 10h `TimeLimitedCache`, cleared by an explicit refresh or a server/
 * music-folder change - so, like [PlaylistListViewModel], this class adds no ViewModel-level
 * load-once guard of its own), then a per-genre representative-cover resolve
 * (`loadGenreCover`/`getSongsByGenre`) that is **visibility-driven, not eager**: [onCoverNeeded]
 * is only ever called by [org.moire.ultrasonic.ui.genrelist.GenreListScreen] for a row that has
 * actually entered composition, exactly mirroring the legacy `GenreAdapter.onBindViewHolder`'s
 * bind-driven fetch. Navigation stays in the Fragment; this class only reads and applies genre
 * data.
 */
class GenreListViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GenreListUiState())
    val uiState: StateFlow<GenreListUiState> = _uiState.asStateFlow()

    private var rawGenres: List<Genre> = emptyList()
    private val coverTracks = mutableMapOf<String, Track?>()
    private val requestedCovers = mutableSetOf<String>()
    private val coverSemaphore = Semaphore(MAX_CONCURRENT_COVER_REQUESTS)

    private var loadJob: Job? = null

    /** Guards a slow [onCoverNeeded] response from overwriting a newer [load]'s data - the exact
     *  same generation counter the legacy `SelectGenreFragment.coverGeneration` used. */
    private var coverGeneration = 0

    /** `MusicService.getGenres`, unchanged. Test seam. */
    internal var genresLoader: suspend (refresh: Boolean) -> List<Genre> = { refresh ->
        withContext(Dispatchers.IO) { MusicServiceFactory.getMusicService().getGenres(refresh) }
    }

    /** `MusicService.getSongsByGenre(genre, COVER_CANDIDATES, 0)`, picking the first track with
     *  non-blank cover art - `SelectGenreFragment.loadGenreCover`, unchanged. Test seam. */
    internal var genreCoverLoader: suspend (genreName: String) -> Track? = { genreName ->
        withContext(Dispatchers.IO) {
            MusicServiceFactory.getMusicService()
                .getSongsByGenre(genreName, COVER_CANDIDATES, 0)
                .getTracks()
                .firstOrNull { !it.coverArt.isNullOrBlank() }
        }
    }

    /** Load (or reload) the genre list. */
    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadFailed = false) }

            if (refresh) {
                coverGeneration++
                requestedCovers.clear()
                coverTracks.clear()
            }

            val result = try {
                genresLoader(refresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }

            if (result == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = it.rows.isEmpty()) }
                return@launch
            }

            rawGenres = result
            publish()
        }
    }

    fun refresh() = load(refresh = true)

    /** `SelectGenreFragment.loadGenreCover`, ported 1:1: de-duped per genre name, gated to
     *  [MAX_CONCURRENT_COVER_REQUESTS] concurrent lookups, and guarded against a stale response
     *  landing after a refresh reset the generation. Called by the Screen once a row's cell
     *  actually enters composition - never eagerly for the whole list. */
    fun onCoverNeeded(genreName: String) {
        if (!requestedCovers.add(genreName)) return
        val generation = coverGeneration

        viewModelScope.launch {
            val track = coverSemaphore.withPermit { genreCoverLoader(genreName) }
            if (generation != coverGeneration) return@launch
            coverTracks[genreName] = track
            publish()
        }
    }

    private fun publish() {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                loadFailed = false,
                rows = rawGenres.map { it.toRow() }.toImmutableList(),
                coverGeneration = coverGeneration,
            )
        }
    }

    private fun Genre.toRow(): GenreListRow = GenreListRow(
        name = name,
        artworkModel = coverTracks[name]?.coverArtRequestOrNull(large = false),
    )

    companion object {
        private const val COVER_CANDIDATES = 10
        private const val MAX_CONCURRENT_COVER_REQUESTS = 4
    }
}
