/*
 * PlaylistListViewModel.kt
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
import org.moire.ultrasonic.domain.Playlist
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.service.DownloadState
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.playlistlist.PlaylistListRow
import org.moire.ultrasonic.ui.playlistlist.PlaylistListUiState
import org.moire.ultrasonic.ui.playlistlist.PlaylistRowDownloadStatus
import org.moire.ultrasonic.util.CacheCleaner
import org.moire.ultrasonic.util.LayoutType

/**
 * Owns the Compose Playlists List state (issue #10 phase 4G1) as one
 * [StateFlow]<[PlaylistListUiState]>. A projection of exactly what the legacy `PlaylistsFragment`
 * already loaded/derived: `MusicService.getPlaylists` (cached - see [load]'s kdoc), then a
 * sequential per-playlist track resolve (`resolveDownloadStates`, one network call per playlist,
 * not parallelized - preserved exactly, not "optimized", since that would change real server load
 * characteristics beyond this migration's scope) that fills in the real song count/cover art/
 * download status. Navigation, the create/rename/delete dialogs and playback stay in the
 * Fragment; this class only reads and applies their results.
 *
 * **No RxBus dependency here** - unlike the legacy Fragment's own `observeDownloadStates`
 * subscription, this class stays a plain `StateFlow` surface. The Fragment owns the
 * `RxBus.trackDownloadStateObservable` subscription (matching every other Compose-migrated
 * screen's own RxBus-stays-in-the-Fragment precedent) and calls [onTrackDownloadStateChanged].
 */
class PlaylistListViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val cacheCleaner: CacheCleaner by inject()

    private val _uiState = MutableStateFlow(PlaylistListUiState())
    val uiState: StateFlow<PlaylistListUiState> = _uiState.asStateFlow()

    private var rawPlaylists: List<Playlist> = emptyList()
    private val playlistTracks = mutableMapOf<String, List<Track>>()
    private val downloadStatuses = mutableMapOf<String, PlaylistRowDownloadStatus>()

    private var loadJob: Job? = null
    private var resolveJob: Job? = null

    /** Guards a slow `resolveDownloadStates` response from overwriting a newer [load]'s data -
     *  the exact same generation counter the legacy `PlaylistsFragment.statusLoadGeneration`
     *  used. */
    private var statusLoadGeneration = 0

    /** `MusicService.getPlaylists`, unchanged - `CachedMusicService.getPlaylists` already caches
     *  (`refresh = false` returns instantly from cache when available), so this class adds no
     *  ViewModel-level load-once guard of its own; it would only duplicate that cache. Test
     *  seam. */
    internal var playlistsLoader: suspend (refresh: Boolean) -> List<Playlist> = { refresh ->
        withContext(Dispatchers.IO) { MusicServiceFactory.getMusicService().getPlaylists(refresh) }
    }

    /** `MusicService.getPlaylist`, unchanged (never cached - see `CachedMusicService.getPlaylist`).
     *  Test seam. */
    internal var playlistTracksLoader: suspend (id: String, name: String) -> List<Track> = { id, name ->
        withContext(Dispatchers.IO) {
            MusicServiceFactory.getMusicService().getPlaylist(id, name)
                .getChildren().filterIsInstance<Track>()
        }
    }

    /** Load (or reload) the playlist list. */
    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadFailed = false,
                    online = !ActiveServerProvider.isOffline(),
                )
            }
            val generation = ++statusLoadGeneration

            val result = try {
                playlistsLoader(refresh)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }

            if (result == null) {
                _uiState.update { it.copy(isLoading = false, loadFailed = it.rows.isEmpty()) }
                return@launch
            }

            rawPlaylists = result
            playlistTracks.clear()
            downloadStatuses.clear()
            result.forEach { downloadStatuses[it.id] = PlaylistRowDownloadStatus.CHECKING }
            publish()

            resolveDownloadStates(result, generation)

            if (!ActiveServerProvider.isOffline()) cacheCleaner.cleanPlaylists(result)
        }
    }

    fun refresh() = load(refresh = true)

    /** `PlaylistsFragment.resolveDownloadStates`, ported 1:1: one sequential `getPlaylist` call
     *  per playlist (a failure for one playlist resolves to an empty track list for that
     *  playlist only, exactly like the legacy per-item `try/catch`), then the real song
     *  count/cover art/download status are derived and republished. */
    private fun resolveDownloadStates(playlists: List<Playlist>, generation: Int) {
        resolveJob?.cancel()
        resolveJob = viewModelScope.launch {
            // No outer withContext(Dispatchers.IO) here - playlistTracksLoader's own default
            // implementation already dispatches to IO itself (matching every other ViewModel's
            // loader-owns-its-dispatch convention in this codebase); wrapping the whole sequential
            // map in a second, ViewModel-owned withContext would force a real dispatcher switch
            // even when a test substitutes a non-dispatching fake loader.
            val resolved = playlists.map { playlist ->
                val tracks = try {
                    playlistTracksLoader(playlist.id, playlist.name)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                    emptyList()
                }
                playlist to tracks
            }
            if (generation != statusLoadGeneration) return@launch
            resolved.forEach { (playlist, tracks) ->
                playlistTracks[playlist.id] = tracks
                downloadStatuses[playlist.id] = computeDownloadStatus(tracks)
            }
            publish()
        }
    }

    /** `PlaylistsFragment.observeDownloadStates`'s per-event recompute - the Fragment forwards
     *  each `RxBus.trackDownloadStateObservable` event here. A no-op for a track that isn't part
     *  of any resolved playlist. */
    fun onTrackDownloadStateChanged(trackId: String) {
        var changed = false
        playlistTracks.forEach { (playlistId, tracks) ->
            if (tracks.any { it.id == trackId }) {
                val updated = computeDownloadStatus(tracks)
                val current = downloadStatuses[playlistId]
                downloadStatuses[playlistId] = if (
                    current == PlaylistRowDownloadStatus.REMOVING &&
                    updated != PlaylistRowDownloadStatus.NOT_DOWNLOADED
                ) {
                    PlaylistRowDownloadStatus.REMOVING
                } else {
                    updated
                }
                changed = true
            }
        }
        if (changed) publish()
    }

    /** The optimistic `DOWNLOADING`/`REMOVING` flip the legacy `downloadPlaylist`/
     *  `confirmRemoveDownload` applied before kicking off the actual (fire-and-forget)
     *  `DownloadUtil.justDownload` call - the real status is reconciled once
     *  [onTrackDownloadStateChanged] starts firing. */
    fun setDownloadStatusOptimistic(playlistId: String, status: PlaylistRowDownloadStatus) {
        downloadStatuses[playlistId] = status
        publish()
    }

    fun tracksFor(playlistId: String): List<Track>? = playlistTracks[playlistId]

    fun playlistFor(id: String): Playlist? = rawPlaylists.firstOrNull { it.id == id }

    /** `PlaylistsFragment.deletePlaylist`'s final `playlistAdapter.remove()` step - a local
     *  removal, not a reload (the underlying `CachedMusicService.cachedPlaylists` entry is left
     *  stale until the next `refresh = true` load, exactly like the legacy screen - see this
     *  class's own kdoc and the phase 4G1 report's disclosed pre-existing quirk). */
    fun removePlaylist(id: String) {
        rawPlaylists = rawPlaylists.filterNot { it.id == id }
        playlistTracks.remove(id)
        downloadStatuses.remove(id)
        publish()
    }

    fun setLayoutType(type: LayoutType) {
        _uiState.update { it.copy(layoutType = type) }
    }

    /** `PlaylistsFragment.getPlaylistDownloadStatus`, ported 1:1. */
    private fun computeDownloadStatus(tracks: List<Track>): PlaylistRowDownloadStatus {
        if (tracks.isEmpty()) return PlaylistRowDownloadStatus.EMPTY
        val states = tracks.map(DownloadService::getDownloadState)
        val downloaded = states.count { it == DownloadState.DONE || it == DownloadState.PINNED }
        return when {
            states.any {
                it == DownloadState.QUEUED || it == DownloadState.DOWNLOADING ||
                    it == DownloadState.RETRYING
            } -> PlaylistRowDownloadStatus.DOWNLOADING

            states.any { it == DownloadState.FAILED } -> PlaylistRowDownloadStatus.FAILED

            downloaded == tracks.size -> PlaylistRowDownloadStatus.DOWNLOADED

            downloaded > 0 -> PlaylistRowDownloadStatus.PARTIAL

            else -> PlaylistRowDownloadStatus.NOT_DOWNLOADED
        }
    }

    private fun publish() {
        _uiState.update { current ->
            current.copy(
                isLoading = false,
                loadFailed = false,
                rows = rawPlaylists.map { it.toRow() }.toImmutableList(),
            )
        }
    }

    /** `PlaylistsFragment.bindTrackCount`'s exact fallback: the real resolved count once
     *  available, else the server's own (possibly stale) [Playlist.songCount] metadata. */
    private fun Playlist.toRow(): PlaylistListRow {
        val tracks = playlistTracks[id]
        val songCount = tracks?.size ?: songCount.toIntOrNull() ?: 0
        val representative = tracks.orEmpty().firstOrNull { !it.coverArt.isNullOrBlank() }
        return PlaylistListRow(
            id = id,
            name = name,
            songCount = songCount,
            artworkModel = representative?.coverArtRequestOrNull(large = false),
            downloadStatus = downloadStatuses[id] ?: PlaylistRowDownloadStatus.CHECKING,
        )
    }
}
