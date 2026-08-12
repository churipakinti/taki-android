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
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Album
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DailyMixSelector
import org.moire.ultrasonic.service.MusicService
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.util.PerfMetrics
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

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
    private suspend fun fetchOrRestoreMix(): DailyMix = withContext(Dispatchers.IO) {
        // Same reasoning as fetch(): a failure here must not cancel the sibling shelf
        // fetches in loadHomeScreen()'s coroutineScope.
        try {
            val service = MusicServiceFactory.getMusicService()
            val today = LocalDate.now().toString()
            val serverId = ActiveServerProvider.getActiveServerId()

            if (Settings.homeMixDate == today && Settings.homeMixServerId == serverId) {
                val restored = restoreMix(service, Settings.homeMixTrackIds)
                if (restored.tracks.size >= DAILY_MIX_MIN_SIZE) return@withContext restored
            }

            val fresh = generateMix(service)
            Settings.homeMixDate = today
            Settings.homeMixServerId = serverId
            Settings.homeMixGenre = ""
            Settings.homeMixTrackIds = fresh.tracks.joinToString(",") { it.id }
            fresh
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            DailyMix(emptyList())
        }
    }

    private suspend fun generateMix(service: MusicService): DailyMix {
        val perfToken = PerfMetrics.start("daily_mix_generate")
        val candidates = fetchDailyMixCandidates(service)
        val tracks = DailyMixSelector.select(
            familiarTracks = candidates.familiar,
            rediscoveryTracks = candidates.rediscovery,
            explorationTracks = candidates.exploration,
            fallbackTracks = candidates.fallback,
            targetSize = DAILY_MIX_SIZE,
            minimumSize = DAILY_MIX_MIN_SIZE
        )
        Timber.i(
            "Daily mix generated: familiarCandidates=%d rediscoveryCandidates=%d " +
                "explorationCandidates=%d fallbackCandidates=%d finalSize=%d",
            candidates.familiar.distinctBy { it.id }.size,
            candidates.rediscovery.distinctBy { it.id }.size,
            candidates.exploration.distinctBy { it.id }.size,
            candidates.fallback.distinctBy { it.id }.size,
            tracks.size
        )
        PerfMetrics.end("daily_mix_generate", perfToken)
        return DailyMix(tracks)
    }

    private suspend fun restoreMix(service: MusicService, trackIdsCsv: String): DailyMix {
        val wantedIds = trackIdsCsv.split(",").filter { it.isNotBlank() }
        if (wantedIds.isEmpty()) return DailyMix(emptyList())

        val candidates = fetchDailyMixCandidates(service)
        val available = candidates.all.associateBy { it.id }

        return DailyMix(wantedIds.mapNotNull { available[it] })
    }

    private suspend fun fetchDailyMixCandidates(service: MusicService): DailyMixCandidates {
        val starred = fetchTracks("daily_mix_starred") {
            service.getStarred2().songs
        }
        val frequent = fetchTracksFromAlbums(service, AlbumListType.FREQUENT, DAILY_MIX_ALBUM_COUNT)
        val newest = fetchTracksFromAlbums(service, AlbumListType.NEWEST, DAILY_MIX_ALBUM_COUNT)
        val random = fetchTracks("daily_mix_random") {
            service.getRandomSongs(DAILY_MIX_RANDOM_FETCH_SIZE).getTracks()
        }

        return DailyMixCandidates(
            familiar = starred + frequent,
            rediscovery = random.filterNot { it.starred },
            exploration = newest + random.filter { it.created != null && !it.starred },
            fallback = random + starred + frequent + newest
        )
    }

    private suspend fun fetchTracksFromAlbums(
        service: MusicService,
        type: AlbumListType,
        count: Int
    ): List<Track> {
        val albums = fetchList("daily_mix_$type") {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                service.getAlbumList2(type, count, 0, null, null)
            } else {
                service.getAlbumList(type, count, 0, null)
            }
        }

        return albums.flatMap { album ->
            fetchTracks("daily_mix_album_tracks") {
                service.getAlbumAsDir(album.id, album.title, false).getTracks()
            }
        }
    }

    private suspend fun fetchTracks(label: String, block: suspend () -> List<Track>): List<Track> =
        fetchList(label, block)

    private suspend fun <T> fetchList(label: String, block: suspend () -> List<T>): List<T> =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Timber.i(error, "%s unavailable while building Mix diario", label)
            emptyList()
        }

    private data class DailyMix(val tracks: List<Track>)

    private data class DailyMixCandidates(
        val familiar: List<Track>,
        val rediscovery: List<Track>,
        val exploration: List<Track>,
        val fallback: List<Track>
    ) {
        val all: List<Track>
            get() = familiar + rediscovery + exploration + fallback
    }

    companion object {
        private const val SIZE = 12
        private const val SHORTCUTS_SIZE = 6
        private const val DAILY_MIX_SIZE = 30
        private const val DAILY_MIX_MIN_SIZE = 15
        private const val DAILY_MIX_ALBUM_COUNT = 8
        private const val DAILY_MIX_RANDOM_FETCH_SIZE = 80
        private const val MILLIS_PER_SECOND = 1000L
    }
}
