/*
 * FolderBrowserViewModel.kt
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
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicDirectory
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.folderbrowser.FolderBrowserArgs
import org.moire.ultrasonic.ui.folderbrowser.FolderBrowserRow
import org.moire.ultrasonic.ui.folderbrowser.FolderBrowserUiState

private const val SUBTITLE_SEPARATOR = " · "

/**
 * Owns the Compose folder/non-ID3 browser state (issue #10 phase 4M1) as one
 * [StateFlow]<[FolderBrowserUiState]>. A projection of exactly what the legacy
 * `TrackCollectionModel.getMusicDirectory` (called from `TrackCollectionFragment.getLiveData`'s
 * `else` fallthrough) already loaded, mixed-rendered by `AlbumRowDelegate` (sub-directories) and
 * `TrackViewBinder` (bare tracks) - both children types come back from the same server call, in
 * server order, exactly as this screen preserves it. Playback, star submission and navigation
 * stay in the Fragment; this class only reads.
 *
 * The state survives Fragment view recreation (retained ViewModel), matching every other Compose
 * `TrackCollectionFragment` mode.
 */
class FolderBrowserViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val _uiState = MutableStateFlow(FolderBrowserUiState())
    val uiState: StateFlow<FolderBrowserUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedArgs: FolderBrowserArgs? = null

    /** The unsliced track children, in server order - playback reads it, no re-fetch. */
    private var rawTracks: List<Track> = emptyList()

    /** The directory's live server call. Mirrors `TrackCollectionModel.getMusicDirectory`
     *  exactly (same `id`/`name`/`refresh` args, same service seam). Test seam. */
    internal var directoryLoader: suspend (FolderBrowserArgs) -> List<MusicDirectory.Child>? = { args ->
        withContext(Dispatchers.IO) {
            val service = MusicServiceFactory.getMusicService()
            service.getMusicDirectory(args.id, args.name, args.refresh).getChildren()
        }
    }

    /** Load the directory once. Idempotent across Fragment view recreation (barring an explicit
     *  refresh), exactly like [AlbumDetailViewModel.load]/[TrackListViewModel]. */
    fun load(args: FolderBrowserArgs) {
        val normalized = args.copy(refresh = false)
        if (loadedArgs == normalized && !args.refresh) return
        loadedArgs = normalized

        _uiState.update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                title = args.name.orEmpty(),
                online = args.online,
            )
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val children = try {
                directoryLoader(args)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception,
            ) {
                null
            }

            if (children == null) {
                // A failed refresh must not destroy a directory already on screen - only the
                // very first load surfaces the "No media found" state.
                _uiState.update { it.copy(isLoading = false, loadFailed = it.rows.isEmpty()) }
                return@launch
            }

            rawTracks = children.filterIsInstance<Track>()
            _uiState.update { current ->
                current.copy(
                    isLoading = false,
                    loadFailed = children.isEmpty(),
                    rows = children.toRows().toImmutableList(),
                )
            }
        }
    }

    fun refresh() {
        val args = loadedArgs ?: return
        load(args.copy(refresh = true))
    }

    /** All tracks in the directory's own order - the same list `playFromHere`'s `startIndex`
     *  lookup needs, exactly like the legacy `getAllTracks()` (which only ever counted `Track`
     *  entries, never the mixed sub-directories). */
    fun tracksSnapshot(): List<Track> = rawTracks

    fun itemFor(trackId: String): Track? = rawTracks.firstOrNull { it.id == trackId }

    /** Flips [Track.starred] in place and returns the new value, or null if the track isn't
     *  currently loaded - the same optimistic-then-submit contract
     *  [TrackListViewModel.toggleHeartOptimistic]/the legacy `toggleLibraryHeart` use. The
     *  caller (the Fragment) still owns the actual `RxBus.ratingSubmitter` dispatch. */
    fun toggleHeartOptimistic(trackId: String): Boolean? {
        val track = rawTracks.firstOrNull { it.id == trackId } ?: return null
        track.starred = !track.starred
        _uiState.update { current ->
            current.copy(
                rows = current.rows.map { row ->
                    if (row is FolderBrowserRow.Track && row.id == trackId) {
                        row.copy(liked = track.starred)
                    } else {
                        row
                    }
                }.toImmutableList(),
            )
        }
        return track.starred
    }

    private fun List<MusicDirectory.Child>.toRows(): List<FolderBrowserRow> =
        mapNotNull { child ->
            if (child is Track) {
                child.toRow()
            } else {
                (child as? Album)?.toDirectoryRow()
            }
        }

    private fun Album.toDirectoryRow(): FolderBrowserRow.Directory = FolderBrowserRow.Directory(
        id = id,
        title = title.orEmpty().ifEmpty { name.orEmpty() },
        artist = artist?.takeIf { it.isNotBlank() },
        artworkModel = coverArtRequestOrNull(large = false),
        parent = parent,
    )

    private fun Track.toRow(): FolderBrowserRow.Track = FolderBrowserRow.Track(
        id = id,
        title = title ?: name.orEmpty(),
        subtitle = listOfNotNull(
            artist?.takeIf { it.isNotBlank() },
            album?.takeIf { it.isNotBlank() },
        ).joinToString(SUBTITLE_SEPARATOR),
        artworkModel = coverArtRequestOrNull(large = false),
        liked = starred,
    )
}
