/*
 * HomeViewModel.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.model

import android.app.Application
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
import org.moire.ultrasonic.service.MusicService
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
    val mixGenreName: MutableLiveData<String?> = MutableLiveData()
    val mixTracks: MutableLiveData<List<Track>> = MutableLiveData()

    suspend fun loadHomeScreen() = coroutineScope {
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
        mixGenreName.value = mixResult.genreName
        mixTracks.value = mixResult.tracks

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
     * The Mix is regenerated at most once a day: it stays the exact same 25 tracks across
     * pull-to-refresh and app restarts within the same day, like a real playlist would, instead
     * of reshuffling into something different every time Home reloads.
     */
    private suspend fun fetchOrRestoreMix(): GenreMix = withContext(Dispatchers.IO) {
        // Same reasoning as fetch(): a failure here must not cancel the sibling shelf
        // fetches in loadHomeScreen()'s coroutineScope.
        try {
            val service = MusicServiceFactory.getMusicService()
            val today = LocalDate.now().toString()

            if (Settings.homeMixDate == today && Settings.homeMixGenre.isNotEmpty()) {
                val restored =
                    restoreMix(service, Settings.homeMixGenre, Settings.homeMixTrackIds)
                if (restored.tracks.isNotEmpty()) return@withContext restored
            }

            val fresh = generateMix(service)
            Settings.homeMixDate = today
            Settings.homeMixGenre = fresh.genreName ?: ""
            Settings.homeMixTrackIds = fresh.tracks.joinToString(",") { it.id }
            fresh
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            GenreMix(null, emptyList())
        }
    }

    private fun generateMix(service: MusicService): GenreMix {
        val genres = service.getGenres(false)
        val genre = genres.randomOrNull() ?: return GenreMix(null, emptyList())

        val tracks = service.getSongsByGenre(genre.name, MIX_FETCH_SIZE, 0).getTracks()

        return GenreMix(genre.name, tracks.shuffled().take(MIX_SIZE))
    }

    /**
     * Re-fetches the genre's songs and picks out the previously chosen track ids, in their
     * original order. There is no "get tracks by id" endpoint, so a wide re-fetch is the only
     * way to turn stored ids back into playable Track objects.
     */
    private fun restoreMix(
        service: MusicService,
        genreName: String,
        trackIdsCsv: String
    ): GenreMix {
        val wantedIds = trackIdsCsv.split(",").filter { it.isNotBlank() }
        if (wantedIds.isEmpty()) return GenreMix(genreName, emptyList())

        val available = service.getSongsByGenre(genreName, MIX_RESTORE_FETCH_SIZE, 0)
            .getTracks().associateBy { it.id }

        return GenreMix(genreName, wantedIds.mapNotNull { available[it] })
    }

    private data class GenreMix(val genreName: String?, val tracks: List<Track>)

    companion object {
        private const val SIZE = 12
        private const val SHORTCUTS_SIZE = 6
        private const val MIX_FETCH_SIZE = 50
        private const val MIX_RESTORE_FETCH_SIZE = 200
        private const val MIX_SIZE = 25
    }
}
