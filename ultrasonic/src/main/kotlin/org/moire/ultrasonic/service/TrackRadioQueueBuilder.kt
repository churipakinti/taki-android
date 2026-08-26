/*
 * TrackRadioQueueBuilder.kt
 * Copyright (C) 2009-2026 Ultrasonic developers
 *
 * Distributed under terms of the GNU GPLv3 license.
 */

package org.moire.ultrasonic.service

import org.moire.ultrasonic.domain.Track
import org.moire.ultrasonic.util.PerfMetrics
import timber.log.Timber

private const val RADIO_TARGET_SIZE = 30
private const val RADIO_MIN_SIZE = 15
private const val RADIO_RANDOM_FILL_SIZE = 50
private const val RADIO_TOP_SONGS_PER_ARTIST = 12
private const val RADIO_RELATED_ARTIST_LIMIT = 4

class TrackRadioQueueBuilder(private val musicService: MusicService) {
    companion object {
        // Exposed so callers can tell the user when the generated radio came back shorter than
        // usual.
        const val TARGET_SIZE = RADIO_TARGET_SIZE
    }

    suspend fun build(seed: Track): List<Track> {
        val perfToken = PerfMetrics.start("track_radio_generate")
        val candidates = mutableListOf<List<Track>>()

        val relatedArtists = seed.artistId
            ?.let { artistId ->
                fetch("track_radio_artist_info") { musicService.getArtistInfo(artistId) }
            }
            ?.similarArtists
            .orEmpty()
            .take(RADIO_RELATED_ARTIST_LIMIT)

        if (!seed.artist.isNullOrBlank()) {
            candidates += fetch("track_radio_seed_artist_top") {
                musicService.getTopSongs(seed.artist!!, RADIO_TOP_SONGS_PER_ARTIST)
            }.orEmpty()
        }

        relatedArtists.forEach { artist ->
            val artistName = artist.name?.takeIf { it.isNotBlank() }
            if (artistName != null) {
                candidates += fetch("track_radio_related_artist_top") {
                    musicService.getTopSongs(artistName, RADIO_TOP_SONGS_PER_ARTIST)
                }.orEmpty()
            }
        }

        if (!seed.genre.isNullOrBlank()) {
            candidates += fetch("track_radio_genre") {
                musicService.getSongsByGenre(seed.genre!!, RADIO_RANDOM_FILL_SIZE, 0).getTracks()
            }.orEmpty()
        }

        candidates += fetch("track_radio_random") {
            musicService.getRandomSongs(RADIO_RANDOM_FILL_SIZE).getTracks()
        }.orEmpty()

        val queue = TrackRadioSelector.select(
            seed = seed,
            candidateGroups = candidates,
            targetSize = RADIO_TARGET_SIZE,
            minimumSize = RADIO_MIN_SIZE
        )

        Timber.i(
            "Track radio generated: seed=%s relatedArtists=%d groups=%d finalSize=%d",
            seed.id,
            relatedArtists.size,
            candidates.size,
            queue.size
        )
        PerfMetrics.end("track_radio_generate", perfToken)

        return queue
    }

    private suspend fun <T> fetch(label: String, block: suspend () -> T): T? = try {
        block()
    } catch (error: Exception) {
        Timber.i(error, "%s unavailable while building track radio", label)
        null
    }
}
