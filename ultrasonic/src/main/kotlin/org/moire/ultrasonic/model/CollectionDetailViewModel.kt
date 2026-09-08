/*
 * CollectionDetailViewModel.kt
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.imageloader.coverArtRequestOrNull
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.collection.CollectionDetailUiState
import org.moire.ultrasonic.ui.collection.CollectionMember
import org.moire.ultrasonic.util.CollectionResolver
import timber.log.Timber

/**
 * Owns the Compose Collection Detail state (issue #10 phase 4B) as one
 * [StateFlow]<[CollectionDetailUiState]>. A 1:1 port of the legacy `CollectionDetailModel`:
 *  - [load] resolves the box set client-side from already-cached album rows
 *    (`AlbumDao.withGrouping()` -> [CollectionResolver.resolve] -> exact `title` match) and is
 *    load-once, keeping scroll/data across back navigation, exactly as before;
 *  - [discoverMore] runs the same user-initiated, bounded-concurrency artist crawl that finds
 *    ungrouped member releases - never called on open.
 *
 * Navigation and the "N new discs" toast stay in the Fragment; this class only reads and
 * resolves. No track list is ever fetched here.
 */
class CollectionDetailViewModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {

    private val activeServerProvider: ActiveServerProvider by inject()

    private val _uiState = MutableStateFlow(CollectionDetailUiState())
    val uiState: StateFlow<CollectionDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var loadedGrouping: String? = null

    /**
     * Resolve the collection for an exact `grouping` string. Default: the same
     * `AlbumDao.withGrouping()` + [CollectionResolver] + exact-`title` match the legacy model
     * used. Test seam.
     */
    internal var collectionLoader: suspend (grouping: String) -> MusicCollection? = { grouping ->
        withContext(Dispatchers.IO) {
            val albums = activeServerProvider.getActiveMetaDatabase().albumDao().withGrouping()
            CollectionResolver.resolve(albums).firstOrNull { it.title == grouping }
        }
    }

    /**
     * The "find missing discs" crawl: for the artist(s) the known members belong to, pull the
     * rest of their albums and fetch tracks (which is what reveals `grouping` -
     * `CachedMusicService.getAlbumAsDir`) for the not-yet-resolved ones. Bounded concurrency so
     * a 222-disc box set does not fire 222 requests. Verbatim from the legacy model. Test seam.
     */
    internal var discoverer: suspend (grouping: String, known: List<Album>) -> Unit =
        { grouping, known ->
            withContext(Dispatchers.IO) {
                val artistIds = known.mapNotNull { it.artistId }.filter { it.isNotBlank() }.toSet()
                if (artistIds.isNotEmpty()) {
                    val knownIds = known.mapTo(mutableSetOf()) { it.id }
                    val musicService = MusicServiceFactory.getMusicService()

                    val candidates = artistIds
                        .flatMap { artistId ->
                            try {
                                musicService.getAlbumsOfArtist(artistId, null, false)
                            } catch (
                                @Suppress("TooGenericExceptionCaught") e: Exception,
                            ) {
                                Timber.w(e, "CollectionResolver: discovery failed for artist %s", artistId)
                                emptyList()
                            }
                        }
                        .distinctBy { it.id }
                        .filter { it.id !in knownIds && it.grouping.isNullOrBlank() }

                    Timber.i(
                        "CollectionResolver: discovering %d candidate albums for \"%s\"",
                        candidates.size,
                        grouping,
                    )

                    val semaphore = Semaphore(DISCOVERY_CONCURRENCY)
                    coroutineScope {
                        candidates.map { candidate ->
                            async {
                                semaphore.withPermit {
                                    try {
                                        musicService.getAlbumAsDir(candidate.id, candidate.title, false)
                                    } catch (
                                        @Suppress("TooGenericExceptionCaught") e: Exception,
                                    ) {
                                        Timber.w(
                                            e,
                                            "CollectionResolver: discovery failed for album %s",
                                            candidate.id,
                                        )
                                    }
                                }
                            }
                        }.forEach { it.await() }
                    }
                }
            }
        }

    /**
     * Resolve the collection once. Load-once across Fragment view recreation - the same
     * grouping with content already on screen returns without a new resolve, exactly like the
     * legacy `if (!refresh && collection.value != null) return`.
     */
    fun load(grouping: String, refresh: Boolean = false) {
        if (!refresh && loadedGrouping == grouping && _uiState.value.hasContent) return
        loadedGrouping = grouping

        _uiState.update {
            it.copy(
                isLoading = true,
                loadFailed = false,
                grouping = grouping,
                title = it.title.ifEmpty { grouping },
            )
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val collection = try {
                collectionLoader(grouping)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception) {
                null
            }

            _uiState.update { current ->
                if (collection == null) {
                    // A failed refresh keeps the members already on screen; only the first
                    // resolve surfaces the empty state.
                    current.copy(isLoading = false, loadFailed = current.members.isEmpty())
                } else {
                    project(current, grouping, collection)
                }
            }
        }
    }

    /** Pull-to-refresh: re-resolve through the same [load] path, keeping members visible.
     *  Ignored while a load or a discovery crawl is already running. */
    fun refresh() {
        if (_uiState.value.isLoading || _uiState.value.isDiscovering) return
        loadedGrouping?.let { load(it, refresh = true) }
    }

    /**
     * User-initiated "find missing discs". Never called on open. Ignored if already running.
     * Re-resolves the collection when the crawl finishes so newly grouped members appear.
     */
    fun discoverMore() {
        val grouping = loadedGrouping ?: return
        if (_uiState.value.isDiscovering) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDiscovering = true) }
            try {
                val known = try {
                    collectionLoader(grouping)?.albums.orEmpty()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception,
                ) {
                    emptyList()
                }
                discoverer(grouping, known)
                val resolved = try {
                    collectionLoader(grouping)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught", "SwallowedException") expected: Exception,
                ) {
                    null
                }
                if (resolved != null) {
                    _uiState.update { project(it, grouping, resolved) }
                }
            } finally {
                _uiState.update { it.copy(isDiscovering = false) }
            }
        }
    }

    private fun project(
        current: CollectionDetailUiState,
        grouping: String,
        collection: MusicCollection,
    ): CollectionDetailUiState = current.copy(
        isLoading = false,
        loadFailed = false,
        grouping = grouping,
        title = collection.title,
        members = collection.albums.map { it.toMember() }.toImmutableList(),
    )

    private fun Album.toMember(): CollectionMember = CollectionMember(
        id = id,
        parent = parent,
        discNumber = discNumber?.takeIf { it > 0 },
        title = (title ?: album).orEmpty(),
        trackCount = songCount?.takeIf { it > 0 },
        artworkModel = coverArtRequestOrNull(large = false),
    )

    private companion object {
        private const val DISCOVERY_CONCURRENCY = 4
    }
}
