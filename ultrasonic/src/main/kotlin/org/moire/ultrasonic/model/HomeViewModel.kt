/*
 * HomeViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DailyMixQueueBuilder
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.PerfMetrics
import org.moire.ultrasonic.util.Settings

/**
 * Provides the album shelves ("carousels") shown on the Home screen.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    val shortcutAlbums: MutableLiveData<List<Album>> = MutableLiveData()
    val favoriteAlbums: MutableLiveData<List<Album>> = MutableLiveData()
    val newestAlbums: MutableLiveData<List<Album>> = MutableLiveData()
    val randomAlbums: MutableLiveData<List<Album>> = MutableLiveData()
    val frequentAlbums: MutableLiveData<List<Album>> = MutableLiveData()
    val mixTracks: MutableLiveData<List<Track>> = MutableLiveData()

    private val shelvesFreshness =
        HomeShelvesFreshness(Settings.DIRECTORY_CACHE_TIME * MILLIS_PER_SECOND)

    suspend fun loadHomeScreen(forceRefresh: Boolean = false) = coroutineScope {
        val currentServerId = ActiveServerProvider.getActiveServerId()
        val now = SystemClock.elapsedRealtime()

        if (!forceRefresh && shelvesFreshness.isFresh(now, currentServerId)) {
            return@coroutineScope
        }

        val perfToken = PerfMetrics.start("home_load")
        val shortcuts = async { fetch(AlbumListType.RECENT, SHORTCUTS_SIZE) }
        val favorites = async { fetch(AlbumListType.STARRED) }
        val newest = async { fetch(AlbumListType.NEWEST) }
        val random = async { fetch(AlbumListType.RANDOM) }
        val frequent = async { fetch(AlbumListType.FREQUENT) }
        val mix = async { fetchOrRestoreMix() }

        // Using .value (not postValue) is safe and correct here: loadHomeScreen() is always
        // called from the main thread, and postValue's async post would otherwise race with
        // code that reads .value right after this suspend function returns (e.g. an empty-state
        // check), potentially reading stale values.
        shortcutAlbums.value = shortcuts.await()
        favoriteAlbums.value = favorites.await()
        newestAlbums.value = newest.await()
        randomAlbums.value = random.await()
        frequentAlbums.value = frequent.await()

        val mixResult = mix.await()
        mixTracks.value = mixResult.tracks

        shelvesFreshness.markLoaded(SystemClock.elapsedRealtime(), currentServerId)

        PerfMetrics.end("home_load", perfToken)
    }

    suspend fun regenerateDailyMix() {
        mixTracks.value = fetchMix(forceRefresh = true).tracks
    }

    private suspend fun fetch(type: AlbumListType, size: Int = SIZE): List<Album> =
        withContext(Dispatchers.IO) {
            // A failure here (e.g. offline folder-based browsing, a transient network error)
            // must not cancel the sibling async fetches in loadHomeScreen()'s coroutineScope.
            try {
                val service = MusicServiceFactory.getMusicService()

                if (ActiveServerProvider.shouldUseId3Tags()) {
                    service.getAlbumList2(type, size, 0, null, null)
                } else {
                    service.getAlbumList(type, size, 0, null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                emptyList()
            }
        }

    /**
     * Mix diario is regenerated at most once per day and server. It stores the selected IDs, then
     * tries to restore them from the same stable sources before building a fresh selection.
     */
    private suspend fun fetchOrRestoreMix(): DailyMix = fetchMix(forceRefresh = false)

    private suspend fun fetchMix(forceRefresh: Boolean): DailyMix = withContext(Dispatchers.IO) {
        // Same reasoning as fetch(): a failure here must not cancel the sibling shelf
        // fetches in loadHomeScreen()'s coroutineScope.
        try {
            DailyMix(DailyMixQueueBuilder(MusicServiceFactory.getMusicService()).build(forceRefresh))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DailyMix(emptyList())
        }
    }

    private data class DailyMix(val tracks: List<Track>)

    companion object {
        private const val SIZE = 12
        private const val SHORTCUTS_SIZE = 6
        private const val MILLIS_PER_SECOND = 1000L
    }
}
