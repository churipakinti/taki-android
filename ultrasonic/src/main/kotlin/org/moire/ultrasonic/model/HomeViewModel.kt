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
import java.util.Calendar
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.service.DailyMixQueueBuilder
import org.moire.ultrasonic.service.MusicServiceFactory
import org.moire.ultrasonic.ui.home.FeaturedMixUi
import org.moire.ultrasonic.ui.home.HomeAlbumUi
import org.moire.ultrasonic.ui.home.HomeShelfKind
import org.moire.ultrasonic.ui.home.HomeShelfUi
import org.moire.ultrasonic.ui.home.HomeUiState
import org.moire.ultrasonic.ui.home.greetingForHour
import org.moire.ultrasonic.ui.home.mixToFeaturedUi
import org.moire.ultrasonic.ui.home.toHomeAlbumUi
import org.moire.ultrasonic.util.ListLoadFeedback
import org.moire.ultrasonic.util.PerfMetrics
import org.moire.ultrasonic.util.Settings

/**
 * Provides [HomeUiState] for the Compose Home screen: the daily-mix featured card, the
 * "Recently played" shelf and the album shelves (Liked / Recently added / Discover /
 * Most played).
 *
 * Built entirely from data the server already exposes (recent, starred, newest, random,
 * most played, plus a stable daily mix from library signals) - there is no recommendation
 * engine behind this. The same per-server freshness window and per-shelf error swallowing
 * as the old View implementation are kept.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * The raw tracks of the current daily mix. Consumed by the host Fragment to issue the
     * playback command (`MediaPlayerManager.addToPlaylist`); never rendered by a composable.
     */
    @Volatile
    var mixTracks: List<Track> = emptyList()
        private set

    private val shelvesFreshness =
        HomeShelvesFreshness(Settings.DIRECTORY_CACHE_TIME * MILLIS_PER_SECOND)

    /**
     * Loads (or refreshes) every shelf. Failure semantics (issue #31): a shelf fetch that throws
     * is *not* an empty shelf - it keeps whatever that shelf showed before. When **every** album
     * fetch fails the load as a whole failed: with nothing on screen the state flips to
     * [HomeUiState.loadFailed] (error + Retry, never the empty message), with content on screen
     * the content stays and one refresh-failed message is raised. A single failing shelf (e.g. a
     * type an offline/folder server cannot answer) stays quiet, as before. A failed load is never
     * marked fresh, so the next visit retries it.
     */
    suspend fun loadHomeScreen(forceRefresh: Boolean = false) = coroutineScope {
        _uiState.update { it.copy(greeting = greetingForHour(currentHour())) }

        val currentServerId = ActiveServerProvider.getActiveServerId()
        val now = SystemClock.elapsedRealtime()

        if (!forceRefresh && shelvesFreshness.isFresh(now, currentServerId) && uiState.value.hasContent) {
            _uiState.update { it.copy(isLoading = false) }
            return@coroutineScope
        }

        _uiState.update {
            it.copy(isRefreshing = true, loadFailed = false, isLoading = it.isLoading || !it.hasContent)
        }
        val perfToken = PerfMetrics.start("home_load")
        try {
            val recent = async { fetchAlbums(AlbumListType.RECENT, SHORTCUTS_SIZE) }
            val liked = async { fetchAlbums(AlbumListType.STARRED) }
            val newest = async { fetchAlbums(AlbumListType.NEWEST) }
            val random = async { fetchAlbums(AlbumListType.RANDOM) }
            val frequent = async { fetchAlbums(AlbumListType.FREQUENT) }
            val mix = async { fetchMixUi(forceRefresh = false) }

            val results = listOf(liked.await(), newest.await(), random.await(), frequent.await())
            val recentResult = recent.await()
            val mixResult = mix.await()
            val allAlbumFetchesFailed = recentResult == null && results.all { it == null }

            var hadContent = false
            _uiState.update { current ->
                hadContent = current.hasContent
                mergeLoad(current, results, recentResult, mixResult, allAlbumFetchesFailed)
            }
            if (allAlbumFetchesFailed) {
                if (hadContent) ListLoadFeedback.refreshFailed(getApplication())
            } else {
                shelvesFreshness.markLoaded(SystemClock.elapsedRealtime(), currentServerId)
            }
        } finally {
            _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
            PerfMetrics.end("home_load", perfToken)
        }
    }

    /**
     * Folds one load into [current]: a failed shelf keeps its previous albums, and an empty mix
     * during a total failure keeps the previous mix (DailyMixQueueBuilder swallows its own network
     * errors into an empty list, so that is a failure, not "no mix" - #31).
     */
    private fun mergeLoad(
        current: HomeUiState,
        results: List<ImmutableList<HomeAlbumUi>?>,
        recent: ImmutableList<HomeAlbumUi>?,
        mixResult: MixFetch,
        allFailed: Boolean,
    ): HomeUiState {
        val mixUsable = mixResult.ok && (mixResult.ui != null || !allFailed)
        if (mixUsable) mixTracks = mixResult.tracks
        val shelves = SHELF_KINDS.mapIndexed { i, kind ->
            results[i]?.let { HomeShelfUi(kind, it) }
                ?: current.shelves.firstOrNull { it.kind == kind }
                ?: HomeShelfUi(kind, persistentListOf())
        }.toImmutableList()
        val next = current.copy(
            featuredMix = if (mixUsable) mixResult.ui else current.featuredMix,
            recentlyPlayed = recent ?: current.recentlyPlayed,
            shelves = shelves,
        )
        return next.copy(isLoading = false, loadFailed = allFailed && !next.hasContent)
    }

    suspend fun regenerateDailyMix() {
        _uiState.update { it.copy(isRefreshing = true) }
        try {
            val mix = fetchMixUi(forceRefresh = true)
            if (mix.ok) {
                mixTracks = mix.tracks
                _uiState.update { it.copy(featuredMix = mix.ui) }
            }
        } finally {
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun fetchAlbums(
        type: AlbumListType,
        size: Int = SIZE,
    ): ImmutableList<HomeAlbumUi>? = withContext(Dispatchers.IO) {
        // A failure here (offline folder browsing, a transient network error) must not cancel
        // the sibling fetches in loadHomeScreen()'s coroutineScope. null = "failed", which is
        // different from a successful empty shelf (#31).
        try {
            val service = MusicServiceFactory.getMusicService()
            val albums = if (ActiveServerProvider.shouldUseId3Tags()) {
                service.getAlbumList2(type, size, 0, null, null)
            } else {
                service.getAlbumList(type, size, 0, null)
            }
            albums.map { it.toHomeAlbumUi() }.toImmutableList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            null
        }
    }

    private class MixFetch(val ui: FeaturedMixUi?, val ok: Boolean, val tracks: List<Track> = emptyList())

    /** The daily-mix source. Test seam. */
    internal var mixLoader: suspend (forceRefresh: Boolean) -> List<Track> = { forceRefresh ->
        DailyMixQueueBuilder(MusicServiceFactory.getMusicService()).build(forceRefresh)
    }

    private suspend fun fetchMixUi(forceRefresh: Boolean): MixFetch = withContext(Dispatchers.IO) {
        try {
            val tracks = mixLoader(forceRefresh)
            MixFetch(mixToFeaturedUi(tracks), ok = true, tracks = tracks)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (expected: Exception) {
            // Keep the previous mix (and its playable tracks) rather than blanking the card.
            MixFetch(null, ok = false)
        }
    }

    private fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    companion object {
        private val SHELF_KINDS = listOf(
            HomeShelfKind.LIKED,
            HomeShelfKind.NEWEST,
            HomeShelfKind.DISCOVER,
            HomeShelfKind.FREQUENT,
        )
        private const val SIZE = 12
        private const val SHORTCUTS_SIZE = 6
        private const val MILLIS_PER_SECOND = 1000L
    }
}
