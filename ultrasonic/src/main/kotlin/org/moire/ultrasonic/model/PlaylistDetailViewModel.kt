/*
 * PlaylistDetailViewModel.kt
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
import org.moire.ultrasonic.R
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.playlist.PlaylistDetailArgs
import org.moire.ultrasonic.ui.playlist.PlaylistDetailRow
import org.moire.ultrasonic.ui.playlist.PlaylistDetailUiState
import org.moire.ultrasonic.util.Settings
import org.moire.ultrasonic.util.Util

/**
 * Owns the Compose Playlist Detail state (issue #10 phase 4F3) as one
 * [StateFlow]<[PlaylistDetailUiState]>. A projection of exactly what
 * `TrackCollectionModel.getPlaylist` already loaded for `TrackCollectionFragment`'s
 * `navArgs.playlistId != null` mode - the header derivation mirrors the legacy
 * `AlbumDetailHeaderBinder`/`buildDisplayList` one for one (see [PlaylistDetailUiState]'s kdoc
 * for what a playlist never had: discs, notes, a star, an info sheet, a clickable artist).
 * Playback, the header menu (download/rename/delete) and navigation stay in the Fragment; this
 * class only reads.
 *
 * **No load-once guard, unlike [AlbumDetailViewModel]**: `CachedMusicService.getPlaylist`
 * passes straight through to the network unconditionally (see [PlaylistDetailUiState]'s kdoc),
 * so [load] always re-fetches - calling it twice with the same [PlaylistDetailArgs] does two real
 * network calls, exactly matching the legacy screen's own behaviour (a fresh `getLiveData()` /
 * `getPlaylist()` call on every `onViewCreated`, config changes included).
 */
class PlaylistDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** The unsliced track list, in the playlist's own order - playback reads it, no re-fetch. */
    private var rawTracks: List<Track> = emptyList()

    /** `TrackCollectionModel.getPlaylist`'s `service.getPlaylist(id, name)` call. Test seam. */
    internal var playlistLoader: suspend (id: String, name: String) -> List<Track> = { id, name ->
        withContext(Dispatchers.IO) {
            MusicServiceFactory.getMusicService().getPlaylist(id, name)
                .getChildren().filterIsInstance<Track>()
        }
    }

    /** Load (or reload - see the class kdoc) the playlist. */
    fun load(args: PlaylistDetailArgs) {
        _uiState.update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                playlistId = args.playlistId,
                title = args.playlistName,
                radioAvailable = args.radioAvailable,
            )
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val tracks = try {
                playlistLoader(args.playlistId, args.playlistName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }

            if (tracks == null) {
                // A failed refresh must not destroy a playlist that is already on screen - only
                // the very first load surfaces the "No matches" state, exactly like Album Detail.
                _uiState.update { it.copy(isLoading = false, loadFailed = it.rows.isEmpty()) }
                return@launch
            }

            rawTracks = tracks
            _uiState.update { current ->
                project(current, args).copy(isLoading = false, loadFailed = tracks.isEmpty())
            }
        }
    }

    /** Pull-to-refresh: re-fetch through [load] - see the class kdoc, this is always a real
     *  network call, not a cache bypass. Ignored while a load is already running, same guard as
     *  [AlbumDetailViewModel.refresh]. */
    fun refresh(args: PlaylistDetailArgs) {
        if (_uiState.value.isLoading) return
        load(args)
    }

    /** All playlist tracks in order - the Fragment's playback commands read this. */
    fun tracksSnapshot(): List<Track> = rawTracks

    fun trackFor(id: String): Track? = rawTracks.firstOrNull { it.id == id }

    /**
     * "Remove from playlist" (issue #10 phase 4F3 parity with the legacy
     * `TrackCollectionFragment.removeFromPlaylist`): drops [trackId] from the in-memory
     * snapshot and republishes the rows. The caller (the Fragment) submits the actual
     * `updatePlaylist` network call via [indexOf] first and only calls this once that succeeds,
     * exactly like the legacy code removed the row only after the call returned.
     */
    fun removeTrackAt(trackId: String) {
        rawTracks = rawTracks.filterNot { it.id == trackId }
        _uiState.update {
            it.copy(
                songCount = rawTracks.size,
                rows = rawTracks.map { it.toRow() }.toImmutableList(),
            )
        }
    }

    /** The track's position in [rawTracks] - what `updatePlaylist`'s `songIndexesToRemove`
     *  expects. Null if the track is no longer in the snapshot. */
    fun indexOf(trackId: String): Int? =
        rawTracks.indexOfFirst { it.id == trackId }.takeIf { it >= 0 }

    /** Header + rows, from [rawTracks]. Pure - no I/O (the cover-art key is a synchronous
     *  hash), so it runs on the caller's coroutine, like [AlbumDetailViewModel.project]. */
    private fun project(current: PlaylistDetailUiState, args: PlaylistDetailArgs): PlaylistDetailUiState {
        val tracks = rawTracks
        val artistNames = tracks.mapNotNull { it.artist?.takeIf(String::isNotBlank) }.toSet()
        val singleArtist = artistNames.singleOrNull()
        val year = tracks.mapNotNull { it.year }.toSortedSet()
            .singleOrNull()?.takeIf { it > 0 }?.toString()
        val totalSeconds = tracks.sumOf { (it.duration ?: 0).toLong() }
        val representativeTrack = tracks.firstOrNull { !it.coverArt.isNullOrBlank() }

        return current.copy(
            title = args.playlistName,
            artist = singleArtist
                ?: getApplication<Application>().getString(R.string.common_various_artists),
            year = year,
            songCount = tracks.size,
            totalDuration = if (totalSeconds > 0) Util.formatTotalDuration(totalSeconds) else null,
            artworkModel = representativeTrack?.coverArtRequestOrNull(large = true),
            rows = tracks.map { it.toRow() }.toImmutableList(),
        )
    }

    private fun Track.toRow(): PlaylistDetailRow {
        val trackNo = track
        val number = if (Settings.SHOULD_SHOW_TRACK_NUMBER && trackNo != null && trackNo > 0) {
            String.format(Locale.ROOT, "%02d.", trackNo)
        } else {
            null
        }
        return PlaylistDetailRow(
            id = id,
            number = number,
            title = title.orEmpty().ifEmpty { name.orEmpty() },
            artist = artist?.takeIf(String::isNotBlank),
            duration = duration?.takeIf { it > 0 }?.let { Util.formatTotalDuration(it.toLong()) },
            isVideo = isVideo,
        )
    }
}
