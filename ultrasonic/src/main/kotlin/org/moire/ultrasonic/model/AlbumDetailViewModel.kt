/*
 * AlbumDetailViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.core.text.HtmlCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.Collections
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
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.R
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.album.AlbumDetailArgs
import org.moire.ultrasonic.ui.album.AlbumDetailRow
import org.moire.ultrasonic.ui.album.AlbumDetailUiState
import org.moire.ultrasonic.util.EntryByDiscAndTrackComparator
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/**
 * Owns the Compose Album Detail state (issue #10 phase 4A) as one
 * [StateFlow]<[AlbumDetailUiState]>. A projection of exactly what
 * `TrackCollectionModel.getAlbum` / `getMusicDirectory` + `getAlbumInfo` + `getAlbumStarred`
 * already load for `TrackCollectionFragment`'s `isAlbum == true` mode - the sort, disc
 * grouping and header derivation mirror the old `buildDisplayList` / `AlbumDetailHeaderBinder`
 * one for one. Playback, star submission and navigation stay in the Fragment; this class only
 * reads.
 *
 * The state survives Fragment view recreation (retained ViewModel), so returning from a track
 * / the artist restores the album with no new server call.
 */
class AlbumDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedArgs: AlbumDetailArgs? = null

    /** The unsliced, sorted track list - playback and the info sheet read it, no re-fetch. */
    private var rawTracks: List<Track> = emptyList()

    /** The album track list. id3 -> `getAlbumAsDir`; folder -> `getMusicDirectory`, tracks
     *  only. Mirrors `TrackCollectionModel.getAlbum` / `getMusicDirectory`. Test seam. */
    internal var albumLoader: suspend (AlbumDetailArgs) -> List<Track>? = { args ->
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val id = args.id ?: return@withContext emptyList()
            val dir = if (args.isId3) {
                service.getAlbumAsDir(id, args.name, args.refresh)
            } else {
                service.getMusicDirectory(id, args.name, args.refresh)
            }
            dir.getChildren().filterIsInstance<Track>()
        }
    }

    /** Album notes + server favourite state - the slower, optional pair, exactly as
     *  `TrackCollectionModel.getAlbumInfo` / `getAlbumStarred`. Test seam. */
    internal var metaLoader: suspend (albumId: String) -> AlbumMeta = { albumId ->
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            val notes = runCatching { service.getAlbumInfo(albumId)?.notes }.getOrNull()
                ?.let { HtmlCompat.fromHtml(it, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim() }
                ?.takeIf { it.isNotEmpty() }
            val starred = runCatching {
                service.getAlbum(albumId, null, false)?.starred
            }.getOrNull()
            AlbumMeta(notes = notes, starred = starred)
        }
    }

    /**
     * Load the album once. Idempotent across Fragment view recreation - the same args (barring
     * an explicit refresh) return without a new call.
     */
    fun load(args: AlbumDetailArgs) {
        val normalized = args.copy(refresh = false)
        if (loadedArgs == normalized && !args.refresh) return
        loadedArgs = normalized

        _uiState.update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                albumId = args.id,
                title = it.title.ifEmpty { args.name.orEmpty() },
                starVisible = args.isId3,
                radioAvailable = args.radioAvailable,
            )
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val tracks = try {
                albumLoader(args)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }

            if (tracks == null) {
                // A failed refresh must not destroy an album that is already on screen - only
                // the very first load surfaces the "No media found" state.
                _uiState.update {
                    it.copy(isLoading = false, loadFailed = it.rows.isEmpty())
                }
                return@launch
            }

            rawTracks = sortForDisplay(tracks)
            _uiState.update { current ->
                project(current, args).copy(
                    isLoading = false,
                    loadFailed = rawTracks.isEmpty(),
                )
            }

            val albumId = args.id
            if (albumId != null && args.isId3) {
                val meta = try {
                    metaLoader(albumId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception,
                ) {
                    null
                }
                if (meta != null) {
                    _uiState.update {
                        it.copy(
                            notes = meta.notes,
                            isStarred = meta.starred ?: it.isStarred,
                        )
                    }
                }
            }
        }
    }

    /**
     * Pull-to-refresh / an explicit `refresh` nav arg: re-fetch through the same
     * [load] path, keeping the rows on screen. A refresh while one is already running is
     * ignored so a repeated gesture cannot stack loads.
     */
    fun refresh() {
        if (_uiState.value.isLoading) return
        loadedArgs?.let { load(it.copy(refresh = true)) }
    }

    /** Optimistic heart flip for the shared `RatingManager` round-trip (issue #15). */
    fun setStarredOptimistic(starred: Boolean) {
        _uiState.update { it.copy(isStarred = starred) }
    }

    /** All album tracks in display order - the Fragment's playback commands read this. */
    fun tracksSnapshot(): List<Track> = rawTracks

    fun trackFor(id: String): Track? = rawTracks.firstOrNull { it.id == id }

    fun tracksForDisc(discNumber: Int): List<Track> =
        rawTracks.filter { (it.discNumber ?: 1) == discNumber }

    private fun sortForDisplay(tracks: List<Track>): List<Track> {
        if (!Settings.SHOULD_SORT_BY_DISC) return tracks
        val mutable = tracks.toMutableList()
        Collections.sort(mutable, EntryByDiscAndTrackComparator())
        return mutable
    }

    /** Header + rows, from the sorted [rawTracks]. Pure - no I/O (the cover-art key is a
     *  synchronous hash), so it runs on the caller's coroutine, like `SearchViewModel`. */
    private fun project(current: AlbumDetailUiState, args: AlbumDetailArgs): AlbumDetailUiState {
        val tracks = rawTracks
        val artistNames = tracks.mapNotNull { it.artist?.takeIf(String::isNotBlank) }.toSet()
        val hasMultipleArtists = artistNames.size > 1
        val singleArtist = artistNames.singleOrNull()
        val artistIds = tracks.mapNotNull { it.artistId?.takeIf(String::isNotBlank) }.toSet()
        val discNumbers = tracks.mapNotNull { it.discNumber }.toSet()
        val hasMultipleDiscs = discNumbers.size > 1

        val year = tracks.mapNotNull { it.year }.toSortedSet()
            .singleOrNull()?.takeIf { it > 0 }?.toString()
        val genre = tracks.mapNotNull { it.genre?.takeIf(String::isNotBlank) }.toSet().singleOrNull()
        val totalSeconds = tracks.sumOf { (it.duration ?: 0).toLong() }

        val rows = buildList {
            if (hasMultipleDiscs) {
                var lastDisc: Int? = null
                for (track in tracks) {
                    val disc = track.discNumber ?: 1
                    if (disc != lastDisc) {
                        add(AlbumDetailRow.Disc(disc))
                        lastDisc = disc
                    }
                    add(track.toRow(hasMultipleArtists))
                }
            } else {
                tracks.forEach { add(it.toRow(hasMultipleArtists)) }
            }
        }

        return current.copy(
            title = args.name?.takeIf { it.isNotEmpty() }
                ?: tracks.firstOrNull()?.album.orEmpty(),
            artist = singleArtist
                ?: getApplication<Application>().getString(R.string.common_various_artists),
            artistId = artistIds.singleOrNull()?.takeIf { singleArtist != null },
            year = year,
            genre = genre,
            songCount = tracks.size,
            totalDuration = if (totalSeconds > 0) Util.formatTotalDuration(totalSeconds) else null,
            artworkModel = tracks.firstOrNull()?.coverArtRequestOrNull(large = true),
            hasMultipleDiscs = hasMultipleDiscs,
            rows = rows.toImmutableList(),
        )
    }

    private fun Track.toRow(showArtist: Boolean): AlbumDetailRow.Track {
        val trackNo = track
        val number = if (Settings.SHOULD_SHOW_TRACK_NUMBER && trackNo != null && trackNo > 0) {
            String.format(Locale.ROOT, "%02d.", trackNo)
        } else {
            null
        }
        return AlbumDetailRow.Track(
            id = id,
            number = number,
            title = title.orEmpty().ifEmpty { name.orEmpty() },
            artist = artist?.takeIf { showArtist && it.isNotBlank() },
            duration = duration?.takeIf { it > 0 }?.let { Util.formatTotalDuration(it.toLong()) },
            isVideo = isVideo,
        )
    }

    data class AlbumMeta(val notes: String?, val starred: Boolean?)
}
