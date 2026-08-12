/*
 * DailyMixQueueBuilder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import org.moire.ultrasonic.api.subsonic.models.AlbumListType
import org.moire.ultrasonic.data.ActiveServerProvider
import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.PerfMetrics
import org.moire.ultrasonic.util.Settings
import timber.log.Timber

private const val DAILY_MIX_SIZE = 30
private const val DAILY_MIX_MIN_SIZE = 15
private const val DAILY_MIX_ALBUM_COUNT = 8
private const val DAILY_MIX_RANDOM_FETCH_SIZE = 80

class DailyMixQueueBuilder(
    private val musicService: MusicService
) {
    suspend fun build(forceRefresh: Boolean = false): List<Track> {
        val today = LocalDate.now().toString()
        val serverId = ActiveServerProvider.getActiveServerId()

        if (!forceRefresh && Settings.homeMixDate == today && Settings.homeMixServerId == serverId) {
            val restored = restore(Settings.homeMixTrackIds)
            if (restored.size >= DAILY_MIX_MIN_SIZE) return restored
        }

        val fresh = generate(seed = if (forceRefresh) System.nanoTime() else stableSeed(today, serverId))
        Settings.homeMixDate = today
        Settings.homeMixServerId = serverId
        Settings.homeMixGenre = ""
        Settings.homeMixTrackIds = fresh.joinToString(",") { it.id }
        return fresh
    }

    private suspend fun restore(trackIdsCsv: String): List<Track> {
        val wantedIds = trackIdsCsv.split(",").filter { it.isNotBlank() }
        if (wantedIds.isEmpty()) return emptyList()

        val candidates = fetchCandidates()
        val available = candidates.all.associateBy { it.id }
        return wantedIds.mapNotNull { available[it] }
    }

    private suspend fun generate(seed: Long): List<Track> {
        val perfToken = PerfMetrics.start("daily_mix_generate")
        val candidates = fetchCandidates()
        val previousIds = Settings.homeMixTrackIds
            .split(",")
            .filter { it.isNotBlank() }
        var attemptSeed = seed
        var tracks = emptyList<Track>()

        for (attempt in 0 until DAILY_MIX_REGENERATION_ATTEMPTS) {
            tracks = DailyMixSelector.select(
                familiarTracks = candidates.familiar,
                rediscoveryTracks = candidates.rediscovery,
                explorationTracks = candidates.exploration,
                fallbackTracks = candidates.fallback,
                targetSize = DAILY_MIX_SIZE,
                minimumSize = DAILY_MIX_MIN_SIZE,
                seed = attemptSeed
            )
            if (tracks.map { it.id } != previousIds || attempt == DAILY_MIX_REGENERATION_ATTEMPTS - 1) {
                break
            }
            attemptSeed += DAILY_MIX_SEED_STEP
        }
        Timber.i(
            "Daily mix generated: familiarCandidates=%d rediscoveryCandidates=%d " +
                "explorationCandidates=%d fallbackCandidates=%d finalSize=%d seed=%d",
            candidates.familiar.distinctBy { it.id }.size,
            candidates.rediscovery.distinctBy { it.id }.size,
            candidates.exploration.distinctBy { it.id }.size,
            candidates.fallback.distinctBy { it.id }.size,
            tracks.size,
            attemptSeed
        )
        PerfMetrics.end("daily_mix_generate", perfToken)
        return tracks
    }

    private suspend fun fetchCandidates(): DailyMixCandidates {
        val starred = fetchTracks("daily_mix_starred") {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                musicService.getStarred2().songs
            } else {
                musicService.getStarred().songs
            }
        }
        val frequent = fetchTracksFromAlbums(AlbumListType.FREQUENT, DAILY_MIX_ALBUM_COUNT)
        val newest = fetchTracksFromAlbums(AlbumListType.NEWEST, DAILY_MIX_ALBUM_COUNT)
        val random = fetchTracks("daily_mix_random") {
            musicService.getRandomSongs(DAILY_MIX_RANDOM_FETCH_SIZE).getTracks()
        }

        return DailyMixCandidates(
            familiar = starred + frequent,
            rediscovery = random.filterNot { it.starred },
            exploration = newest + random.filter { it.created != null && !it.starred },
            fallback = random + starred + frequent + newest
        )
    }

    private suspend fun fetchTracksFromAlbums(type: AlbumListType, count: Int): List<Track> {
        val albums = fetchList("daily_mix_$type") {
            if (ActiveServerProvider.shouldUseId3Tags()) {
                musicService.getAlbumList2(type, count, 0, null, null)
            } else {
                musicService.getAlbumList(type, count, 0, null)
            }
        }

        return albums.flatMap { album ->
            fetchTracks("daily_mix_album_tracks") {
                musicService.getAlbumAsDir(album.id, album.title, false).getTracks()
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

    private data class DailyMixCandidates(
        val familiar: List<Track>,
        val rediscovery: List<Track>,
        val exploration: List<Track>,
        val fallback: List<Track>
    ) {
        val all: List<Track>
            get() = familiar + rediscovery + exploration + fallback
    }

    private fun stableSeed(today: String, serverId: Int): Long =
        "$today:$serverId".hashCode().toLong()
}

private const val DAILY_MIX_REGENERATION_ATTEMPTS = 3
private const val DAILY_MIX_SEED_STEP = -7046029254386353131L
