/*
 * DailyMixQueueBuilder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

class DailyMixQueueBuilder(private val musicService: MusicService) {
    companion object {
        // Exposed so callers can tell the user when the mix came back shorter than usual.
        const val TARGET_SIZE = DAILY_MIX_SIZE
    }

    suspend fun build(forceRefresh: Boolean = false): List<Track> {
        val today = LocalDate.now().toString()
        val serverId = ActiveServerProvider.getActiveServerId()

        if (
            shouldAttemptDailyMixRestore(
                forceRefresh = forceRefresh,
                storedDate = Settings.homeMixDate,
                today = today,
                storedServerId = Settings.homeMixServerId,
                serverId = serverId
            )
        ) {
            val storedTrackIds = Settings.homeMixTrackIds
                .split(",")
                .filter { it.isNotBlank() }
            val restored = restore(Settings.homeMixTrackIds)
            if (shouldUseRestoredDailyMix(storedTrackIds, restored)) return restored
            Timber.i(
                "Daily mix restore incomplete: expectedSize=%d restoredSize=%d; regenerating",
                storedTrackIds.size,
                restored.size
            )
        }

        val fresh =
            generate(seed = if (forceRefresh) System.nanoTime() else stableSeed(today, serverId))
        Settings.homeMixDate = today
        Settings.homeMixServerId = serverId
        Settings.homeMixGenre = ""
        Settings.homeMixTrackIds = fresh.joinToString(",") { it.id }
        return fresh
    }

    /*
     * Restores the saved Mix diario by looking up each stored id directly (getSong.view),
     * rather than matching stored ids against a freshly-fetched candidate pool (starred,
     * frequent albums, newest albums, random). The old approach failed after an app restart or
     * whenever the random pool happened to return a different sample: a saved song that was
     * still perfectly valid could simply not appear in that call's candidates.
     */
    private suspend fun restore(trackIdsCsv: String): List<Track> =
        restoreTracksByExactId(trackIdsCsv, ::fetchSong)

    private suspend fun fetchSong(id: String): Track? = try {
        musicService.getSong(id)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Timber.i(error, "Song %s unavailable while restoring Mix diario", id)
        null
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
            if (tracks.map { it.id } != previousIds ||
                attempt == DAILY_MIX_REGENERATION_ATTEMPTS - 1
            ) {
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
            // "nunca escuchada": a track with a confirmed playCount of 0 counts as
            // never-listened even without a created date. playCount
            // null (server didn't expose it) is not treated as a signal either way.
            exploration = newest + random.filter {
                !it.starred && (it.created != null || it.playCount == 0L)
            },
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

    private suspend fun <T> fetchList(label: String, block: suspend () -> List<T>): List<T> = try {
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
    )

    private fun stableSeed(today: String, serverId: Int): Long =
        "$today:$serverId".hashCode().toLong()
}

private const val DAILY_MIX_REGENERATION_ATTEMPTS = 3
private const val DAILY_MIX_SEED_STEP = -7046029254386353131L

internal fun shouldUseRestoredDailyMix(
    storedTrackIds: List<String>,
    restoredTracks: List<Track>
): Boolean = storedTrackIds.isNotEmpty() && restoredTracks.size == storedTrackIds.size

/*
 * Gate for build()'s restore branch: only attempt to restore a stored Mix diario when it was
 * generated today, for this same server, and the caller isn't forcing a fresh one. Extracted as
 * a free function so the per-serverId stability contract ("cambiar de servidor no debe reutilizar
 * el mix de la biblioteca anterior") is directly testable without a Settings/Android dependency.
 */
internal fun shouldAttemptDailyMixRestore(
    forceRefresh: Boolean,
    storedDate: String,
    today: String,
    storedServerId: Int,
    serverId: Int
): Boolean = !forceRefresh && storedDate == today && storedServerId == serverId

/*
 * Looks up each id in [trackIdsCsv] concurrently via [fetchTrack], dropping ids that could not
 * be resolved (song deleted/moved server-side) instead of failing the whole restore. Extracted
 * as a free function so the concurrency/filtering behavior is testable without a MusicService
 * or Settings/Android dependency.
 */
internal suspend fun restoreTracksByExactId(
    trackIdsCsv: String,
    fetchTrack: suspend (String) -> Track?
): List<Track> = coroutineScope {
    val wantedIds = trackIdsCsv.split(",").filter { it.isNotBlank() }
    if (wantedIds.isEmpty()) return@coroutineScope emptyList()

    wantedIds.map { id -> async { fetchTrack(id) } }.awaitAll().filterNotNull()
}
