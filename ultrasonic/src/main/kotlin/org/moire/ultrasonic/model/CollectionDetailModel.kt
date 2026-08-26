/*
 * CollectionDetailModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.MusicCollection
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.CollectionResolver
import timber.log.Timber

/**
 * A single Collection/Box Set's discs. Works with lightweight already-cached Album rows
 * only - opening a Collection with 222
 * discs never fetches or renders their ~5,500 tracks; each disc's own track list is only fetched
 * when the user actually opens or plays that specific disc (see CollectionDetailFragment).
 */
class CollectionDetailModel(application: Application) :
    AndroidViewModel(application),
    KoinComponent {
    private val activeServerProvider: ActiveServerProvider by inject()

    val collection: MutableLiveData<MusicCollection?> = MutableLiveData()
    val isDiscovering: MutableLiveData<Boolean> = MutableLiveData(false)

    /** Keeps scroll position across back navigation, same recipe as CollectionListModel. */
    fun load(grouping: String, refresh: Boolean = false) {
        if (!refresh && collection.value != null) return

        viewModelScope.launch {
            collection.value = resolveCollection(grouping)
        }
    }

    /**
     * Explicit, user-initiated "find missing discs" (loading every track automatically on
     * open is forbidden - this is deliberately never called on open, only from a button tap).
     * Never hardcoded to any particular collection:
     * looks at which artist(s) the discs already known to this Collection belong to, pulls the
     * rest of those artists' albums (cheap - AlbumID3 only, already cached per-artist), and only
     * fetches tracks (which is what actually reveals grouping - see CachedMusicService.
     * getAlbumAsDir) for the ones not already resolved. Bounded concurrency so a 222-disc box set
     * doesn't fire 222 simultaneous requests at the server.
     */
    fun discoverMore(grouping: String) {
        if (isDiscovering.value == true) return

        viewModelScope.launch {
            isDiscovering.value = true
            try {
                withContext(Dispatchers.IO) {
                    val known = resolveCollection(grouping)?.albums.orEmpty()
                    val artistIds = known.mapNotNull { it.artistId }.filter { it.isNotBlank() }
                        .toSet()
                    if (artistIds.isEmpty()) return@withContext

                    val knownIds = known.mapTo(mutableSetOf()) { it.id }
                    val musicService = MusicServiceFactory.getMusicService()

                    val candidates = artistIds
                        .flatMap { artistId ->
                            try {
                                musicService.getAlbumsOfArtist(artistId, null, false)
                            } catch (e: Exception) {
                                Timber.w(e, "CollectionResolver: discovery failed for artist %s", artistId)
                                emptyList()
                            }
                        }
                        .distinctBy { it.id }
                        .filter { it.id !in knownIds && it.grouping.isNullOrBlank() }

                    Timber.i(
                        "CollectionResolver: discovering %d candidate albums for \"%s\"",
                        candidates.size,
                        grouping
                    )

                    val semaphore = Semaphore(DISCOVERY_CONCURRENCY)
                    coroutineScope {
                        candidates.map { candidate ->
                            async {
                                semaphore.withPermit {
                                    try {
                                        musicService.getAlbumAsDir(candidate.id, candidate.title, false)
                                    } catch (e: Exception) {
                                        Timber.w(
                                            e,
                                            "CollectionResolver: discovery failed for album %s",
                                            candidate.id
                                        )
                                    }
                                }
                            }
                        }.forEach { it.await() }
                    }
                }
                collection.value = resolveCollection(grouping)
            } finally {
                isDiscovering.value = false
            }
        }
    }

    private suspend fun resolveCollection(grouping: String): MusicCollection? = withContext(
        Dispatchers.IO
    ) {
        val albums = activeServerProvider.getActiveMetaDatabase().albumDao().withGrouping()
        CollectionResolver.resolve(albums).firstOrNull { it.title == grouping }
    }

    companion object {
        private const val DISCOVERY_CONCURRENCY = 4
    }
}
