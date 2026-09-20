/*
 * DownloadsViewModel.kt
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.DownloadService
import org.moire.ultrasonic.ui.downloads.DownloadedAlbumRow
import org.moire.ultrasonic.ui.downloads.DownloadsUiState

/**
 * Owns the Compose Downloads state (issue #10 phase 4G3) as one [StateFlow]<[DownloadsUiState]>.
 * A 1:1 projection of what the legacy `DownloadsFragment` + `TrackCollectionModel
 * .getDownloadedAlbums` did: read the downloaded albums from the local offline metadata database
 * (the same path online and offline - no network involved), overwrite each album's song count
 * with the real local track count, and sort by lower-cased title. Removing an album is the same
 * sequence as before: look up its local tracks, `DownloadService.deleteAsync`, re-query.
 *
 * There is deliberately **no download-state/RxBus subscription**: the legacy screen had none (it
 * lists finished local albums only - no queue, progress, cancel or retry), so none is invented.
 * Loading is not guarded by a load-once flag either: the legacy Fragment re-queried on every view
 * creation (so returning from a downloaded album showed fresh data) and the Fragment calls
 * [load] from `onViewCreated` for exactly that reason.
 */
class DownloadsViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    private val _events = Channel<DownloadsEvent>(Channel.BUFFERED)

    /** One-shot outcomes the Fragment turns into toasts, as the legacy screen did. */
    val events: Flow<DownloadsEvent> = _events.receiveAsFlow()

    /** Where domain -> row mapping runs (cover-art keys hash a path, so off the main thread).
     *  Test seam. */
    internal var mappingDispatcher: CoroutineDispatcher = Dispatchers.IO

    private var loadJob: Job? = null
    private val removing = mutableSetOf<String>()

    /** `getDownloadedAlbums`, unchanged: local albums with real local song counts, sorted by
     *  lower-cased title. Test seam. */
    internal var albumsLoader: suspend () -> List<Album> = {
        withContext(Dispatchers.IO) {
            val db = activeServerProvider.offlineMetaDatabase
            val counts = db.trackDao().get().groupingBy { it.albumId }.eachCount()
            db.albumDao().get()
                .onEach { it.songCount = (counts[it.id] ?: 0).toLong() }
                .sortedBy { it.title.orEmpty().lowercase(Locale.ROOT) }
        }
    }

    /** `getDownloadedTracksForAlbum`, unchanged. Test seam. */
    internal var albumTracksLoader: suspend (albumId: String) -> List<Track> = { albumId ->
        withContext(Dispatchers.IO) {
            activeServerProvider.offlineMetaDatabase.trackDao().byAlbum(albumId)
        }
    }

    /** `DownloadService.deleteAsync`, unchanged. Test seam. */
    internal var trackDeleter: suspend (List<Track>) -> Unit = { DownloadService.deleteAsync(it) }

    /** Query the downloaded albums (again). */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                publish(albumsLoader())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                _uiState.update { it.copy(isLoading = false) }
                _events.trySend(DownloadsEvent.Error(error))
            }
        }
    }

    /**
     * Pull-to-refresh. The legacy swipe handler ran the base `GenericListModel.load`, which does
     * nothing for this screen, so a pull only flashed the spinner; here it re-queries the local
     * database, which is what a refresh gesture is expected to do.
     */
    fun refresh() = load()

    /** The trash button - immediate, no confirmation, exactly as before. */
    fun removeAlbum(albumId: String) {
        if (!removing.add(albumId)) return
        viewModelScope.launch {
            try {
                val tracks = albumTracksLoader(albumId)
                trackDeleter(tracks)
                publish(albumsLoader())
                _events.trySend(DownloadsEvent.Removed(tracks.size))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                _events.trySend(DownloadsEvent.Error(error))
            } finally {
                removing.remove(albumId)
            }
        }
    }

    private suspend fun publish(albums: List<Album>) {
        val rows = withContext(mappingDispatcher) { albums.map { it.toRow() } }
        _uiState.update { it.copy(isLoading = false, rows = rows.toImmutableList()) }
    }

    private fun Album.toRow(): DownloadedAlbumRow = DownloadedAlbumRow(
        id = id,
        title = title.orEmpty(),
        artist = artist,
        songCount = (songCount ?: 0).toInt(),
        artworkModel = coverArtRequestOrNull(large = false),
    )
}

/** One-shot results of a [DownloadsViewModel] operation, surfaced as toasts by the Fragment. */
sealed interface DownloadsEvent {
    data class Removed(val songCount: Int) : DownloadsEvent
    data class Error(val cause: Throwable) : DownloadsEvent
}
